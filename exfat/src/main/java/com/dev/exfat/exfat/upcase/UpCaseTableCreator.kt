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
    ): UpCaseTableInfo = mutex.withLock {
        require(firstCluster >= CLUSTERS_OFFSET) { "Invalid upcase first cluster: $firstCluster" }

        val raw = buildRawUpcaseTable()
        val clusterCount = ceilDiv(raw.size.toLong(), meta.bytesPerCluster).toInt().coerceAtLeast(1)
        val padded = ByteArray((clusterCount.toLong() * meta.bytesPerCluster).toInt())
        System.arraycopy(raw, 0, padded, 0, raw.size)

        val startByte = meta.heapStartByte + (firstCluster.toLong() - CLUSTERS_OFFSET) * meta.bytesPerCluster
        writeAt(data, startByte, padded)

        UpCaseTableInfo(
            firstCluster = firstCluster,
            clusterCount = clusterCount,
            dataLength = raw.size.toLong(),
            checksum = computeChecksum(raw)
        )
    }

    private fun buildRawUpcaseTable(): ByteArray {
        val out = ByteArray(UNICODE_CODEPOINT_COUNT * 2)
        var off = 0
        for (codeUnit in 0 until UNICODE_CODEPOINT_COUNT) {
            val upper = codeUnit.toChar().uppercaseChar().code
            out[off] = (upper and 0xFF).toByte()
            out[off + 1] = ((upper ushr 8) and 0xFF).toByte()
            off += 2
        }
        return out
    }

    private fun computeChecksum(bytes: ByteArray): Int {
        var sum = 0
        for (b in bytes) {
            sum = (sum ushr 1) or (sum shl 31)
            sum += (b.toInt() and 0xFF)
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
        private const val UNICODE_CODEPOINT_COUNT = 65536
    }
}
