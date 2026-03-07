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
import kotlin.math.min


class ExFATFS(private val dataFactory: RandomAccessDataFactory) {
    private val serviceData = dataFactory.create()

    private val bootRegionOperator = BootRegionOperator(serviceData)
    private val fatOperator = FATOperator(serviceData)

    // -------------------- caches --------------------

    private var constantMetadataCache: ExFatFIleSystemConstantMetadata? = null
    private var changingMetadataCache: ExFatFileSystemChangingMetadata? = null
    private var rootMetaCache: ExFatNodeMetadata? = null


    /** firstCluster -> full FAT chain (includes first cluster) */
    private val fatChainCache = LruCache<Int, IntArray>(512)

    // -------------------- metadata API --------------------

    suspend fun isExFat(): Boolean = bootRegionOperator.isExFat(serviceData)

    suspend fun readConstantFileSystemMetadata(): ExFatFIleSystemConstantMetadata {
        constantMetadataCache?.let { return it }
        return bootRegionOperator.readConstantFileSystemMetadata(serviceData)
            .also { constantMetadataCache = it }
    }

    suspend fun readChangingFileSystemMetadata(): ExFatFileSystemChangingMetadata {
        changingMetadataCache?.let { return it }
        return bootRegionOperator.readChangingFileSystemMetadata(serviceData)
            .also { changingMetadataCache = it }
    }

    suspend fun readFullFileSystemMetadata(): ExFatFileSystemMetadata {
        // intentionally not cached separately; it is usually cheap enough
        return bootRegionOperator.readFullFileSystemMetadata(serviceData)
    }

    // -------------------- public file API --------------------

    /**
     * Root directory as ExFATFile (RandomAccessData + metadata).
     */
    suspend fun root(): ExFATFile = ExFATFile(this, getRootMetadata())

    /**
     * Lists root directory entries as files.
     */
    suspend fun listRoot(): List<ExFATFile> = root().listFiles()

    /**
     * Returns file/dir by absolute path (e.g. "/", "/foo/bar.txt"), or null if not found.
     * Only absolute paths are supported.
     */
    suspend fun getFileFromPath(path: String): ExFATFile? {
        val normalized = normalizeAbsolutePath(path)
        if (normalized == "/") return root()


        val parts = splitAbsolutePath(normalized)
        var current = getRootMetadata()

        for (part in parts) {
            if (!current.isDirectory) return null

            val children = listDirectoryEntries(current, serviceData)
            val next = children.firstOrNull { it.name == part } ?: return null
            current = next
        }

        return ExFATFile(this, current)
    }

    suspend fun exists(path: String): Boolean = getFileFromPath(path) != null

    suspend fun close() {
        serviceData.close()
    }

    // -------------------- internal API used by handlers --------------------

    internal fun createDataHandle(): RandomAccessData = dataFactory.create()

    internal suspend fun listDirectory(
        fileMeta: ExFatNodeMetadata,
        data: RandomAccessData
    ): List<ExFATFile> {
        require(fileMeta.isDirectory) { "Not a directory: ${fileMeta.path}" }
        return listDirectoryEntries(fileMeta, data).map { ExFATFile(this, it) }
    }

    internal suspend fun readStreamRange(
        meta: ExFatNodeMetadata,
        position: Long,
        buf: ByteArray,
        data: RandomAccessData
    ): Int {
        if (buf.isEmpty()) return 0
        if (position < 0L) throw IllegalArgumentException("Negative position: $position")

        val readableSize = meta.readableLength
        if (position >= readableSize) return -1L.toInt()

        val maxToRead = min(buf.size.toLong(), readableSize - position).toInt()
        if (maxToRead <= 0) return -1

        if (maxToRead == 0) return 0

        // Empty files/dirs are handled above via readableSize.
        if (meta.firstCluster == 0L) {
            // Corrupt case: non-empty but no first cluster.
            throw IllegalStateException("Stream is non-empty but firstCluster == 0: ${meta.path}")
        }

        val fsMeta = readConstantFileSystemMetadata()
        val bytesPerCluster = fsMeta.bytesPerCluster.toInt()

        if (meta.noFatChain) {
            readContiguousRange(
                data = data,
                meta = meta,
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
            meta = meta,
            fsMeta = fsMeta,
            position = position,
            out = buf,
            outOffset = 0,
            len = maxToRead,
            bytesPerCluster = bytesPerCluster
        )
        println(buf[0])
        return maxToRead
    }

    // -------------------- directory parsing --------------------

    private suspend fun getRootMetadata(): ExFatNodeMetadata {
        rootMetaCache?.let { return it }

        val c = readConstantFileSystemMetadata()
        val rootCluster = c.rootDirFirstCluster

        // Root directory size is not stored in boot region like regular file entries.
        // We estimate readable size as full FAT chain byte length (slack may be included).
        val rootChain = resolveFatChain(serviceData, rootCluster.toInt())
        val estimatedBytes = rootChain.size.toLong() * c.bytesPerCluster
        val readable = min(estimatedBytes, MAX_ROOT_DIR_BYTES_SAFETY)

        val rootMeta = ExFatNodeMetadata(
            name = "",
            path = "/",
            isDirectory = true,
            attributes = ATTR_DIRECTORY,
            firstCluster = rootCluster,
            dataLength = readable,
            validDataLength = readable,
            readableLength = readable,
            streamFlags = 0,
            noFatChain = false
        )

        rootMetaCache = rootMeta
        return rootMeta
    }

    private suspend fun listDirectoryEntries(
        dirMeta: ExFatNodeMetadata,
        data: RandomAccessData
    ): List<ExFatNodeMetadata> {
        require(dirMeta.isDirectory) { "Not a directory: ${dirMeta.path}" }
        if (dirMeta.readableLength == 0L) return emptyList()
        val dirBytes = readAllReadableBytes(dirMeta, data)
        val parsed = parseDirectory(dirBytes, dirMeta.path)
        return parsed
    }

    private fun parseDirectory(dirBytes: ByteArray, parentPath: String): List<ExFatNodeMetadata> {
        val out = mutableListOf<ExFatNodeMetadata>()

        var i = 0
        while (i + DIR_ENTRY_SIZE <= dirBytes.size) {
            val entryTypeRaw = u8(dirBytes[i])

            // 0x00 => end of directory entries
            if (entryTypeRaw == 0x00) break

            val inUse = (entryTypeRaw and 0x80) != 0
            val entryType = entryTypeRaw and 0x7F

            if (!inUse) {
                i += DIR_ENTRY_SIZE
                continue
            }

            if (entryType == TYPE_FILE_DIR_ENTRY) {
                val secondaryCount = u8(dirBytes[i + 1])
                val fileAttributes = u16le(dirBytes, i + 4)
                val isDir = (fileAttributes and ATTR_DIRECTORY) != 0

                val secondariesStart = i + DIR_ENTRY_SIZE
                val secondariesEnd = secondariesStart + secondaryCount * DIR_ENTRY_SIZE

                if (secondariesEnd <= dirBytes.size) {
                    val (stream, name) = parseSecondariesForFile(
                        dirBytes,
                        secondariesStart,
                        secondaryCount
                    )

                    if (!name.isNullOrEmpty() && stream != null) {
                        val dataLength = stream.dataLength
                        val validDataLength = stream.validDataLength
                        val readableLength = min(validDataLength, dataLength).coerceAtLeast(0L)

                        val childPath = joinPath(parentPath, name)

                        out += ExFatNodeMetadata(
                            name = name,
                            path = childPath,
                            isDirectory = isDir,
                            attributes = fileAttributes,
                            firstCluster = stream.firstCluster,
                            dataLength = dataLength,
                            validDataLength = validDataLength,
                            readableLength = readableLength,
                            streamFlags = stream.flags,
                            noFatChain = (stream.flags and STREAM_FLAG_NO_FAT_CHAIN) != 0
                        )
                    }
                }

                i += DIR_ENTRY_SIZE * (1 + secondaryCount)
                continue
            }

            // Other entry types ignored for now (bitmap, upcase, volume label, etc.)
            i += DIR_ENTRY_SIZE
        }

        return out
    }

    private data class StreamExt(
        val nameLength: Int,
        val flags: Int,
        val validDataLength: Long,
        val firstCluster: Long,
        val dataLength: Long
    )

    private fun parseSecondariesForFile(
        bytes: ByteArray,
        start: Int,
        secondaryCount: Int
    ): Pair<StreamExt?, String?> {
        var stream: StreamExt? = null
        val nameChars = StringBuilder()
        var expectedNameLen = -1

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
                        dataLength = dataLength
                    )
                    expectedNameLen = nameLength
                }

                TYPE_FILE_NAME -> {
                    val s =
                        decodeUtf16Le(bytes, offset + FILE_NAME_UTF16_OFFSET, FILE_NAME_UTF16_BYTES)
                    nameChars.append(s)

                    if (expectedNameLen >= 0 && nameChars.length >= expectedNameLen) {
                        val finalName = nameChars.toString()
                            .take(expectedNameLen)
                            .trimEnd('\u0000')
                        return stream to finalName
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

        return stream to finalName.takeIf { it.isNotEmpty() }
    }

    // -------------------- stream reading helpers --------------------

    private suspend fun readAllReadableBytes(
        meta: ExFatNodeMetadata,
        data: RandomAccessData
    ): ByteArray {
        val len = meta.readableLength
        if (len == 0L) return ByteArray(0)
        if (len > Int.MAX_VALUE.toLong()) {
            throw IllegalStateException("Stream too large to materialize in memory: ${meta.path}, len=$len")
        }

        val out = ByteArray(len.toInt())
        var offset = 0
        var pos = 0L
        while (offset < out.size) {
            val tmp = ByteArray(out.size - offset)
            val n = readStreamRange(meta, pos, tmp, data)
            if (n <= 0) break

            System.arraycopy(tmp, 0, out, offset, n)

            offset += n
            pos += n
        }
        return if (offset == out.size) out else out.copyOf(offset)
    }

    /**
     * Reads contiguous (NoFatChain) stream data.
     */
    private suspend fun readContiguousRange(
        data: RandomAccessData,
        meta: ExFatNodeMetadata,
        fsMeta: ExFatFIleSystemConstantMetadata,
        position: Long,
        out: ByteArray,
        outOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        val firstCluster = meta.firstCluster
        if (firstCluster < CLUSTERS_OFFSET) {
            // Empty files may have firstCluster=0 but they should never reach here (len > 0).
            throw IllegalStateException("Invalid first cluster for contiguous stream: $firstCluster, path=${meta.path}")
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

    /**
     * Reads FAT-chained stream data.
     */
    private suspend fun readFatChainedRange(
        data: RandomAccessData,
        meta: ExFatNodeMetadata,
        fsMeta: ExFatFIleSystemConstantMetadata,
        position: Long,
        out: ByteArray,
        outOffset: Int,
        len: Int,
        bytesPerCluster: Int
    ) {
        val firstCluster = meta.firstCluster
        if (firstCluster < CLUSTERS_OFFSET) {
            throw IllegalStateException("Invalid first cluster for FAT stream: $firstCluster, path=${meta.path}")
        }

        val chain = resolveFatChain(data, firstCluster.toInt())
        if (chain.isEmpty()) throw IllegalStateException("Empty FAT chain for non-empty stream: ${meta.path}")

        var remaining = len
        var dst = outOffset
        var streamPos = position

        while (remaining > 0) {
            val clusterIndex = (streamPos / bytesPerCluster).toInt()
            val inClusterOffset = (streamPos % bytesPerCluster).toInt()

            if (clusterIndex >= chain.size) {
                // Corrupt metadata/FAT mismatch
                throw IllegalStateException(
                    "FAT chain shorter than expected for ${meta.path}: " +
                            "clusterIndex=$clusterIndex chainSize=${chain.size}"
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
        val bytes = readAt(data, clusterByte, bytesPerCluster)
        return bytes
    }

    private suspend fun resolveFatChain(data: RandomAccessData, firstCluster: Int): IntArray {
        fatChainCache[firstCluster]?.let { return it }

        val fsMeta = readConstantFileSystemMetadata()
        val chain = fatOperator.walkChain(
            data = data,
            firstCluster = firstCluster,
            fatStartByte = fsMeta.fatStartByte,
            maxSteps = DEFAULT_WALK_LIMIT
        )

        fatChainCache[firstCluster] = chain
        return chain
    }

    internal data class ExFatNodeMetadata(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val attributes: Int,
        val firstCluster: Long,
        val dataLength: Long,
        val validDataLength: Long,
        val readableLength: Long,   // min(validDataLength, dataLength)
        val streamFlags: Int,
        val noFatChain: Boolean
    )

    companion object {
        private const val DIR_ENTRY_SIZE = 32
        internal const val  MB = 1024 * 1024

        // entry types without "in-use" bit (0x80)
        private const val TYPE_FILE_DIR_ENTRY = 0x05 // 0x85 on disk
        private const val TYPE_STREAM_EXT = 0x40     // 0xC0 on disk
        private const val TYPE_FILE_NAME = 0x41      // 0xC1 on disk

        private const val FILE_NAME_UTF16_OFFSET = 2
        private const val FILE_NAME_UTF16_BYTES = 30

        private const val CLUSTERS_OFFSET = 2

        // attributes
        private const val ATTR_DIRECTORY = 0x0010

        // Stream Extension GeneralSecondaryFlags bits
        private const val STREAM_FLAG_NO_FAT_CHAIN = 0x02

        private const val DEFAULT_WALK_LIMIT = 1_000_000
        private const val MAX_ROOT_DIR_BYTES_SAFETY = 256L * 1024L * 1024L
    }
}
