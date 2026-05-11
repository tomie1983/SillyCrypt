package com.dev.exfat.exfat.upcase

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.ceilDiv
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class UpCaseTableCreator(private val data: RandomAccessData) {

    private val mutex = Mutex()

    suspend fun createDefaultTable(
        meta: ExFatFIleSystemConstantMetadata,
        firstCluster: Int,
        reservedClusterCount: Int? = null
    ): UpCaseTableInfo = mutex.withLock {
        require(firstCluster >= CLUSTERS_OFFSET) {
            "Invalid upcase first cluster: $firstCluster"
        }

        val raw = buildCompressedUpcaseTable()

        val actualClusterCount = ceilDiv(
            raw.size.toLong(),
            meta.bytesPerCluster
        ).toInt().coerceAtLeast(1)

        val clusterCount = reservedClusterCount ?: actualClusterCount

        require(clusterCount >= actualClusterCount) {
            "Reserved UpCase Table area is too small: reserved=$clusterCount actual=$actualClusterCount"
        }

        val padded = ByteArray((clusterCount.toLong() * meta.bytesPerCluster).toInt())
        System.arraycopy(raw, 0, padded, 0, raw.size)

        val startByte = meta.heapStartByte +
                (firstCluster.toLong() - CLUSTERS_OFFSET) * meta.bytesPerCluster

        writeAt(data, startByte, padded)

        UpCaseTableInfo(
            firstCluster = firstCluster,
            clusterCount = clusterCount,
            dataLength = raw.size.toLong(),
            checksum = computeChecksum(raw)
        )
    }

    private fun buildCompressedUpcaseTable(): ByteArray {
        val words = ArrayList<Int>()

        var codeUnit = 0

        while (codeUnit < UNICODE_CODE_UNIT_COUNT) {
            val upper = uppercaseCodeUnit(codeUnit)

            if (upper == codeUnit) {
                var runLength = 1

                while (
                    codeUnit + runLength < UNICODE_CODE_UNIT_COUNT &&
                    runLength < 0xFFFF &&
                    uppercaseCodeUnit(codeUnit + runLength) == codeUnit + runLength
                ) {
                    runLength++
                }

                words += COMPRESSED_IDENTITY_RUN_MARKER
                words += runLength

                codeUnit += runLength
            } else {
                require(upper != COMPRESSED_IDENTITY_RUN_MARKER) {
                    "Uppercase mapping produced reserved marker 0xFFFF for codeUnit=$codeUnit"
                }

                words += upper
                codeUnit++
            }
        }

        val out = ByteArray(words.size * 2)
        var offset = 0

        for (word in words) {
            out[offset] = (word and 0xFF).toByte()
            out[offset + 1] = ((word ushr 8) and 0xFF).toByte()
            offset += 2
        }

        return out
    }

    private fun uppercaseCodeUnit(codeUnit: Int): Int {
        return codeUnit.toChar().uppercaseChar().code
    }

    private fun computeChecksum(bytes: ByteArray): Int {
        var sum = 0

        for (b in bytes) {
            sum = (sum ushr 1) or (sum shl 31)
            sum += b.toInt() and 0xFF
        }

        return sum
    }

    internal data class UpCaseTableInfo(
        val firstCluster: Int,
        val clusterCount: Int,
        val dataLength: Long,
        val checksum: Int,
    )

    companion object {
        private const val CLUSTERS_OFFSET = 2
        private const val UNICODE_CODE_UNIT_COUNT = 65536
        private const val COMPRESSED_IDENTITY_RUN_MARKER = 0xFFFF
    }
}