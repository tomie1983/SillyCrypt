package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.exfat.bitmap.BitmapOperator
import com.dev.exfat.exfat.bootregion.BootRegionOperator
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.bootregion.ExFatFileSystemChangingMetadata
import com.dev.exfat.exfat.bootregion.ExFatFileSystemMetadata
import com.dev.exfat.exfat.cache.LruCache
import com.dev.exfat.exfat.fat.FATOperator
import com.dev.exfat.exfat.fat.FATOperator.Companion.DEFAULT_WALK_LIMIT
import com.dev.exfat.file.ExFATFile
import com.dev.exfat.file.ExFATSeekableFile
import com.dev.exfat.file.SeekableOpenOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class ExFATFS(
    private val dataFactory: RandomAccessDataFactory,
    val name: String
) {
    private val serviceData = dataFactory.create()

    private val bootRegionOperator = BootRegionOperator(serviceData)
    private val fatOperator = FATOperator(serviceData)
    private val bitmapOperator = BitmapOperator(serviceData)

    private val serviceDataMutex = Mutex()
    private val constantMetadataMutex = Mutex()
    private val changingMetadataMutex = Mutex()
    private val rootStateMutex = Mutex()
    private val fatChainCacheMutex = Mutex()
    private val allocationBitmapInfoMutex = Mutex()
    private val allocationMutex = Mutex()

    @Volatile
    private var constantMetadataCache: ExFatFIleSystemConstantMetadata? = null
    @Volatile
    private var changingMetadataCache: ExFatFileSystemChangingMetadata? = null
    @Volatile
    private var rootStateCache: NodeState? = null
    @Volatile
    private var allocationBitmapInfoCache: AllocationBitmapInfo? = null

    private val fatChainCache = LruCache<Int, IntArray>(512)
    private val nodeStateRegistry = ConcurrentHashMap<NodeId, NodeState>()

    private val upCaseTableMutex = Mutex()

    @Volatile
    private var upCaseTableCache: IntArray? = null
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

    suspend fun root(): ExFATFile {
        val rootState = getRootState()
        return ExFATFile(this, rootState, "/", "/")
    }

    suspend fun openSeekable(
        path: String,
        options: SeekableOpenOptions = SeekableOpenOptions.readOnly()
    ): ExFATSeekableFile? {
        val file = getFileFromPath(path) ?: return null
        require(!file.isDirectory) { "Seekable file access is not supported for directories: $path" }
        return file.openSeekable(options)
    }

    internal fun openSeekable(
        state: NodeState,
        displayPath: String,
        options: SeekableOpenOptions
    ): ExFATSeekableFile {
        return ExFATSeekableFile(this, state, displayPath, options)
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

                currentState = internNodeState(next.nodeId, next.core, next.entrySetLocation)
                currentPath = joinPath(currentPath, next.name)
            }

            val displayName = if (currentPath == "/") "/" else parts.last()
            return ExFATFile(this, currentState, displayName, currentPath)
        } finally {
            lookupData.close()
        }
    }

    suspend fun exists(path: String): Boolean = getFileFromPath(path) != null

    private data class DeleteTarget(
        val parentState: NodeState,
        val childName: String,
        val target: ParsedDirectoryNode
    )

    private suspend fun resolveChildForDelete(
        normalizedPath: String,
        data: RandomAccessData
    ): DeleteTarget? {
        val normalized = normalizeAbsolutePath(normalizedPath)
        require(normalized != "/") { "Root directory cannot be deleted" }

        val parts = splitAbsolutePath(normalized)
        if (parts.isEmpty()) return null

        val childName = parts.last()
        val parentPath = if (parts.size == 1) {
            "/"
        } else {
            "/" + parts.dropLast(1).joinToString("/")
        }

        val parentState = resolveDirectoryState(parentPath, data)
            ?: return null

        val target = listDirectoryEntries(parentState, data)
            .firstOrNull { it.name == childName }
            ?: return null

        return DeleteTarget(
            parentState = parentState,
            childName = childName,
            target = target
        )
    }

    private suspend fun deleteSingleFileOrEmptyDirectory(normalizedPath: String): Boolean {
        val data = createDataHandle()

        try {
            val resolved = resolveChildForDelete(normalizedPath, data)
                ?: return false

            val parentState = resolved.parentState
            val target = resolved.target

            return parentState.writeMutex.withLock {
                val freshTarget = listDirectoryEntries(parentState, data)
                    .firstOrNull { it.name == resolved.childName }
                    ?: return@withLock false

                val targetState = internNodeState(
                    nodeId = freshTarget.nodeId,
                    core = freshTarget.core,
                    entrySetLocation = freshTarget.entrySetLocation
                )

                targetState.writeMutex.withLock {
                    if (freshTarget.core.isDirectory) {
                        val children = listDirectoryEntries(targetState, data)
                            .filterNot { it.name == "." || it.name == ".." }

                        check(children.isEmpty()) {
                            "Directory is not empty: $normalizedPath"
                        }
                    }

                    withAllocationLock {
                        val fsMeta = readConstantFileSystemMetadata()
                        val bitmapInfo = readAllocationBitmapInfo()
                        val fatStartBytes = buildFatStartBytes(fsMeta)

                        serviceDataMutex.withLock {
                            bootRegionOperator.setDirty(true)

                            try {
                                freeNodeClustersOnDelete(
                                    core = freshTarget.core,
                                    fsMeta = fsMeta,
                                    bitmapInfo = bitmapInfo,
                                    fatStartBytes = fatStartBytes
                                )

                                markDirectoryEntrySetDeleted(
                                    data = serviceData,
                                    location = freshTarget.entrySetLocation
                                )

                                updatePercentInUse(
                                    data = serviceData,
                                    bitmapInfo = bitmapInfo,
                                    fsMeta = fsMeta
                                )

                                nodeStateRegistry.remove(freshTarget.nodeId)

                                targetState.update(
                                    freshTarget.core.copy(
                                        firstCluster = 0,
                                        dataLength = 0,
                                        validDataLength = 0,
                                        readableLength = 0,
                                        streamFlags = buildStreamFlags(noFatChain = false),
                                        noFatChain = false
                                    ),
                                    null
                                )

                                changingMetadataCache = null

                                if (freshTarget.core.isDirectory) {
                                    allocationBitmapInfoCache = null
                                }
                            } finally {
                                bootRegionOperator.setDirty(false)
                            }
                        }
                    }

                    true
                }
            }
        } finally {
            data.close()
        }
    }

    private suspend fun deleteChildrenIfDirectory(normalizedPath: String): Boolean {
        val data = createDataHandle()

        try {
            val resolved = resolveChildForDelete(normalizedPath, data)
                ?: return false

            val target = resolved.target

            if (!target.core.isDirectory) {
                return true
            }

            val targetState = internNodeState(
                nodeId = target.nodeId,
                core = target.core,
                entrySetLocation = target.entrySetLocation
            )

            val children = listDirectoryEntries(targetState, data)
                .filterNot { it.name == "." || it.name == ".." }
                .map { child ->
                    joinPath(normalizedPath, child.name)
                }

            data.close()

            for (childPath in children) {
                val deleted = delete(
                    path = childPath,
                    recursive = true
                )

                check(deleted) {
                    "Failed to delete child while deleting directory recursively: $childPath"
                }
            }

            return true
        } finally {
            runCatching {
                data.close()
            }
        }
    }

    suspend fun delete(path: String): Boolean {
        return delete(path = path, recursive = false)
    }

    suspend fun deleteRecursively(path: String): Boolean {
        return delete(path = path, recursive = true)
    }

    /**
     * Deletes a file or directory from the exFAT volume.
     *
     * Behavior:
     * - regular file: always deleted;
     * - empty directory: deleted;
     * - non-empty directory:
     *   - recursive=false -> throws IllegalStateException;
     *   - recursive=true -> deletes all children first, then the directory itself.
     *
     * Returns false when [path] does not exist.
     */
    suspend fun delete(
        path: String,
        recursive: Boolean
    ): Boolean {
        val normalized = normalizeAbsolutePath(path)
        require(normalized != "/") { "Root directory cannot be deleted" }

        if (recursive) {
            val exists = deleteChildrenIfDirectory(normalized)
            if (!exists) return false
        }

        return deleteSingleFileOrEmptyDirectory(normalized)
    }

    suspend fun close() {
        nodeStateRegistry.clear()
        fatChainCacheMutex.withLock { fatChainCache.clear() }
        rootStateCache = null
        constantMetadataCache = null
        changingMetadataCache = null
        allocationBitmapInfoCache = null
        serviceData.close()
    }

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
        val created = NodeState(nodeId, core, entrySetLocation)
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
                val rootState = internNodeState(NodeId.Root, rootCore, null)
                rootStateCache = rootState
                rootState
            } finally {
                tempData.close()
            }
        }
    }

    internal fun createDataHandle(): RandomAccessData = dataFactory.create()

    internal suspend fun <T> withAllocationLock(action: suspend () -> T): T {
        return allocationMutex.withLock { action() }
    }


    internal suspend fun createChild(
        parentState: NodeState,
        parentDisplayPath: String,
        name: String,
        isDirectory: Boolean
    ): ExFATFile {
        validateNewChildName(name)
        val parentCore = parentState.snapshotCore()
        require(parentCore.isDirectory) { "Not a directory: $parentDisplayPath" }

        return parentState.writeMutex.withLock {
            val data = createDataHandle()
            var allocatedDirectoryCore: NodeCoreMetadata? = null

            try {
                val existing = listDirectoryEntries(parentState, data).firstOrNull { it.name == name }
                require(existing == null) { "Entry already exists: ${joinPath(parentDisplayPath, name)}" }

                val entrySetSize = (2 + fileNameEntryCount(name)) * DIR_ENTRY_SIZE
                val placement = findOrCreateDirectoryEntryPlacement(parentState, data, entrySetSize)

                if (isDirectory) {
                    allocatedDirectoryCore = allocateStandaloneDirectoryCore()
                    zeroNewDirectoryContent(data, allocatedDirectoryCore)
                }

                val childCore = if (isDirectory) {
                    allocatedDirectoryCore!!
                } else {
                    NodeCoreMetadata(
                        isDirectory = false,
                        attributes = 0,
                        firstCluster = 0,
                        dataLength = 0,
                        validDataLength = 0,
                        readableLength = 0,
                        streamFlags = buildStreamFlags(noFatChain = false),
                        noFatChain = false
                    )
                }

                val finalEntrySet = buildEntrySetBytes(
                    name = name,
                    isDirectory = isDirectory,
                    core = childCore
                )

                val currentParentCore = parentState.snapshotCore()

                writeDirectoryStreamRange(
                    data = data,
                    firstCluster = currentParentCore.firstCluster,
                    noFatChain = currentParentCore.noFatChain,
                    position = placement.offset,
                    bytes = finalEntrySet
                )

                if (placement.appendAtEnd) {
                    val after = placement.offset + finalEntrySet.size
                    val parentSize = parentState.snapshotCore().dataLength

                    if (after + DIR_ENTRY_SIZE <= parentSize) {
                        writeDirectoryStreamRange(
                            data = data,
                            firstCluster = parentState.snapshotCore().firstCluster,
                            noFatChain = parentState.snapshotCore().noFatChain,
                            position = after,
                            bytes = ByteArray(DIR_ENTRY_SIZE)
                        )
                    }
                }

                val nameEntryCount = fileNameEntryCount(name)

                val entrySetLocation = NodeEntrySetLocation(
                    parentDirFirstCluster = parentState.snapshotCore().firstCluster,
                    parentDirNoFatChain = parentState.snapshotCore().noFatChain,
                    primaryEntryOffsetInParentBytes = placement.offset,
                    streamEntryOffsetInParentBytes = placement.offset + DIR_ENTRY_SIZE,
                    fileNameEntryOffsetsInParentBytes = LongArray(nameEntryCount) { idx ->
                        placement.offset + DIR_ENTRY_SIZE.toLong() * (2L + idx)
                    }
                )

                val nodeId = NodeId.DirectoryEntry(
                    parentDirFirstCluster = parentState.snapshotCore().firstCluster,
                    primaryEntryOffsetInParentBytes = placement.offset
                )

                val state = internNodeState(nodeId, childCore, entrySetLocation)

                ExFATFile(
                    fileSystem = this,
                    state = state,
                    displayName = name,
                    displayPath = joinPath(parentDisplayPath, name)
                )
            } catch (t: Throwable) {
                allocatedDirectoryCore?.let {
                    runCatching { freeStandaloneNode(it) }
                }
                throw t
            } finally {
                data.close()
            }
        }
    }

    private suspend fun createNodeByPath(path: String, isDirectory: Boolean): ExFATFile {
        val normalized = normalizeAbsolutePath(path)
        require(normalized != "/") { "Cannot create root directory" }
        require(!exists(normalized)) { "Entry already exists: $normalized" }

        val parentPath = normalized.substringBeforeLast('/', "/").ifEmpty { "/" }
        val childName = normalized.substringAfterLast('/')
        val parent = getFileFromPath(parentPath)
            ?: throw IllegalArgumentException("Parent directory does not exist: $parentPath")
        require(parent.isDirectory) { "Parent path is not a directory: $parentPath" }
        return createChild(parent.state, parent.path, childName, isDirectory)
    }

    private data class DirectoryEntryPlacement(
        val offset: Long,
        val appendAtEnd: Boolean
    )

    private suspend fun findOrCreateDirectoryEntryPlacement(
        dirState: NodeState,
        data: RandomAccessData,
        entrySetSizeBytes: Int
    ): DirectoryEntryPlacement {
        val requiredEntries = entrySetSizeBytes / DIR_ENTRY_SIZE
        while (true) {
            val core = dirState.snapshotCore()
            val bytes = readAllReadableBytes(core, data)
            findDirectoryEntryPlacement(bytes, requiredEntries)?.let { return it }

            val fsMeta = readConstantFileSystemMetadata()
            val currentClusters = clustersForSize(core.dataLength, fsMeta.bytesPerCluster)
            val targetClusters = (currentClusters + 1).coerceAtLeast(1)
            val targetSize = targetClusters.toLong() * fsMeta.bytesPerCluster
            ensureDirectoryCapacityLocked(dirState, targetSize)
        }
    }

    private fun findDirectoryEntryPlacement(
        dirBytes: ByteArray,
        requiredEntries: Int
    ): DirectoryEntryPlacement? {
        var runStart = -1
        var runCount = 0
        var runStartedAtEndMarker = false

        var i = 0
        while (i + DIR_ENTRY_SIZE <= dirBytes.size) {
            val raw = u8(dirBytes[i])
            val free = raw == 0x00 || (raw and 0x80) == 0
            if (free) {
                if (runCount == 0) {
                    runStart = i
                    runStartedAtEndMarker = raw == 0x00
                }
                runCount++
                if (runCount >= requiredEntries) {
                    return DirectoryEntryPlacement(runStart.toLong(), runStartedAtEndMarker)
                }
            } else {
                runStart = -1
                runCount = 0
                runStartedAtEndMarker = false
            }
            i += DIR_ENTRY_SIZE
        }
        return null
    }

    private suspend fun ensureDirectoryCapacityLocked(
        dirState: NodeState,
        targetSize: Long
    ) {
        val current = dirState.snapshotCore()
        if (targetSize <= current.dataLength) return

        val fsMeta = readConstantFileSystemMetadata()
        val oldClusters = clustersForSize(current.dataLength, fsMeta.bytesPerCluster)
        val requiredClusters = clustersForSize(targetSize, fsMeta.bytesPerCluster)
        if (requiredClusters <= oldClusters) {
            val finalCore = current.copy(
                dataLength = targetSize,
                validDataLength = targetSize,
                readableLength = targetSize
            )
            updateFileMetadata(dirState, finalCore)
            return
        }

        val tempData = createDataHandle()
        try {
            val plannedCore = allocateExpansion(dirState, current, requiredClusters, oldClusters)
            val finalSize = requiredClusters.toLong() * fsMeta.bytesPerCluster
            zeroFillRange(tempData, plannedCore, current.dataLength, finalSize)
            val finalCore = plannedCore.copy(
                dataLength = finalSize,
                validDataLength = finalSize,
                readableLength = finalSize
            )
            updateFileMetadata(dirState, finalCore)
        } finally {
            tempData.close()
        }
    }

    private suspend fun allocateStandaloneDirectoryCore(): NodeCoreMetadata {
        val fsMeta = readConstantFileSystemMetadata()
        val bitmapInfo = readAllocationBitmapInfo()
        val fatStartBytes = buildFatStartBytes(fsMeta)

        return withAllocationLock {
            serviceDataMutex.withLock {
                bootRegionOperator.setDirty(true)

                try {
                    val planned = allocateForEmptyFile(
                        current = NodeCoreMetadata(
                            isDirectory = true,
                            attributes = ATTR_DIRECTORY,
                            firstCluster = 0,
                            dataLength = 0,
                            validDataLength = 0,
                            readableLength = 0,
                            streamFlags = buildStreamFlags(noFatChain = false),
                            noFatChain = false
                        ),
                        requiredClusters = 1,
                        fsMeta = fsMeta,
                        bitmapInfo = bitmapInfo,
                        fatStartBytes = fatStartBytes
                    ).copy(
                        dataLength = fsMeta.bytesPerCluster,
                        validDataLength = fsMeta.bytesPerCluster,
                        readableLength = fsMeta.bytesPerCluster,
                        attributes = ATTR_DIRECTORY
                    )

                    updatePercentInUse(serviceData, bitmapInfo, fsMeta)
                    planned
                } finally {
                    bootRegionOperator.setDirty(false)
                }
            }
        }
    }

    private suspend fun zeroNewDirectoryContent(data: RandomAccessData, core: NodeCoreMetadata) {
        val fsMeta = readConstantFileSystemMetadata()
        val zeroLen = clustersForSize(core.dataLength, fsMeta.bytesPerCluster).toLong() * fsMeta.bytesPerCluster
        zeroFillRange(data, core, 0L, zeroLen)
    }

    private suspend fun freeStandaloneNode(core: NodeCoreMetadata) {
        if (core.firstCluster < CLUSTERS_OFFSET || core.dataLength <= 0L) return
        val fsMeta = readConstantFileSystemMetadata()
        val bitmapInfo = readAllocationBitmapInfo()
        val fatStartBytes = buildFatStartBytes(fsMeta)
        val clusters = clustersForSize(core.dataLength, fsMeta.bytesPerCluster)
        withAllocationLock {
            serviceDataMutex.withLock {
                bootRegionOperator.setDirty(true)
                if (core.noFatChain) {
                    bitmapOperator.markFreeRange(bitmapInfo.startByte, core.firstCluster.toInt(), clusters, fsMeta.clusterCount)
                } else {
                    val chain = fatOperator.walkChain(serviceData, core.firstCluster.toInt(), fatStartBytes[0], maxOf(clusters, 1))
                    bitmapOperator.markFreeClusters(bitmapInfo.startByte, chain, fsMeta.clusterCount)
                    fatOperator.freeChain(core.firstCluster.toInt(), fatStartBytes, DEFAULT_WALK_LIMIT)
                    fatChainCacheMutex.withLock { fatChainCache.remove(core.firstCluster.toInt()) }
                }
                updatePercentInUse(serviceData, bitmapInfo, fsMeta)
                bootRegionOperator.setDirty(false)
            }
        }
    }

    private suspend fun buildEntrySetBytes(
        name: String,
        isDirectory: Boolean,
        core: NodeCoreMetadata
    ): ByteArray {
        val nameEntries = buildFileNameEntries(name)
        val secondaryCount = 1 + nameEntries.size

        val primary = ByteArray(DIR_ENTRY_SIZE)
        primary[0] = (0x80 or TYPE_FILE_DIR_ENTRY).toByte()
        primary[1] = secondaryCount.toByte()
        putU16le(primary, 4, if (isDirectory) ATTR_DIRECTORY else 0)

        val stream = ByteArray(DIR_ENTRY_SIZE)
        stream[0] = (0x80 or TYPE_STREAM_EXT).toByte()
        stream[1] = buildStreamFlags(core.noFatChain).toByte()
        stream[3] = name.length.toByte()

        putU16le(stream, 4, computeNameHash(name))
        putU64le(stream, 8, core.validDataLength)
        putU32le(stream, 20, core.firstCluster.toInt())
        putU64le(stream, 24, core.dataLength)

        val parts = ArrayList<ByteArray>(2 + nameEntries.size)
        parts += primary
        parts += stream
        parts.addAll(nameEntries)

        val checksum = computeEntrySetChecksum(parts)
        putU16le(primary, 2, checksum)

        return concatChunks(parts, parts.sumOf { it.size })
    }

    private fun buildFileNameEntries(name: String): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < name.length) {
            val entry = ByteArray(DIR_ENTRY_SIZE)
            entry[0] = (0x80 or TYPE_FILE_NAME).toByte()
            val part = name.substring(offset, min(offset + FILE_NAME_CHARS_PER_ENTRY, name.length))
            val encoded = part.toByteArray(Charsets.UTF_16LE)
            System.arraycopy(encoded, 0, entry, FILE_NAME_UTF16_OFFSET, encoded.size)
            result += entry
            offset += FILE_NAME_CHARS_PER_ENTRY
        }
        return result
    }

    private fun computeEntrySetChecksum(entrySet: ByteArray): Int {
        var sum = 0

        for (i in entrySet.indices) {
            if (i == 2 || i == 3) continue

            val value = entrySet[i].toInt() and 0xFF
            sum = (((sum ushr 1) or ((sum and 1) shl 15)) + value) and 0xFFFF
        }

        return sum
    }

    private suspend fun computeNameHash(name: String): Int {
        val upCaseTable = readUpCaseTable()

        var hash = 0

        for (ch in name) {
            val upper = upCaseTable[ch.code]

            val low = upper and 0xFF
            val high = (upper ushr 8) and 0xFF

            hash = rotateNameHash(hash, low)
            hash = rotateNameHash(hash, high)
        }

        return hash
    }

    private suspend fun readUpCaseTable(): IntArray {
        upCaseTableCache?.let { return it }

        return upCaseTableMutex.withLock {
            upCaseTableCache?.let { return@withLock it }

            val info = readUpCaseTableInfo()
            val fsMeta = readConstantFileSystemMetadata()
            val data = createDataHandle()

            try {
                require(info.firstCluster >= CLUSTERS_OFFSET) {
                    "Invalid UpCase Table firstCluster=${info.firstCluster}"
                }
                require(info.dataLength > 0) {
                    "Invalid UpCase Table dataLength=${info.dataLength}"
                }
                require(info.dataLength <= Int.MAX_VALUE) {
                    "UpCase Table too large: ${info.dataLength}"
                }

                val startByte =
                    fsMeta.heapStartByte +
                            (info.firstCluster.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster

                val bytes = readAt(data, startByte, info.dataLength.toInt())

                val actualChecksum = computeUpCaseTableChecksum(bytes)
                require(actualChecksum == info.checksum) {
                    "Bad UpCase Table checksum: actual=0x${actualChecksum.toString(16)}, " +
                            "expected=0x${info.checksum.toString(16)}"
                }

                val table = decodeUpCaseTable(bytes)
                upCaseTableCache = table
                table
            } finally {
                data.close()
            }
        }
    }

    private fun computeUpCaseTableChecksum(bytes: ByteArray): Long {
        var sum = 0u

        for (byte in bytes) {
            sum = (sum shl 31) or (sum shr 1)
            sum += (byte.toInt() and 0xFF).toUInt()
        }

        return sum.toLong() and 0xFFFF_FFFFL
    }

    private suspend fun readUpCaseTableInfo(): UpCaseTableInfo {
        val rootState = getRootState()
        val data = createDataHandle()

        try {
            val rootBytes = readAllReadableBytes(rootState.snapshotCore(), data)

            var i = 0
            while (i + DIR_ENTRY_SIZE <= rootBytes.size) {
                val raw = u8(rootBytes[i])
                if (raw == 0x00) break

                val inUse = (raw and 0x80) != 0
                val type = raw and 0x7F

                if (inUse && type == TYPE_UPCASE_TABLE) {
                    val checksum = u32le(rootBytes, i + 4).toLong() and 0xFFFF_FFFFL
                    val firstCluster = u32le(rootBytes, i + 20)
                    val dataLength = u64le(rootBytes, i + 24)

                    return UpCaseTableInfo(
                        checksum = checksum,
                        firstCluster = firstCluster,
                        dataLength = dataLength
                    )
                }

                i += DIR_ENTRY_SIZE
            }

            error("UpCase Table entry not found in root directory")
        } finally {
            data.close()
        }
    }

    private fun decodeUpCaseTable(bytes: ByteArray): IntArray {
        val result = IntArray(UTF16_CODE_UNIT_COUNT) { it }

        var tableIndex = 0
        var offset = 0

        while (offset + 1 < bytes.size && tableIndex < UTF16_CODE_UNIT_COUNT) {
            val value = u16le(bytes, offset)
            offset += 2

            if (value == 0xFFFF) {
                require(offset + 1 < bytes.size) { "Broken compressed UpCase Table" }

                val identityRunLength = u16le(bytes, offset)
                offset += 2

                tableIndex += identityRunLength
            } else {
                result[tableIndex] = value
                tableIndex++
            }
        }

        return result
    }

    private fun rotateNameHash(hash: Int, value: Int): Int {
        return (((hash and 1) shl 15) + (hash ushr 1) + value) and 0xFFFF
    }

    private fun computeEntrySetChecksum(entries: List<ByteArray>): Int {
        var sum = 0
        entries.forEachIndexed { entryIndex, entry ->
            for (i in entry.indices) {
                if (entryIndex == 0 && (i == 2 || i == 3)) continue
                val value = entry[i].toInt() and 0xFF
                sum = (((sum ushr 1) or ((sum and 1) shl 15)) + value) and 0xFFFF
            }
        }
        return sum
    }

    private fun fileNameEntryCount(name: String): Int {
        return if (name.isEmpty()) 0 else (name.length + FILE_NAME_CHARS_PER_ENTRY - 1) / FILE_NAME_CHARS_PER_ENTRY
    }

    private fun validateNewChildName(name: String) {
        require(name.isNotEmpty()) { "Entry name must not be empty" }
        require(name != "." && name != "..") { "Special names are not supported: $name" }
        require('/' !in name) { "Entry name must not contain '/': $name" }
        require('\u0000' !in name) { "Entry name must not contain NUL" }
        require(name.length <= MAX_FILE_NAME_CHARS) {
            "Entry name is too long: ${name.length} > $MAX_FILE_NAME_CHARS"
        }
    }

    internal suspend fun listDirectory(
        dirState: NodeState,
        parentDisplayPath: String,
        data: RandomAccessData
    ): List<ExFATFile> {
        val dirCore = dirState.snapshotCore()
        require(dirCore.isDirectory) { "Not a directory" }
        return listDirectoryEntries(dirState, data).map { parsed ->
            val childState = internNodeState(parsed.nodeId, parsed.core, parsed.entrySetLocation)
            val childDisplayPath = joinPath(parentDisplayPath, parsed.name)
            ExFATFile(this, childState, parsed.name, childDisplayPath)
        }
    }

    private suspend fun resolveDirectoryState(
        normalizedDirectoryPath: String,
        data: RandomAccessData
    ): NodeState? {
        val normalized = normalizeAbsolutePath(normalizedDirectoryPath)
        if (normalized == "/") return getRootState()

        var currentState = getRootState()
        for (part in splitAbsolutePath(normalized)) {
            val currentCore = currentState.snapshotCore()
            if (!currentCore.isDirectory) return null

            val next = listDirectoryEntries(currentState, data).firstOrNull { it.name == part }
                ?: return null
            if (!next.core.isDirectory) return null

            currentState = internNodeState(next.nodeId, next.core, next.entrySetLocation)
        }
        return currentState
    }

    private suspend fun freeNodeClustersOnDelete(
        core: NodeCoreMetadata,
        fsMeta: ExFatFIleSystemConstantMetadata,
        bitmapInfo: AllocationBitmapInfo,
        fatStartBytes: LongArray
    ) {
        val clusterCount = clustersForSize(core.dataLength, fsMeta.bytesPerCluster)
        if (clusterCount == 0 || core.firstCluster < CLUSTERS_OFFSET) return

        if (core.noFatChain) {
            bitmapOperator.markFreeRange(
                bitmapStartByte = bitmapInfo.startByte,
                firstCluster = core.firstCluster.toInt(),
                count = clusterCount,
                clusterCount = fsMeta.clusterCount
            )
        } else {
            val walkLimit = (fsMeta.clusterCount + CLUSTERS_OFFSET).coerceAtLeast(DEFAULT_WALK_LIMIT)
            val chain = fatOperator.walkChain(
                data = serviceData,
                firstCluster = core.firstCluster.toInt(),
                fatStartByte = fatStartBytes[0],
                maxSteps = walkLimit
            )
            if (chain.isNotEmpty()) {
                bitmapOperator.markFreeClusters(bitmapInfo.startByte, chain, fsMeta.clusterCount)
                fatOperator.freeChain(core.firstCluster.toInt(), fatStartBytes, walkLimit)
                fatChainCacheMutex.withLock { fatChainCache.remove(core.firstCluster.toInt()) }
            }
        }
    }

    private suspend fun markDirectoryEntrySetDeleted(
        data: RandomAccessData,
        location: NodeEntrySetLocation
    ) {
        val primary = readDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = location.primaryEntryOffsetInParentBytes,
            length = DIR_ENTRY_SIZE
        )
        val secondaryCount = u8(primary[1])
        markDirectoryEntryDeleted(data, location, location.primaryEntryOffsetInParentBytes, primary)

        for (i in 0 until secondaryCount) {
            val secondaryOffset = location.primaryEntryOffsetInParentBytes + DIR_ENTRY_SIZE.toLong() * (i + 1)
            val secondary = readDirectoryStreamRange(
                data = data,
                firstCluster = location.parentDirFirstCluster,
                noFatChain = location.parentDirNoFatChain,
                position = secondaryOffset,
                length = DIR_ENTRY_SIZE
            )
            markDirectoryEntryDeleted(data, location, secondaryOffset, secondary)
        }
    }

    private suspend fun markDirectoryEntryDeleted(
        data: RandomAccessData,
        location: NodeEntrySetLocation,
        entryOffset: Long,
        entryBytes: ByteArray
    ) {
        entryBytes[0] = (u8(entryBytes[0]) and 0x7F).toByte()
        writeDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = entryOffset,
            bytes = entryBytes
        )
    }

    internal suspend fun readSeekableRange(
        state: NodeState,
        position: Long,
        dst: ByteArray,
        dstOffset: Int,
        length: Int,
        data: RandomAccessData
    ): Int {
        val core = state.snapshotCore()
        if (position >= core.dataLength) return -1
        val totalToReturn = min(length.toLong(), core.dataLength - position).toInt()
        val readablePart = if (position < core.readableLength) {
            min(totalToReturn.toLong(), core.readableLength - position).toInt()
        } else 0
        var copied = 0
        if (readablePart > 0) {
            if (dstOffset == 0 && totalToReturn == dst.size && readablePart == totalToReturn) {
                val n = readStreamRange(core, position, dst, data)
                if (n <= 0) return n
                copied = n
            } else {
                val tmp = ByteArray(readablePart)
                val n = readStreamRange(core, position, tmp, data)
                if (n <= 0) return n
                System.arraycopy(tmp, 0, dst, dstOffset, n)
                copied = n
            }
        }
        val zeroPart = totalToReturn - readablePart
        if (zeroPart > 0) {
            dst.fill(0, dstOffset + copied, dstOffset + copied + zeroPart)
            copied += zeroPart
        }
        return copied
    }

    internal suspend fun writeSeekableRange(
        state: NodeState,
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        length: Int,
        data: RandomAccessData
    ): Int {
        if (length == 0) return 0
        return state.writeMutex.withLock {
            doWriteSeekableRange(state, position, src, srcOffset, length, data)
        }
    }

    internal suspend fun truncateSeekable(state: NodeState, newSize: Long) {
        state.writeMutex.withLock {
            doTruncateSeekable(state, newSize)
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
            readContiguousRange(data, core, fsMeta, position, buf, 0, maxToRead, bytesPerCluster)
            return maxToRead
        }

        readFatChainedRange(data, core, fsMeta, position, buf, 0, maxToRead, bytesPerCluster)
        return maxToRead
    }

    private suspend fun listDirectoryEntries(
        dirState: NodeState,
        data: RandomAccessData
    ): List<ParsedDirectoryNode> {
        val dirCore = dirState.snapshotCore()
        require(dirCore.isDirectory) { "Not a directory" }
        if (dirCore.readableLength == 0L) return emptyList()
        val dirBytes = readAllReadableBytes(dirCore, data)
        return parseDirectory(dirBytes, dirCore)
    }

    private fun parseDirectory(
        dirBytes: ByteArray,
        parentCore: NodeCoreMetadata
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
                    val entrySet = dirBytes.copyOfRange(i, secondariesEnd)
                    val storedChecksum = u16le(entrySet, 2)
                    val actualChecksum = computeEntrySetChecksum(entrySet)

                    if (storedChecksum != actualChecksum) {
                        i += DIR_ENTRY_SIZE * (1 + secondaryCount)
                        continue
                    }
                    val secondaryInfo = parseSecondariesForFile(dirBytes, secondariesStart, secondaryCount)
                    val stream = secondaryInfo.stream
                    val name = secondaryInfo.name
                    if (!name.isNullOrEmpty() && stream != null) {
                        val dataLength = stream.dataLength
                        val validDataLength = stream.validDataLength
                        val readableLength = min(validDataLength, dataLength).coerceAtLeast(0L)
                        val nodeId = NodeId.DirectoryEntry(
                            parentDirFirstCluster = parentCore.firstCluster,
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
                            parentDirFirstCluster = parentCore.firstCluster,
                            parentDirNoFatChain = parentCore.noFatChain,
                            primaryEntryOffsetInParentBytes = primaryEntryOffset,
                            streamEntryOffsetInParentBytes = stream.streamEntryOffsetInParentBytes,
                            fileNameEntryOffsetsInParentBytes = secondaryInfo.fileNameEntryOffsetsInParentBytes.toLongArray()
                        )
                        out += ParsedDirectoryNode(nodeId, name, core, entrySetLocation)
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

    private data class AllocationBitmapInfo(
        val firstCluster: Int,
        val dataLength: Long,
        val startByte: Long
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
                    if ((flags and STREAM_FLAG_ALLOCATION_POSSIBLE) == 0) {
                        offset += DIR_ENTRY_SIZE
                        continue
                    }
                    val nameLength = u8(bytes[offset + 3])
                    val validDataLength = u64le(bytes, offset + 8)
                    val firstCluster = u32le(bytes, offset + 20).toLong()
                    val dataLength = u64le(bytes, offset + 24)
                    stream = StreamExt(nameLength, flags, validDataLength, firstCluster, dataLength, offset.toLong())
                    expectedNameLen = nameLength
                }
                TYPE_FILE_NAME -> {
                    fileNameOffsets += offset.toLong()
                    val s = decodeUtf16Le(bytes, offset + FILE_NAME_UTF16_OFFSET, FILE_NAME_UTF16_BYTES)
                    nameChars.append(s)
                    if (expectedNameLen >= 0 && nameChars.length >= expectedNameLen) {
                        val finalName = nameChars.toString().take(expectedNameLen).trimEnd('\u0000')
                        return SecondaryParseResult(stream, finalName, fileNameOffsets)
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
        return SecondaryParseResult(stream, finalName.takeIf { it.isNotEmpty() }, fileNameOffsets)
    }

    private suspend fun readAllReadableBytes(core: NodeCoreMetadata, data: RandomAccessData): ByteArray {
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
        val clusterByte = fsMeta.heapStartByte + (cluster.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster
        return readAt(data, clusterByte, bytesPerCluster)
    }

    private suspend fun writeContiguousRange(
        data: RandomAccessData,
        firstCluster: Long,
        fsMeta: ExFatFIleSystemConstantMetadata,
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        var remaining = len
        var srcPos = srcOffset
        var streamPos = position
        while (remaining > 0) {
            val clusterIndex = (streamPos / bytesPerCluster).toInt()
            val inClusterOffset = (streamPos % bytesPerCluster).toInt()
            val clusterNum = firstCluster.toInt() + clusterIndex
            val take = min(remaining, bytesPerCluster - inClusterOffset)
            val absPos = fsMeta.heapStartByte + (clusterNum.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster + inClusterOffset
            writeAt(data, absPos, src.copyOfRange(srcPos, srcPos + take))
            srcPos += take
            streamPos += take
            remaining -= take
        }
    }

    private suspend fun writeFatChainedRange(
        data: RandomAccessData,
        firstCluster: Long,
        fsMeta: ExFatFIleSystemConstantMetadata,
        streamLengthHint: Long,
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        val chain = resolveFatChain(data, firstCluster.toInt(), streamLengthHint)
        var remaining = len
        var srcPos = srcOffset
        var streamPos = position
        while (remaining > 0) {
            val clusterIndex = (streamPos / bytesPerCluster).toInt()
            val inClusterOffset = (streamPos % bytesPerCluster).toInt()
            val clusterNum = chain[clusterIndex]
            val take = min(remaining, bytesPerCluster - inClusterOffset)
            val absPos = fsMeta.heapStartByte + (clusterNum.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster + inClusterOffset
            writeAt(data, absPos, src.copyOfRange(srcPos, srcPos + take))
            srcPos += take
            streamPos += take
            remaining -= take
        }
    }

    private suspend fun zeroFillRange(data: RandomAccessData, core: NodeCoreMetadata, from: Long, until: Long) {
        if (until <= from) return
        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()
        var pos = from
        while (pos < until) {
            val take = min(ZERO_CHUNK_SIZE.toLong(), until - pos).toInt()
            val zeros = ByteArray(take)
            if (core.noFatChain) {
                writeContiguousRange(data, core.firstCluster, fsMeta, pos, zeros, 0, take, bytesPerCluster)
            } else {
                writeFatChainedRange(data, core.firstCluster, fsMeta, maxOf(core.dataLength, until), pos, zeros, 0, take, bytesPerCluster)
            }
            pos += take
        }
    }

    private suspend fun writeWithinExistingAllocation(
        data: RandomAccessData,
        core: NodeCoreMetadata,
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        length: Int
    ) {
        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()
        if (core.noFatChain) {
            writeContiguousRange(data, core.firstCluster, fsMeta, position, src, srcOffset, length, bytesPerCluster)
        } else {
            writeFatChainedRange(data, core.firstCluster, fsMeta, maxOf(core.dataLength, position + length), position, src, srcOffset, length, bytesPerCluster)
        }
    }

    private suspend fun doWriteSeekableRange(
        state: NodeState,
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        length: Int,
        data: RandomAccessData
    ): Int {
        val current = state.snapshotCore()
        val fsMeta = readConstantFileSystemMetadata()
        val oldSize = current.dataLength
        val endExclusive = position + length.toLong()
        val oldClusters = clustersForSize(oldSize, fsMeta.bytesPerCluster)
        val requiredClusters = clustersForSize(endExclusive, fsMeta.bytesPerCluster)

        if (requiredClusters <= oldClusters) {
            if (position > oldSize) zeroFillRange(data, current, oldSize, position)
            writeWithinExistingAllocation(data, current, position, src, srcOffset, length)
            if (endExclusive > oldSize) {
                val nextCore = current.copy(
                    dataLength = endExclusive,
                    validDataLength = endExclusive,
                    readableLength = endExclusive
                )
                updateFileMetadata(state, nextCore)
            }
            return length
        }

        val plannedCore = allocateExpansion(state, current, requiredClusters, oldClusters)
        if (position > oldSize) zeroFillRange(data, plannedCore, oldSize, position)
        writeWithinExistingAllocation(data, plannedCore, position, src, srcOffset, length)
        val finalCore = plannedCore.copy(
            dataLength = endExclusive,
            validDataLength = endExclusive,
            readableLength = endExclusive
        )
        updateFileMetadata(state, finalCore)
        return length
    }

    private suspend fun doTruncateSeekable(state: NodeState, newSize: Long) {
        val current = state.snapshotCore()
        if (newSize == current.dataLength) return
        val fsMeta = readConstantFileSystemMetadata()
        val oldClusters = clustersForSize(current.dataLength, fsMeta.bytesPerCluster)
        val newClusters = clustersForSize(newSize, fsMeta.bytesPerCluster)

        if (newSize > current.dataLength) {
            val tempData = createDataHandle()
            try {
                val dummy = ByteArray(0)
                // allocate as needed, then zero-fill the growth
                if (newClusters > oldClusters) {
                    val plannedCore = allocateExpansion(state, current, newClusters, oldClusters)
                    zeroFillRange(tempData, plannedCore, current.dataLength, newSize)
                    val finalCore = plannedCore.copy(
                        dataLength = newSize,
                        validDataLength = newSize,
                        readableLength = newSize
                    )
                    updateFileMetadata(state, finalCore)
                } else {
                    zeroFillRange(tempData, current, current.dataLength, newSize)
                    val finalCore = current.copy(
                        dataLength = newSize,
                        validDataLength = newSize,
                        readableLength = newSize
                    )
                    updateFileMetadata(state, finalCore)
                }
            } finally {
                tempData.close()
            }
            return
        }

        withAllocationLock {
            val bitmapInfo = readAllocationBitmapInfo()
            val fatStartBytes = buildFatStartBytes(fsMeta)
            serviceDataMutex.withLock {
                bootRegionOperator.setDirty(true)

                if (newSize == 0L) {
                    if (oldClusters > 0 && current.firstCluster >= CLUSTERS_OFFSET) {
                        if (current.noFatChain) {
                            bitmapOperator.markFreeRange(bitmapInfo.startByte, current.firstCluster.toInt(), oldClusters, fsMeta.clusterCount)
                        } else {
                            val chain = fatOperator.walkChain(serviceData, current.firstCluster.toInt(), fatStartBytes[0], maxOf(oldClusters, 1))
                            bitmapOperator.markFreeClusters(bitmapInfo.startByte, chain, fsMeta.clusterCount)
                            fatOperator.freeChain(current.firstCluster.toInt(), fatStartBytes, DEFAULT_WALK_LIMIT)
                            fatChainCacheMutex.withLock { fatChainCache.remove(current.firstCluster.toInt()) }
                        }
                    }

                    val cleared = current.copy(
                        firstCluster = 0,
                        dataLength = 0,
                        validDataLength = 0,
                        readableLength = 0,
                        streamFlags = buildStreamFlags(noFatChain = false),
                        noFatChain = false
                    )
                    updateStreamEntryOnDisk(serviceData, state, cleared)
                    updatePercentInUse(serviceData, bitmapInfo, fsMeta)
                    state.update(cleared, state.snapshotEntrySetLocation())
                    bootRegionOperator.setDirty(false)
                    return@withLock
                }

                if (newClusters < oldClusters) {
                    if (current.noFatChain) {
                        val freeFirst = current.firstCluster.toInt() + newClusters
                        val freeCount = oldClusters - newClusters
                        bitmapOperator.markFreeRange(bitmapInfo.startByte, freeFirst, freeCount, fsMeta.clusterCount)
                    } else {
                        val detached = fatOperator.detachTail(current.firstCluster.toInt(), newClusters, fatStartBytes)
                        if (detached != null) {
                            val detachedChain = fatOperator.walkChain(serviceData, detached, fatStartBytes[0], DEFAULT_WALK_LIMIT)
                            bitmapOperator.markFreeClusters(bitmapInfo.startByte, detachedChain, fsMeta.clusterCount)
                            fatOperator.freeChain(detached, fatStartBytes, DEFAULT_WALK_LIMIT)
                            fatChainCacheMutex.withLock { fatChainCache.remove(current.firstCluster.toInt()) }
                        }
                    }
                }

                val truncated = current.copy(
                    dataLength = newSize,
                    validDataLength = newSize,
                    readableLength = newSize
                )
                updateStreamEntryOnDisk(serviceData, state, truncated)
                updatePercentInUse(serviceData, bitmapInfo, fsMeta)
                state.update(truncated, state.snapshotEntrySetLocation())
                bootRegionOperator.setDirty(false)
            }
        }
    }

    private suspend fun allocateExpansion(
        state: NodeState,
        current: NodeCoreMetadata,
        requiredClusters: Int,
        oldClusters: Int
    ): NodeCoreMetadata {
        return withAllocationLock {
            val fsMeta = readConstantFileSystemMetadata()
            val bitmapInfo = readAllocationBitmapInfo()
            val fatStartBytes = buildFatStartBytes(fsMeta)
            serviceDataMutex.withLock {
                bootRegionOperator.setDirty(true)
                val additional = requiredClusters - oldClusters
                val planned = when {
                    oldClusters == 0 -> allocateForEmptyFile(current, additional, fsMeta, bitmapInfo, fatStartBytes)
                    current.noFatChain -> growContiguousFile(current, oldClusters, additional, fsMeta, bitmapInfo, fatStartBytes)
                    else -> growFatFile(current, additional, fsMeta, bitmapInfo, fatStartBytes)
                }
                updatePercentInUse(serviceData, bitmapInfo, fsMeta)
                planned
            }
        }
    }

    private suspend fun allocateForEmptyFile(
        current: NodeCoreMetadata,
        requiredClusters: Int,
        fsMeta: ExFatFIleSystemConstantMetadata,
        bitmapInfo: AllocationBitmapInfo,
        fatStartBytes: LongArray
    ): NodeCoreMetadata {
        require(requiredClusters > 0)

        val run = bitmapOperator.findContiguousFreeRun(
            data = serviceData,
            bitmapStartByte = bitmapInfo.startByte,
            clusterCount = fsMeta.clusterCount,
            requiredLength = requiredClusters
        )

        return if (run != null) {
            bitmapOperator.markAllocatedRange(
                bitmapStartByte = bitmapInfo.startByte,
                firstCluster = run.firstCluster,
                count = requiredClusters,
                clusterCount = fsMeta.clusterCount
            )

            current.copy(
                firstCluster = run.firstCluster.toLong(),
                streamFlags = buildStreamFlags(noFatChain = true),
                noFatChain = true
            )
        } else {
            val clusters = bitmapOperator.findFreeClusters(
                data = serviceData,
                bitmapStartByte = bitmapInfo.startByte,
                clusterCount = fsMeta.clusterCount,
                requiredCount = requiredClusters
            )

            require(clusters.size == requiredClusters) { "No space left on device" }

            bitmapOperator.markAllocatedClusters(
                bitmapStartByte = bitmapInfo.startByte,
                clusters = clusters,
                clusterCount = fsMeta.clusterCount
            )

            fatOperator.writeChain(clusters, fatStartBytes)

            current.copy(
                firstCluster = clusters.first().toLong(),
                streamFlags = buildStreamFlags(noFatChain = false),
                noFatChain = false
            )
        }
    }

    private suspend fun growContiguousFile(
        current: NodeCoreMetadata,
        oldClusters: Int,
        additional: Int,
        fsMeta: ExFatFIleSystemConstantMetadata,
        bitmapInfo: AllocationBitmapInfo,
        fatStartBytes: LongArray
    ): NodeCoreMetadata {
        val extensionStart = current.firstCluster.toInt() + oldClusters
        val maxCluster = CLUSTERS_OFFSET + fsMeta.clusterCount - 1

        var canExtendContiguously = extensionStart + additional - 1 <= maxCluster

        if (canExtendContiguously) {
            for (c in extensionStart until extensionStart + additional) {
                if (bitmapOperator.isAllocated(serviceData, bitmapInfo.startByte, c, fsMeta.clusterCount)) {
                    canExtendContiguously = false
                    break
                }
            }
        }

        if (canExtendContiguously) {
            bitmapOperator.markAllocatedRange(
                bitmapStartByte = bitmapInfo.startByte,
                firstCluster = extensionStart,
                count = additional,
                clusterCount = fsMeta.clusterCount
            )

            return current.copy(
                streamFlags = buildStreamFlags(noFatChain = true),
                noFatChain = true
            )
        }

        val newClusters = bitmapOperator.findFreeClusters(
            data = serviceData,
            bitmapStartByte = bitmapInfo.startByte,
            clusterCount = fsMeta.clusterCount,
            requiredCount = additional
        )

        require(newClusters.size == additional) { "No space left on device" }

        bitmapOperator.markAllocatedClusters(
            bitmapStartByte = bitmapInfo.startByte,
            clusters = newClusters,
            clusterCount = fsMeta.clusterCount
        )

        val oldContiguous = fatOperator.buildContiguousClusters(current.firstCluster.toInt(), oldClusters)
        fatOperator.writeChain(oldContiguous + newClusters, fatStartBytes)

        fatChainCacheMutex.withLock {
            fatChainCache.remove(current.firstCluster.toInt())
        }

        return current.copy(
            streamFlags = buildStreamFlags(noFatChain = false),
            noFatChain = false
        )
    }

    private suspend fun growFatFile(
        current: NodeCoreMetadata,
        additional: Int,
        fsMeta: ExFatFIleSystemConstantMetadata,
        bitmapInfo: AllocationBitmapInfo,
        fatStartBytes: LongArray
    ): NodeCoreMetadata {
        val newClusters = bitmapOperator.findFreeClusters(
            data = serviceData,
            bitmapStartByte = bitmapInfo.startByte,
            clusterCount = fsMeta.clusterCount,
            requiredCount = additional
        )

        require(newClusters.size == additional) { "No space left on device" }

        bitmapOperator.markAllocatedClusters(
            bitmapStartByte = bitmapInfo.startByte,
            clusters = newClusters,
            clusterCount = fsMeta.clusterCount
        )

        val oldChain = fatOperator.walkChain(
            data = serviceData,
            firstCluster = current.firstCluster.toInt(),
            fatStartByte = fatStartBytes[0],
            maxSteps = DEFAULT_WALK_LIMIT
        )

        require(oldChain.isNotEmpty()) { "Broken FAT chain" }

        fatOperator.appendChain(oldChain.last(), newClusters, fatStartBytes)

        fatChainCacheMutex.withLock {
            fatChainCache.remove(current.firstCluster.toInt())
        }

        return current.copy(
            streamFlags = buildStreamFlags(noFatChain = false),
            noFatChain = false
        )
    }

    private suspend fun updateFileMetadata(state: NodeState, nextCore: NodeCoreMetadata) {
        val fsMeta = readConstantFileSystemMetadata()
        val bitmapInfo = readAllocationBitmapInfo()
        serviceDataMutex.withLock {
            bootRegionOperator.setDirty(true)
            updateStreamEntryOnDisk(serviceData, state, nextCore)
            updatePercentInUse(serviceData, bitmapInfo, fsMeta)
            state.update(nextCore, state.snapshotEntrySetLocation())
            bootRegionOperator.setDirty(false)
        }
    }

    private suspend fun updateStreamEntryOnDisk(
        data: RandomAccessData,
        state: NodeState,
        nextCore: NodeCoreMetadata
    ) {
        val location = state.snapshotEntrySetLocation()
            ?: throw IllegalStateException("Root has no stream entry")

        val streamOffset = location.streamEntryOffsetInParentBytes
            ?: throw IllegalStateException("Node has no stream entry")

        val streamBytes = readDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = streamOffset,
            length = DIR_ENTRY_SIZE
        )

        streamBytes[1] = updatedStreamFlags(
            oldFlags = nextCore.streamFlags,
            noFatChain = nextCore.noFatChain
        ).toByte()

        putU64le(streamBytes, 8, nextCore.validDataLength)
        putU32le(streamBytes, 20, nextCore.firstCluster.toInt())
        putU64le(streamBytes, 24, nextCore.dataLength)

        writeDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = streamOffset,
            bytes = streamBytes
        )

        recomputeAndWriteEntrySetChecksum(
            data = data,
            location = location
        )
    }

    private suspend fun recomputeAndWriteEntrySetChecksum(
        data: RandomAccessData,
        location: NodeEntrySetLocation
    ) {
        val primary = readDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = location.primaryEntryOffsetInParentBytes,
            length = DIR_ENTRY_SIZE
        )

        val secondaryCount = u8(primary[1])
        val totalEntries = 1 + secondaryCount
        val entrySetBytes = ByteArray(totalEntries * DIR_ENTRY_SIZE)

        System.arraycopy(primary, 0, entrySetBytes, 0, DIR_ENTRY_SIZE)

        for (i in 1 until totalEntries) {
            val offset = location.primaryEntryOffsetInParentBytes + DIR_ENTRY_SIZE.toLong() * i
            val entry = readDirectoryStreamRange(
                data = data,
                firstCluster = location.parentDirFirstCluster,
                noFatChain = location.parentDirNoFatChain,
                position = offset,
                length = DIR_ENTRY_SIZE
            )
            System.arraycopy(entry, 0, entrySetBytes, i * DIR_ENTRY_SIZE, DIR_ENTRY_SIZE)
        }

        val checksum = computeEntrySetChecksum(entrySetBytes)

        putU16le(primary, 2, checksum)

        writeDirectoryStreamRange(
            data = data,
            firstCluster = location.parentDirFirstCluster,
            noFatChain = location.parentDirNoFatChain,
            position = location.primaryEntryOffsetInParentBytes,
            bytes = primary
        )
    }

    private suspend fun readDirectoryStreamRange(
        data: RandomAccessData,
        firstCluster: Long,
        noFatChain: Boolean,
        position: Long,
        length: Int
    ): ByteArray {
        val core = NodeCoreMetadata(
            isDirectory = true,
            attributes = ATTR_DIRECTORY,
            firstCluster = firstCluster,
            dataLength = Long.MAX_VALUE,
            validDataLength = Long.MAX_VALUE,
            readableLength = Long.MAX_VALUE,
            streamFlags = if (noFatChain) STREAM_FLAG_NO_FAT_CHAIN else 0,
            noFatChain = noFatChain
        )
        val out = ByteArray(length)
        if (noFatChain) {
            readContiguousRange(data, core, readConstantFileSystemMetadata(), position, out, 0, length, readConstantFileSystemMetadata().bytesPerCluster.toInt())
        } else {
            readFatChainedRange(data, core, readConstantFileSystemMetadata(), position, out, 0, length, readConstantFileSystemMetadata().bytesPerCluster.toInt())
        }
        return out
    }

    private suspend fun writeDirectoryStreamRange(
        data: RandomAccessData,
        firstCluster: Long,
        noFatChain: Boolean,
        position: Long,
        bytes: ByteArray
    ) {
        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()
        if (noFatChain) {
            writeContiguousRange(data, firstCluster, fsMeta, position, bytes, 0, bytes.size, bytesPerCluster)
        } else {
            writeFatChainedRange(data, firstCluster, fsMeta, Long.MAX_VALUE / 4, position, bytes, 0, bytes.size, bytesPerCluster)
        }
    }

    private suspend fun updatePercentInUse(
        data: RandomAccessData,
        bitmapInfo: AllocationBitmapInfo,
        fsMeta: ExFatFIleSystemConstantMetadata
    ) {
        val percent = bitmapOperator.computePercentInUse(data, bitmapInfo.startByte, fsMeta.clusterCount)
        bootRegionOperator.setPercentInUse(percent)
    }

    private suspend fun readAllocationBitmapInfo(): AllocationBitmapInfo {
        allocationBitmapInfoCache?.let { return it }
        return allocationBitmapInfoMutex.withLock {
            allocationBitmapInfoCache?.let { return@withLock it }
            val rootState = getRootState()
            val tempData = createDataHandle()
            try {
                val rootBytes = readAllReadableBytes(rootState.snapshotCore(), tempData)
                var i = 0
                while (i + DIR_ENTRY_SIZE <= rootBytes.size) {
                    val raw = u8(rootBytes[i])
                    if (raw == 0x00) break
                    val inUse = (raw and 0x80) != 0
                    val type = raw and 0x7F
                    if (inUse && type == TYPE_ALLOCATION_BITMAP) {
                        val firstCluster = u32le(rootBytes, i + 20)
                        val dataLength = u64le(rootBytes, i + 24)
                        val fsMeta = readConstantFileSystemMetadata()
                        val startByte = fsMeta.heapStartByte + (firstCluster.toLong() - CLUSTERS_OFFSET) * fsMeta.bytesPerCluster
                        val info = AllocationBitmapInfo(firstCluster, dataLength, startByte)
                        allocationBitmapInfoCache = info
                        return@withLock info
                    }
                    i += DIR_ENTRY_SIZE
                }
                error("Allocation Bitmap entry not found in root directory")
            } finally {
                tempData.close()
            }
        }
    }

    private fun buildFatStartBytes(fsMeta: ExFatFIleSystemConstantMetadata): LongArray {
        val perFatBytes = fsMeta.fatLengthSectors.toLong() * fsMeta.bytesPerSector.toLong()
        return LongArray(fsMeta.numberOfFats) { idx -> fsMeta.fatStartByte + idx * perFatBytes }
    }

    private fun updatedStreamFlags(oldFlags: Int, noFatChain: Boolean): Int {
        return buildStreamFlags(noFatChain)
    }

    private fun buildStreamFlags(noFatChain: Boolean): Int {
        return STREAM_FLAG_ALLOCATION_POSSIBLE or
                if (noFatChain) STREAM_FLAG_NO_FAT_CHAIN else 0
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
        val chain = fatOperator.walkChain(data, firstCluster, fsMeta.fatStartByte, maxSteps)
        fatChainCacheMutex.withLock {
            fatChainCache[firstCluster] = chain
        }
        return chain
    }

    private data class UpCaseTableInfo(
        val checksum: Long,
        val firstCluster: Int,
        val dataLength: Long
    )

    companion object {
        const val MB = 1024 * 1024L

        const val MIN_EXFAT_SIZE = 2 * MB + 34 * 4096
        private const val DIR_ENTRY_SIZE = 32
        private const val TYPE_FILE_DIR_ENTRY = 0x05
        private const val TYPE_STREAM_EXT = 0x40
        private const val TYPE_FILE_NAME = 0x41
        private const val TYPE_ALLOCATION_BITMAP = 0x01
        private const val FILE_NAME_UTF16_OFFSET = 2
        private const val FILE_NAME_UTF16_BYTES = 30
        private const val FILE_NAME_CHARS_PER_ENTRY = 15
        private const val CLUSTERS_OFFSET = 2
        private const val ATTR_DIRECTORY = 0x0010
        private const val STREAM_FLAG_NO_FAT_CHAIN = 0x02
        private const val MAX_ROOT_DIR_BYTES_SAFETY = 256L * 1024L * 1024L
        private const val ZERO_CHUNK_SIZE = 64 * 1024
        private const val MAX_FILE_NAME_CHARS = 255
        private const val TYPE_UPCASE_TABLE = 0x02

        private const val STREAM_FLAG_ALLOCATION_POSSIBLE = 0x01

        private const val UTF16_CODE_UNIT_COUNT = 0x10000

        private fun clustersForSize(size: Long, bytesPerCluster: Long): Int {
            if (size <= 0L) return 0
            return ((size + bytesPerCluster - 1) / bytesPerCluster).toInt()
        }
    }
}