package com.dev.exfat.exfat.bitmap

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.readAt
import com.dev.exfat.exfat.writeAt
import kotlin.math.max
import kotlin.math.min

internal class BitmapOperator(private val writeableData: RandomAccessData) {

    /**
     * Contiguous free run in Allocation Bitmap.
     */
    data class ClusterRun(
        val firstCluster: Int,
        val length: Int
    )

    /**
     * Returns true if [cluster] is marked allocated in Allocation Bitmap.
     *
     * exFAT bitmap layout:
     * - cluster #2 corresponds to bit 0 of byte 0
     * - cluster #3 corresponds to bit 1 of byte 0
     * - ...
     */
    suspend fun isAllocated(
        data: RandomAccessData,
        bitmapStartByte: Long,
        cluster: Int,
        clusterCount: Int
    ): Boolean {
        requireValidCluster(cluster, clusterCount)

        val (bytePos, bitMask) = locateBit(bitmapStartByte, cluster)
        val b = readAt(data, bytePos, 1)[0].toInt() and 0xFF // беззнаковый байт
        return (b and bitMask) != 0
    }

    /**
     * Marks [cluster] allocated (bit = 1).
     */
    suspend fun setAllocated(
        bitmapStartByte: Long,
        cluster: Int,
        clusterCount: Int
    ) {
        requireValidCluster(cluster, clusterCount)
        setBit(bitmapStartByte, cluster, set = true)
    }

    /**
     * Marks [cluster] free (bit = 0).
     */
    suspend fun setFree(
        bitmapStartByte: Long,
        cluster: Int,
        clusterCount: Int
    ) {
        requireValidCluster(cluster, clusterCount)
        setBit(bitmapStartByte, cluster, set = false)
    }

    /**
     * Marks [count] consecutive clusters starting from [firstCluster] allocated.
     */
    suspend fun markAllocatedRange(
        bitmapStartByte: Long,
        firstCluster: Int,
        count: Int,
        clusterCount: Int
    ) {
        markRange(bitmapStartByte, firstCluster, count, clusterCount, allocated = true)
    }

    /**
     * Marks [count] consecutive clusters starting from [firstCluster] free.
     */
    suspend fun markFreeRange(
        bitmapStartByte: Long,
        firstCluster: Int,
        count: Int,
        clusterCount: Int
    ) {
        markRange(bitmapStartByte, firstCluster, count, clusterCount, allocated = false)
    }

    /**
     * Marks exact [clusters] allocated.
     */
    suspend fun markAllocatedClusters(
        bitmapStartByte: Long,
        clusters: IntArray,
        clusterCount: Int
    ) {
        for (cluster in clusters) {
            setAllocated(bitmapStartByte, cluster, clusterCount)
        }
    }

    /**
     * Marks exact [clusters] free.
     */
    suspend fun markFreeClusters(
        bitmapStartByte: Long,
        clusters: IntArray,
        clusterCount: Int
    ) {
        for (cluster in clusters) {
            setFree(bitmapStartByte, cluster, clusterCount)
        }
    }

    /**
     * Finds first contiguous free extent of at least [requiredLength] clusters.
     *
     * Search begins at [hintCluster], then wraps once to cluster 2.
     * Returns null if no suitable extent exists.
     */
    suspend fun findContiguousFreeRun(
        data: RandomAccessData,
        bitmapStartByte: Long,
        clusterCount: Int,
        requiredLength: Int,
        hintCluster: Int = CLUSTERS_OFFSET
    ): ClusterRun? {
        require(requiredLength > 0) { "requiredLength must be > 0" }
        require(clusterCount >= 0) { "clusterCount must be >= 0" }

        if (clusterCount == 0 || requiredLength > clusterCount) return null

        val start = normalizeHint(hintCluster, clusterCount)
        val endCluster = CLUSTERS_OFFSET + clusterCount - 1

        findContiguousFreeRunInWindow(
            data = data,
            bitmapStartByte = bitmapStartByte,
            requiredLength = requiredLength,
            fromCluster = start,
            toCluster = endCluster
        )?.let { return it }

        if (start > CLUSTERS_OFFSET) {
            findContiguousFreeRunInWindow(
                data = data,
                bitmapStartByte = bitmapStartByte,
                requiredLength = requiredLength,
                fromCluster = CLUSTERS_OFFSET,
                toCluster = start - 1
            )?.let { return it }
        }

        return null
    }

    /**
     * Finds up to [requiredCount] free clusters.
     *
     * Preference:
     * 1) search from [hintCluster] to end
     * 2) wrap to cluster 2 and continue
     *
     * Returned clusters are sorted in scan order.
     */
    suspend fun findFreeClusters(
        data: RandomAccessData,
        bitmapStartByte: Long,
        clusterCount: Int,
        requiredCount: Int,
        hintCluster: Int = CLUSTERS_OFFSET
    ): IntArray {
        require(requiredCount >= 0) { "requiredCount must be >= 0" }
        require(clusterCount >= 0) { "clusterCount must be >= 0" }

        if (requiredCount == 0 || clusterCount == 0) return IntArray(0)

        val out = ArrayList<Int>(requiredCount)
        val start = normalizeHint(hintCluster, clusterCount)
        val endCluster = CLUSTERS_OFFSET + clusterCount - 1

        collectFreeClustersInWindow(
            data = data,
            bitmapStartByte = bitmapStartByte,
            fromCluster = start,
            toCluster = endCluster,
            limit = requiredCount,
            out = out
        )

        if (out.size < requiredCount && start > CLUSTERS_OFFSET) {
            collectFreeClustersInWindow(
                data = data,
                bitmapStartByte = bitmapStartByte,
                fromCluster = CLUSTERS_OFFSET,
                toCluster = start - 1,
                limit = requiredCount,
                out = out
            )
        }

        return out.toIntArray()
    }

    /**
     * Counts allocated clusters exactly from Allocation Bitmap.
     *
     * Useful for precise percentInUse recomputation after alloc/free.
     */
    suspend fun countAllocatedClusters(
        data: RandomAccessData,
        bitmapStartByte: Long,
        clusterCount: Int
    ): Int {
        require(clusterCount >= 0) { "clusterCount must be >= 0" }
        if (clusterCount == 0) return 0

        val bitmapBytes = bitmapByteLength(clusterCount)
        val bytes = readAt(data, bitmapStartByte, bitmapBytes)

        var remainingBits = clusterCount
        var allocated = 0

        for (byte in bytes) {
            if (remainingBits <= 0) break

            val value = byte.toInt() and 0xFF  // беззнаковый байт
            val validBitsInByte = min(8, remainingBits)
            allocated += countSetBits(value and validBitMask(validBitsInByte))
            remainingBits -= validBitsInByte
        }

        return allocated
    }

    /**
     * Counts free clusters exactly from Allocation Bitmap.
     */
    suspend fun countFreeClusters(
        data: RandomAccessData,
        bitmapStartByte: Long,
        clusterCount: Int
    ): Int {
        return clusterCount - countAllocatedClusters(data, bitmapStartByte, clusterCount)
    }

    /**
     * Returns exact percentInUse in exFAT boot-sector semantics (0..100).
     */
    suspend fun computePercentInUse(
        data: RandomAccessData,
        bitmapStartByte: Long,
        clusterCount: Int
    ): Int {
        require(clusterCount >= 0) { "clusterCount must be >= 0" }
        if (clusterCount == 0) return 0

        val allocated = countAllocatedClusters(data, bitmapStartByte, clusterCount)
        return ((allocated * 100L) / clusterCount.toLong()).toInt()
    }

    private suspend fun markRange(
        bitmapStartByte: Long,
        firstCluster: Int,
        count: Int,
        clusterCount: Int,
        allocated: Boolean
    ) {
        require(count >= 0) { "count must be >= 0" }
        if (count == 0) return

        requireValidCluster(firstCluster, clusterCount)
        requireValidCluster(firstCluster + count - 1, clusterCount)

        var currentCluster = firstCluster
        var remaining = count

        while (remaining > 0) {
            val clusterIndex = currentCluster - CLUSTERS_OFFSET
            val byteIndex = clusterIndex ushr 3
            val bitIndex = clusterIndex and 7 // %8
            val bitsLeftInByte = 8 - bitIndex
            val takeBits = min(remaining, bitsLeftInByte)

            val bytePos = bitmapStartByte + byteIndex.toLong()
            val oldValue = readAt(writeableData, bytePos, 1)[0].toInt() and 0xFF // беззнаковый байт
            val mask = (((1 shl takeBits) - 1) shl bitIndex) and 0xFF // беззнаковый байт
            val newValue = if (allocated) {
                oldValue or mask
            } else {
                oldValue and mask.inv()
            }

            if (newValue != oldValue) {
                writeAt(writeableData, bytePos, byteArrayOf(newValue.toByte()))
            }

            currentCluster += takeBits
            remaining -= takeBits
        }
    }

    private suspend fun setBit(
        bitmapStartByte: Long,
        cluster: Int,
        set: Boolean
    ) {
        val (bytePos, bitMask) = locateBit(bitmapStartByte, cluster)
        val oldValue = readAt(writeableData, bytePos, 1)[0].toInt() and 0xFF // беззнаковый байт
        val newValue = if (set) {
            oldValue or bitMask // установка в 1 только нужного бита
        } else {
            oldValue and bitMask.inv() // установка в 0 только нужного бита
        }

        if (newValue != oldValue) {
            writeAt(writeableData, bytePos, byteArrayOf(newValue.toByte()))
        }
    }

    private suspend fun findContiguousFreeRunInWindow(
        data: RandomAccessData,
        bitmapStartByte: Long,
        requiredLength: Int,
        fromCluster: Int,
        toCluster: Int
    ): ClusterRun? {
        if (fromCluster > toCluster) return null

        var runStart = -1
        var runLength = 0

        forEachClusterBit(
            data = data,
            bitmapStartByte = bitmapStartByte,
            fromCluster = fromCluster,
            toCluster = toCluster
        ) { cluster, allocated ->
            if (!allocated) {
                if (runLength == 0) runStart = cluster
                runLength++
                if (runLength >= requiredLength) {
                    return ClusterRun(runStart, runLength)
                }
            } else {
                runStart = -1
                runLength = 0
            }
        }

        return null
    }

    private suspend fun collectFreeClustersInWindow(
        data: RandomAccessData,
        bitmapStartByte: Long,
        fromCluster: Int,
        toCluster: Int,
        limit: Int,
        out: MutableList<Int>
    ) {
        if (fromCluster > toCluster || out.size >= limit) return

        forEachClusterBit(
            data = data,
            bitmapStartByte = bitmapStartByte,
            fromCluster = fromCluster,
            toCluster = toCluster
        ) { cluster, allocated ->
            if (!allocated) {
                out += cluster
                if (out.size >= limit) return
            }
        }
    }

    /**
     * Iterates cluster allocation bits in [fromCluster toCluster], inclusive.
     *
     * The callback may use non-local return from the caller because this function is inline.
     */
    private suspend inline fun forEachClusterBit(
        data: RandomAccessData,
        bitmapStartByte: Long,
        fromCluster: Int,
        toCluster: Int,
        block: (cluster: Int, allocated: Boolean) -> Unit
    ) {
        if (fromCluster > toCluster) return

        val fromIndex = fromCluster - CLUSTERS_OFFSET
        val toIndex = toCluster - CLUSTERS_OFFSET

        val startByteIndex = fromIndex ushr 3
        val endByteIndex = toIndex ushr 3
        val byteCount = endByteIndex - startByteIndex + 1

        val bytes = readAt(data, bitmapStartByte + startByteIndex.toLong(), byteCount)

        for (localByteIndex in 0 until byteCount) {
            val globalByteIndex = startByteIndex + localByteIndex
            val value = bytes[localByteIndex].toInt() and 0xFF // беззнаковый байт

            val firstBit = if (globalByteIndex == startByteIndex) fromIndex and 7 else 0 // %8
            val lastBit = if (globalByteIndex == endByteIndex) toIndex and 7 else 7 // %8

            for (bit in firstBit..lastBit) {
                val clusterIndex = (globalByteIndex shl 3) + bit
                val cluster = clusterIndex + CLUSTERS_OFFSET
                val allocated = (value and (1 shl bit)) != 0
                block(cluster, allocated)
            }
        }
    }

    private fun locateBit(bitmapStartByte: Long, cluster: Int): Pair<Long, Int> {
        val clusterIndex = cluster - CLUSTERS_OFFSET
        val bytePos = bitmapStartByte + (clusterIndex ushr 3).toLong()
        val bitMask = 1 shl (
                clusterIndex and 7 // %8
        ) // маска, где только нужный бит равен 1
        return bytePos to bitMask
    }

    private fun bitmapByteLength(clusterCount: Int): Int {
        return (clusterCount + 7) ushr 3
    }

    private fun normalizeHint(hintCluster: Int, clusterCount: Int): Int {
        if (clusterCount == 0) return CLUSTERS_OFFSET
        val minCluster = CLUSTERS_OFFSET
        val maxCluster = CLUSTERS_OFFSET + clusterCount - 1
        return max(minCluster, min(hintCluster, maxCluster))
    }

    private fun requireValidCluster(cluster: Int, clusterCount: Int) {
        val minCluster = CLUSTERS_OFFSET
        val maxCluster = CLUSTERS_OFFSET + clusterCount - 1
        require(clusterCount > 0) { "clusterCount must be > 0" }
        require(cluster in minCluster..maxCluster) {
            "Cluster out of range: $cluster, valid=[$minCluster..$maxCluster]"
        }
    }

    private fun countSetBits(v: Int): Int = Integer.bitCount(v)

    private fun validBitMask(bits: Int): Int {
        require(bits in 0..8)
        return if (bits == 8) 0xFF else (1 shl bits) - 1
    }

    companion object {
        private const val CLUSTERS_OFFSET = 2
    }
}