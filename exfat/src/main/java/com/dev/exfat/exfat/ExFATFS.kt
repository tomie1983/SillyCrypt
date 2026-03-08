package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.exfat.bootregion.BootRegionOperator
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.bootregion.ExFatFileSystemChangingMetadata
import com.dev.exfat.exfat.bootregion.ExFatFileSystemMetadata
import com.dev.exfat.exfat.cache.LruCache
import com.dev.exfat.exfat.fat.FATOperator
import com.dev.exfat.file.ExFATFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class ExFATFS(private val dataFactory: RandomAccessDataFactory) {
    private val serviceData = dataFactory.create()

    private val bootRegionOperator = BootRegionOperator(serviceData)
    private val fatOperator = FATOperator(serviceData)

    /**
     * serviceData is a shared RandomAccessData with mutable cursor,
     * so all access to it must be serialized.
     */
    private val serviceDataMutex = Mutex()

    /**
     * Separate cache/init locks to avoid re-entrant deadlocks.
     */
    private val constantMetadataMutex = Mutex()
    private val changingMetadataMutex = Mutex()
    private val rootStateMutex = Mutex()
    private val fatChainCacheMutex = Mutex()

    /**
     * Future write path:
     * - FAT
     * - Allocation Bitmap
     * - BootRegion changing metadata
     * - allocator state
     */
    private val allocationMutex = Mutex()

    @Volatile
    private var constantMetadataCache: ExFatFIleSystemConstantMetadata? = null

    @Volatile
    private var changingMetadataCache: ExFatFileSystemChangingMetadata? = null

    @Volatile
    private var rootStateCache: NodeState? = null

    /** firstCluster -> full FAT chain (includes first cluster) */
    private val fatChainCache = LruCache<Int, IntArray>(512)

    /**
     * Registry no longer stores paths and no longer needs a global mutex.
     */
    private val nodeStateRegistry = ConcurrentHashMap<NodeId, NodeState>()

    // -------------------- metadata API --------------------

    suspend fun isExFat(): Boolean {
        return serviceDataMutex.withLock {
            bootRegionOperator.isExFat(serviceData)
        }
    }

    suspend fun readConstantFileSystemMetadata(): ExFatFIleSystemConstantMetadata {
        constantMetadataCache?.let { return it }

        return constantMetadataMutex.withLock {
            constantMetadataCache?.let { return@withLock it }

            val loaded = serviceDataMutex.withLock {
                bootRegionOperator.readConstantFileSystemMetadata(serviceData)
            }

            constantMetadataCache = loaded
            loaded
        }
    }

    suspend fun readChangingFileSystemMetadata(): ExFatFileSystemChangingMetadata {
        changingMetadataCache?.let { return it }

        return changingMetadataMutex.withLock {
            changingMetadataCache?.let { return@withLock it }

            val loaded = serviceDataMutex.withLock {
                bootRegionOperator.readChangingFileSystemMetadata(serviceData)
            }

            changingMetadataCache = loaded
            loaded
        }
    }

    suspend fun readFullFileSystemMetadata(): ExFatFileSystemMetadata {
        return serviceDataMutex.withLock {
            bootRegionOperator.readFullFileSystemMetadata(serviceData)
        }
    }

    // -------------------- public file API --------------------

    suspend fun root(): ExFATFile {
        val rootState = getRootState()
        return ExFATFile(
            fileSystem = this,
            state = rootState,
            displayName = "/",
            displayPath = "/"
        )
    }

    suspend fun listRoot(): List<ExFATFile> = root().listFiles()

    suspend fun getFileFromPath(path: String): ExFATFile? {
        val normalized = normalizeAbsolutePath(path)
        if (normalized == "/") return root()

        val parts = splitAbsolutePath(normalized)
        val lookupData = createDataHandle()

        try {
            var currentState = getRootState()
            var currentPath = "/"

            for (part in parts) {
                val currentCore = currentState.snapshotCore()
                if (!currentCore.isDirectory) return null

                val children = listDirectoryEntries(currentState, lookupData)
                val next = children.firstOrNull { it.name == part } ?: return null

                currentState = internNodeState(
                    nodeId = next.nodeId,
                    core = next.core,
                    entrySetLocation = next.entrySetLocation
                )
                currentPath = joinPath(currentPath, next.name)
            }

            val displayName = if (currentPath == "/") "/" else parts.last()
            return ExFATFile(
                fileSystem = this,
                state = currentState,
                displayName = displayName,
                displayPath = currentPath
            )
        } finally {
            lookupData.close()
        }
    }

    suspend fun exists(path: String): Boolean = getFileFromPath(path) != null

    suspend fun close() {
        nodeStateRegistry.clear()

        fatChainCacheMutex.withLock {
            fatChainCache.clear()
        }

        rootStateCache = null
        constantMetadataCache = null
        changingMetadataCache = null
        serviceData.close()
        dataFactory.close()
    }

    // -------------------- internal live-state registry --------------------

    private fun internNodeState(
        nodeId: NodeId,
        core: NodeCoreMetadata,
        entrySetLocation: NodeEntrySetLocation?
    ): NodeState {
        val existing = nodeStateRegistry[nodeId]
        if (existing != null) {
            existing.update(core, entrySetLocation)
            return existing
        }

        val created = NodeState(
            nodeId = nodeId,
            initialCore = core,
            initialEntrySetLocation = entrySetLocation
        )

        val raced = nodeStateRegistry.putIfAbsent(nodeId, created)
        return if (raced != null) {
            raced.update(core, entrySetLocation)
            raced
        } else {
            created
        }
    }

    private suspend fun getRootState(): NodeState {
        rootStateCache?.let { return it }

        return rootStateMutex.withLock {
            rootStateCache?.let { return@withLock it }

            val c = readConstantFileSystemMetadata()
            val rootCluster = c.rootDirFirstCluster

            val tempData = createDataHandle()
            try {
                val rootChain = resolveFatChain(tempData, rootCluster.toInt())
                val estimatedBytes = rootChain.size.toLong() * c.bytesPerCluster
                val readable = min(estimatedBytes, MAX_ROOT_DIR_BYTES_SAFETY)

                val rootCore = NodeCoreMetadata(
                    isDirectory = true,
                    attributes = ATTR_DIRECTORY,
                    firstCluster = rootCluster,
                    dataLength = readable,
                    validDataLength = readable,
                    readableLength = readable,
                    streamFlags = 0,
                    noFatChain = false
                )

                val rootState = internNodeState(
                    nodeId = NodeId.Root,
                    core = rootCore,
                    entrySetLocation = null
                )

                rootStateCache = rootState
                rootState
            } finally {
                tempData.close()
            }
        }
    }

    // -------------------- internal API used by handlers --------------------

    internal fun createDataHandle(): RandomAccessData = dataFactory.create()

    internal suspend fun <T> withAllocationLock(action: suspend () -> T): T {
        return allocationMutex.withLock { action() }
    }

    internal suspend fun listDirectory(
        dirState: NodeState,
        parentDisplayPath: String,
        data: RandomAccessData
    ): List<ExFATFile> {
        val dirCore = dirState.snapshotCore()
        require(dirCore.isDirectory) { "Not a directory" }

        return listDirectoryEntries(dirState, data).map { parsed ->
            val childState = internNodeState(
                nodeId = parsed.nodeId,
                core = parsed.core,
                entrySetLocation = parsed.entrySetLocation
            )

            val childDisplayPath = joinPath(parentDisplayPath, parsed.name)

            ExFATFile(
                fileSystem = this,
                state = childState,
                displayName = parsed.name,
                displayPath = childDisplayPath
            )
        }
    }

    internal suspend fun readStreamRange(
        state: NodeState,
        position: Long,
        buf: ByteArray,
        data: RandomAccessData
    ): Int {
        val coreSnapshot = state.snapshotCore()
        return readStreamRange(coreSnapshot, position, buf, data)
    }

    internal suspend fun readStreamRange(
        core: NodeCoreMetadata,
        position: Long,
        buf: ByteArray,
        data: RandomAccessData
    ): Int {
        if (buf.isEmpty()) return 0
        if (position < 0L) throw IllegalArgumentException("Negative position: $position")

        val readableSize = core.readableLength
        if (position >= readableSize) return -1

        val maxToRead = min(buf.size.toLong(), readableSize - position).toInt()
        if (maxToRead <= 0) return -1

        if (core.firstCluster == 0L) {
            throw IllegalStateException("Stream is non-empty but firstCluster == 0")
        }

        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()

        if (core.noFatChain) {
            readContiguousRange(
                data = data,
                core = core,
                fsMeta = fsMeta,
                position = position,
                out = buf,
                outOffset = 0,
                len = maxToRead,
                bytesPerCluster = bytesPerCluster
            )
            return maxToRead
        }

        readFatChainedRange(
            data = data,
            core = core,
            fsMeta = fsMeta,
            position = position,
            out = buf,
            outOffset = 0,
            len = maxToRead,
            bytesPerCluster = bytesPerCluster
        )
        return maxToRead
    }

    // -------------------- directory parsing --------------------

    private suspend fun listDirectoryEntries(
        dirState: NodeState,
        data: RandomAccessData
    ): List<ParsedDirectoryNode> {
        val dirCore = dirState.snapshotCore()
        require(dirCore.isDirectory) { "Not a directory" }
        if (dirCore.readableLength == 0L) return emptyList()

        val dirBytes = readAllReadableBytes(dirCore, data)
        val parentCluster = dirCore.firstCluster
        return parseDirectory(dirBytes, parentCluster)
    }

    private fun parseDirectory(
        dirBytes: ByteArray,
        parentDirFirstCluster: Long
    ): List<ParsedDirectoryNode> {
        val out = mutableListOf<ParsedDirectoryNode>()

        var i = 0
        while (i + DIR_ENTRY_SIZE <= dirBytes.size) {
            val entryTypeRaw = u8(dirBytes[i])

            if (entryTypeRaw == 0x00) break

            val inUse = (entryTypeRaw and 0x80) != 0
            val entryType = entryTypeRaw and 0x7F

            if (!inUse) {
                i += DIR_ENTRY_SIZE
                continue
            }

            if (entryType == TYPE_FILE_DIR_ENTRY) {
                val primaryEntryOffset = i.toLong()
                val secondaryCount = u8(dirBytes[i + 1])
                val fileAttributes = u16le(dirBytes, i + 4)
                val isDir = (fileAttributes and ATTR_DIRECTORY) != 0

                val secondariesStart = i + DIR_ENTRY_SIZE
                val secondariesEnd = secondariesStart + secondaryCount * DIR_ENTRY_SIZE

                if (secondariesEnd <= dirBytes.size) {
                    val secondaryInfo = parseSecondariesForFile(
                        bytes = dirBytes,
                        start = secondariesStart,
                        secondaryCount = secondaryCount
                    )

                    val stream = secondaryInfo.stream
                    val name = secondaryInfo.name

                    if (!name.isNullOrEmpty() && stream != null) {
                        val dataLength = stream.dataLength
                        val validDataLength = stream.validDataLength
                        val readableLength = min(validDataLength, dataLength).coerceAtLeast(0L)

                        val nodeId = NodeId.DirectoryEntry(
                            parentDirFirstCluster = parentDirFirstCluster,
                            primaryEntryOffsetInParentBytes = primaryEntryOffset
                        )

                        val core = NodeCoreMetadata(
                            isDirectory = isDir,
                            attributes = fileAttributes,
                            firstCluster = stream.firstCluster,
                            dataLength = dataLength,
                            validDataLength = validDataLength,
                            readableLength = readableLength,
                            streamFlags = stream.flags,
                            noFatChain = (stream.flags and STREAM_FLAG_NO_FAT_CHAIN) != 0
                        )

                        val entrySetLocation = NodeEntrySetLocation(
                            primaryEntryOffsetInParentBytes = primaryEntryOffset,
                            streamEntryOffsetInParentBytes = stream.streamEntryOffsetInParentBytes,
                            fileNameEntryOffsetsInParentBytes = secondaryInfo.fileNameEntryOffsetsInParentBytes.toLongArray()
                        )

                        out += ParsedDirectoryNode(
                            nodeId = nodeId,
                            name = name,
                            core = core,
                            entrySetLocation = entrySetLocation
                        )
                    }
                }

                i += DIR_ENTRY_SIZE * (1 + secondaryCount)
                continue
            }

            i += DIR_ENTRY_SIZE
        }

        return out
    }

    private data class ParsedDirectoryNode(
        val nodeId: NodeId,
        val name: String,
        val core: NodeCoreMetadata,
        val entrySetLocation: NodeEntrySetLocation
    )

    private data class StreamExt(
        val nameLength: Int,
        val flags: Int,
        val validDataLength: Long,
        val firstCluster: Long,
        val dataLength: Long,
        val streamEntryOffsetInParentBytes: Long
    )

    private data class SecondaryParseResult(
        val stream: StreamExt?,
        val name: String?,
        val fileNameEntryOffsetsInParentBytes: MutableList<Long>
    )

    private fun parseSecondariesForFile(
        bytes: ByteArray,
        start: Int,
        secondaryCount: Int
    ): SecondaryParseResult {
        var stream: StreamExt? = null
        val nameChars = StringBuilder()
        var expectedNameLen = -1
        val fileNameOffsets = mutableListOf<Long>()

        var offset = start
        for (k in 0 until secondaryCount) {
            if (offset + DIR_ENTRY_SIZE > bytes.size) break

            val etRaw = u8(bytes[offset])
            val inUse = (etRaw and 0x80) != 0
            val et = etRaw and 0x7F

            if (!inUse) {
                offset += DIR_ENTRY_SIZE
                continue
            }

            when (et) {
                TYPE_STREAM_EXT -> {
                    val flags = u8(bytes[offset + 1])
                    val nameLength = u8(bytes[offset + 3])
                    val validDataLength = u64le(bytes, offset + 8)
                    val firstCluster = u32le(bytes, offset + 20).toLong()
                    val dataLength = u64le(bytes, offset + 24)

                    stream = StreamExt(
                        nameLength = nameLength,
                        flags = flags,
                        validDataLength = validDataLength,
                        firstCluster = firstCluster,
                        dataLength = dataLength,
                        streamEntryOffsetInParentBytes = offset.toLong()
                    )
                    expectedNameLen = nameLength
                }

                TYPE_FILE_NAME -> {
                    fileNameOffsets += offset.toLong()

                    val s = decodeUtf16Le(
                        bytes,
                        offset + FILE_NAME_UTF16_OFFSET,
                        FILE_NAME_UTF16_BYTES
                    )
                    nameChars.append(s)

                    if (expectedNameLen >= 0 && nameChars.length >= expectedNameLen) {
                        val finalName = nameChars.toString()
                            .take(expectedNameLen)
                            .trimEnd('\u0000')
                        return SecondaryParseResult(
                            stream = stream,
                            name = finalName,
                            fileNameEntryOffsetsInParentBytes = fileNameOffsets
                        )
                    }
                }
            }

            offset += DIR_ENTRY_SIZE
        }

        val finalName = if (expectedNameLen >= 0) {
            nameChars.toString().take(expectedNameLen).trimEnd('\u0000')
        } else {
            nameChars.toString().trimEnd('\u0000')
        }

        return SecondaryParseResult(
            stream = stream,
            name = finalName.takeIf { it.isNotEmpty() },
            fileNameEntryOffsetsInParentBytes = fileNameOffsets
        )
    }

    // -------------------- stream reading helpers --------------------

    private suspend fun readAllReadableBytes(
        core: NodeCoreMetadata,
        data: RandomAccessData
    ): ByteArray {
        val len = core.readableLength
        if (len == 0L) return ByteArray(0)
        if (len > Int.MAX_VALUE.toLong()) {
            throw IllegalStateException("Stream too large to materialize in memory: len=$len")
        }

        val out = ByteArray(len.toInt())
        var offset = 0
        var pos = 0L
        while (offset < out.size) {
            val tmp = ByteArray(out.size - offset)
            val n = readStreamRange(core, pos, tmp, data)
            if (n <= 0) break

            System.arraycopy(tmp, 0, out, offset, n)

            offset += n
            pos += n
        }
        return if (offset == out.size) out else out.copyOf(offset)
    }

    private suspend fun readContiguousRange(
        data: RandomAccessData,
        core: NodeCoreMetadata,
        fsMeta: ExFatFIleSystemConstantMetadata,
        position: Long,
        out: ByteArray,
        outOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        val firstCluster = core.firstCluster
        if (firstCluster < CLUSTERS_OFFSET) {
            throw IllegalStateException("Invalid first cluster for contiguous stream: $firstCluster")
        }

        var remaining = len
        var dst = outOffset
        var streamPos = position

        while (remaining > 0) {
            val clusterIndex = (streamPos / bytesPerCluster).toInt()
            val inClusterOffset = (streamPos % bytesPerCluster).toInt()

            val clusterNum = firstCluster.toInt() + clusterIndex
            val clusterBytes = readClusterBytes(data, clusterNum, fsMeta)

            val take = min(remaining, bytesPerCluster - inClusterOffset)
            System.arraycopy(clusterBytes, inClusterOffset, out, dst, take)

            dst += take
            streamPos += take
            remaining -= take
        }
    }

    private suspend fun readFatChainedRange(
        data: RandomAccessData,
        core: NodeCoreMetadata,
        fsMeta: ExFatFIleSystemConstantMetadata,
        position: Long,
        out: ByteArray,
        outOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        val firstCluster = core.firstCluster
        if (firstCluster < CLUSTERS_OFFSET) {
            throw IllegalStateException("Invalid first cluster for FAT stream: $firstCluster")
        }

        val chain = resolveFatChain(data, firstCluster.toInt(), core.readableLength)
        if (chain.isEmpty()) {
            throw IllegalStateException("Empty FAT chain for non-empty stream")
        }

        var remaining = len
        var dst = outOffset
        var streamPos = position

        while (remaining > 0) {
            val clusterIndex = (streamPos / bytesPerCluster).toInt()
            val inClusterOffset = (streamPos % bytesPerCluster).toInt()

            if (clusterIndex >= chain.size) {
                throw IllegalStateException(
                    "FAT chain shorter than expected: clusterIndex=$clusterIndex chainSize=${chain.size}"
                )
            }

            val clusterNum = chain[clusterIndex]
            val clusterBytes = readClusterBytes(data, clusterNum, fsMeta)

            val take = min(remaining, bytesPerCluster - inClusterOffset)
            System.arraycopy(clusterBytes, inClusterOffset, out, dst, take)

            dst += take
            streamPos += take
            remaining -= take
        }
    }

    private suspend fun readClusterBytes(
        data: RandomAccessData,
        cluster: Int,
        fsMeta: ExFatFIleSystemConstantMetadata
    ): ByteArray {
        if (cluster < CLUSTERS_OFFSET) {
            throw IllegalStateException("Invalid cluster number: $cluster")
        }

        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()
        val clusterByte =
            fsMeta.heapStartByte + (cluster.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster
        return readAt(data, clusterByte, bytesPerCluster)
    }

    private suspend fun resolveFatChain(
        data: RandomAccessData,
        firstCluster: Int,
        readableLength: Long? = null
    ): IntArray {
        fatChainCacheMutex.withLock {
            fatChainCache[firstCluster]?.let { return it }
        }

        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster
        val maxSteps = readableLength?.let {
            ((readableLength + bytesPerCluster - 1) / bytesPerCluster)
                .coerceAtLeast(1)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        } ?: DEFAULT_WALK_LIMIT

        val chain = fatOperator.walkChain(
            data = data,
            firstCluster = firstCluster,
            fatStartByte = fsMeta.fatStartByte,
            maxSteps = maxSteps
        )

        fatChainCacheMutex.withLock {
            fatChainCache[firstCluster] = chain
        }
        return chain
    }

    companion object {
        private const val DIR_ENTRY_SIZE = 32
        internal const val  MB = 1024 * 1024

        private const val TYPE_FILE_DIR_ENTRY = 0x05
        private const val TYPE_STREAM_EXT = 0x40
        private const val TYPE_FILE_NAME = 0x41

        private const val FILE_NAME_UTF16_OFFSET = 2
        private const val FILE_NAME_UTF16_BYTES = 30

        private const val CLUSTERS_OFFSET = 2

        private const val ATTR_DIRECTORY = 0x0010

        private const val STREAM_FLAG_NO_FAT_CHAIN = 0x02

        private const val DEFAULT_WALK_LIMIT = 1_000_000
        private const val MAX_ROOT_DIR_BYTES_SAFETY = 256L * 1024L * 1024L
    }
}