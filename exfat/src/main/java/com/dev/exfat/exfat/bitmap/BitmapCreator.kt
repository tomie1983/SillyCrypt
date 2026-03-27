package com.dev.exfat.exfat.bitmap

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.ceilDiv
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BitmapCreator(private val data: RandomAccessData) {

    private val mutex = Mutex()

    suspend fun createEmptyBitmap(
        meta: ExFatFIleSystemConstantMetadata,
        bitmapFirstCluster: Int,
        allocatedClusters: IntArray,
    ) = mutex.withLock {
        require(bitmapFirstCluster >= CLUSTERS_OFFSET) { "Invalid bitmapFirstCluster=$bitmapFirstCluster" }

        val bitmapBytes = ceilDiv(meta.clusterCount.toLong(), 8L).toInt().coerceAtLeast(1)
        val bitmapClusterBytes = allocatedByteLengthForContiguousClusters(
            count = clustersForByteLength(bitmapBytes.toLong(), meta.bytesPerCluster),
            bytesPerCluster = meta.bytesPerCluster
        )
        val bitmap = ByteArray(bitmapClusterBytes)

        for (cluster in allocatedClusters) {
            require(cluster in CLUSTERS_OFFSET..(meta.clusterCount + 1)) {
                "Cluster out of range for bitmap marking: $cluster"
            }
            val clusterIndex = cluster - CLUSTERS_OFFSET
            bitmap[clusterIndex ushr 3] = (bitmap[clusterIndex ushr 3].toInt() or (1 shl (clusterIndex and 7))).toByte()
        }

        val bitmapStartByte = clusterToByteOffset(meta, bitmapFirstCluster)
        writeAt(data, bitmapStartByte, bitmap)
    }

    private fun clustersForByteLength(lengthBytes: Long, bytesPerCluster: Long): Int {
        return ceilDiv(lengthBytes, bytesPerCluster).toInt().coerceAtLeast(1)
    }

    private fun allocatedByteLengthForContiguousClusters(count: Int, bytesPerCluster: Long): Int {
        return (count.toLong() * bytesPerCluster).toInt()
    }

    private fun clusterToByteOffset(meta: ExFatFIleSystemConstantMetadata, cluster: Int): Long {
        return meta.heapStartByte + (cluster.toLong() - CLUSTERS_OFFSET) * meta.bytesPerCluster
    }

    companion object {
        private const val CLUSTERS_OFFSET = 2
    }
}
