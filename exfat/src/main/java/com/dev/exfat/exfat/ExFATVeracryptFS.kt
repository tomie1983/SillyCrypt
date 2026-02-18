package com.dev.exfat.exfat


import com.dev.exfat.data.RandomAccessData


class ExFATVeracryptFS(private val data: RandomAccessData) {

    private val metadataOperator = MetadataOperator(data)
    private val fatOperator = FATOperator(data)

    // Metadata methods

    suspend fun isExFat(): Boolean {
        return metadataOperator.isExFat()
    }

    suspend fun readConstantFileSystemMetadata(): ExFatFIleSystemConstantMetadata {
        return metadataOperator.readConstantFileSystemMetadata()
    }

    suspend fun readChangingFileSystemMetadata(): ExFatFileSystemChangingMetadata {
        return metadataOperator.readChangingFileSystemMetadata()
    }

    suspend fun readFullFileSystemMetadata(): ExFatFileSystemMetadata {
        return metadataOperator.readFullFileSystemMetadata()
    }

    /**
     * Returns file/dir metadata from the root directory.
     * This is a minimal parser: it reads primary file entries (0x85) and their secondary entries
     * (stream extension 0xC0 + filename entries 0xC1).
     */
    suspend fun listRoot(): List<ExFatNodeMetadata> {
        val meta = readConstantFileSystemMetadata()
        // Read root directory as a cluster chain and parse directory entries from it.
        val rootBytes = readFully(firstCluster = meta.rootDirFirstCluster)
        return parseDirectory(rootBytes)
    }

    // ---- Parsing directory ----

    private fun parseDirectory(dirBytes: ByteArray): List<ExFatNodeMetadata> {
        val out = mutableListOf<ExFatNodeMetadata>()

        var i = 0
        while (i + DIR_ENTRY_SIZE <= dirBytes.size) {
            val entryTypeRaw = u8(dirBytes[i])

            // 0x00 => end of directory
            if (entryTypeRaw == 0x00) break

            val inUse = (entryTypeRaw and 0x80) != 0
            val entryType = entryTypeRaw and 0x7F

            if (!inUse) {
                i += DIR_ENTRY_SIZE
                continue
            }

            // File directory entry: 0x85 => type 0x05 with in-use bit set.
            if (entryType == TYPE_FILE_DIR_ENTRY) {
                val secondaryCount = u8(dirBytes[i + 1])

                val fileAttributes = u16le(dirBytes, i + 4)
                val isDir = (fileAttributes and ATTR_DIRECTORY) != 0

                // Collect secondary entries right after this 0x85 entry
                val secondariesStart = i + DIR_ENTRY_SIZE
                val secondariesEnd = secondariesStart + secondaryCount * DIR_ENTRY_SIZE

                if (secondariesEnd <= dirBytes.size) {
                    val (stream, name) = parseSecondariesForFile(dirBytes, secondariesStart, secondaryCount)

                    out += ExFatNodeMetadata(
                        name = name,
                        isDirectory = isDir,
                        attributes = fileAttributes,
                        firstCluster = stream?.firstCluster,
                        dataLength = stream?.dataLength,
                        validDataLength = stream?.validDataLength
                    )
                }

                i += DIR_ENTRY_SIZE * (1 + secondaryCount)
                continue
            }

            // Other entry types (allocation bitmap, upcase, volume label, etc.) are ignored for now.
            i += DIR_ENTRY_SIZE
        }

        return out
    }


    data class ExFatNodeMetadata(
        val name: String,
        val isDirectory: Boolean,
        val attributes: Int,
        val firstCluster: Long?,
        val dataLength: Long?,
        val validDataLength: Long?
    )

    private data class StreamExt(
        val nameLength: Int,
        val flags: Int,
        val validDataLength: Long,
        val firstCluster: Long,
        val dataLength: Long
    )

    private fun parseSecondariesForFile(bytes: ByteArray, start: Int, secondaryCount: Int): Pair<StreamExt?, String> {
        var stream: StreamExt? = null
        val nameChars = StringBuilder()
        var expectedNameLen = -1

        var offset = start
        for (k in 0 until secondaryCount) {
            val etRaw = u8(bytes[offset])
            val inUse = (etRaw and 0x80) != 0
            val et = etRaw and 0x7F

            if (!inUse) {
                offset += DIR_ENTRY_SIZE
                continue
            }

            when (et) {
                TYPE_STREAM_EXT -> {
                    // Layout (exFAT spec):
                    // 0: type (0xC0)
                    // 1: generalSecondaryFlags (we treat it as flags)
                    // 3: nameLength
                    // 8..15: validDataLength (LE u64)
                    // 20..23: firstCluster (LE u32)
                    // 24..31: dataLength (LE u64)
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
                    // File Name entry:
                    // UTF-16LE chars at offset 2..31 (15 chars, 30 bytes)
                    val s = decodeUtf16le(bytes, offset + CLUSTERS_OFFSET, 30)
                    nameChars.append(s)

                    // stop early if we already reached the expected name length
                    if (expectedNameLen >= 0 && nameChars.length >= expectedNameLen) {
                        // trim any padding nulls
                        val trimmed = nameChars.toString().take(expectedNameLen).trimEnd('\u0000')
                        return stream to trimmed
                    }
                }
            }

            offset += DIR_ENTRY_SIZE
        }

        val finalName =
            if (expectedNameLen >= 0) nameChars.toString().take(expectedNameLen).trimEnd('\u0000')
            else nameChars.toString().trimEnd('\u0000')

        return stream to finalName
    }

    // ---- Reading cluster chain via FAT ----

    internal suspend fun readFully(
        firstCluster: Long
    ): ByteArray {
        val metadata = metadataOperator.readConstantFileSystemMetadata()
        return readFully(
            firstCluster,
            metadata.bytesPerCluster,
            metadata.fatStartByte,
            metadata.heapStartByte)
    }

    private suspend fun readFully(
        firstCluster: Long,
        bytesPerCluster: Long,
        fatStartByte: Long,
        heapStartByte: Long,
        maxBytesSafety: Long = 256L * KB * KB // 256 MiB guard for root dir reads
    ): ByteArray {
        if (firstCluster < CLUSTERS_OFFSET) throw IllegalStateException("Wrong cluster: $firstCluster")

        val chunks = ArrayList<ByteArray>()
        var total = 0L
        var cluster = firstCluster.toInt()

        while (cluster >= CLUSTERS_OFFSET) {
            val clusterByte = heapStartByte + (cluster.toLong() - CLUSTERS_OFFSET) * bytesPerCluster
            val chunk = readAt(data, clusterByte, bytesPerCluster.toInt())
            chunks += chunk
            total += chunk.size.toLong()

            if (total > maxBytesSafety) break

            val next = fatOperator.getNextCluster(cluster, fatStartByte)
            if (fatOperator.isEndOfChain(next)) break
            if (next == 0) break // free cluster => broken chain
            cluster = next
        }

        return concatChunks(chunks, total.toInt())
    }

    suspend fun close() {
        data.close()
    }

    companion object {
        private const val DIR_ENTRY_SIZE = 32

        // entry types without "in-use" bit (0x80)
        private const val TYPE_FILE_DIR_ENTRY = 0x05 // 0x85 on disk
        private const val TYPE_STREAM_EXT = 0x40      // 0xC0 on disk
        private const val TYPE_FILE_NAME = 0x41       // 0xC1 on disk
        private const val KB = 1024

        private const val CLUSTERS_OFFSET = 2

        // attributes
        private const val ATTR_DIRECTORY = 0x0010
    }
}
