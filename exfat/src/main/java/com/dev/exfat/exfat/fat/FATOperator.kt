package com.dev.exfat.exfat.fat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.putU32le
import com.dev.exfat.exfat.readAt
import com.dev.exfat.exfat.u32le
import com.dev.exfat.exfat.writeAt
import kotlin.math.min

internal class FATOperator(private val writeableData: RandomAccessData): IFATOperator {

    /**
     * Read raw FAT entry value for [cluster].
     */
    override suspend fun readEntry(data: RandomAccessData, cluster: Int, fatStartByte: Long): Int {
        val pos = fatStartByte + cluster.toLong() * FAT_ENTRY_LENGTH
        val b = readAt(data, pos, FAT_ENTRY_LENGTH.toInt())
        return u32le(b, 0)
    }

    /**
     * Write raw FAT entry value for [cluster]. If you have 2 FATs, call this for each FAT start.
     */
    override suspend fun writeEntry(cluster: Int, value: Int, fatStartByte: Long) {
        val pos = fatStartByte + cluster.toLong() * FAT_ENTRY_LENGTH
        val buf = ByteArray(FAT_ENTRY_LENGTH.toInt())
        putU32le(buf, 0, value)
        writeAt(writeableData, pos, buf)
    }

    /**
     * Write raw FAT entry value to all FAT copies (1 or 2).
     */
    override suspend fun writeEntryAllFats(cluster: Int, value: Int, fatStartBytes: LongArray) {
        assert(fatStartBytes.size in 1..2)
        for (start in fatStartBytes) {
            writeEntry(cluster, value, start)
        }
    }

    /**
     * Convenience: FAT[cluster] == EOC
     */
    override suspend fun setEndOfChain(cluster: Int, fatStartBytes: LongArray) {
        writeEntryAllFats(cluster, END_OF_CHAIN.toInt(), fatStartBytes)
    }

    /**
     * Convenience: FAT[cluster] == 0 (free)
     *
     * WARNING: This only clears FAT. You still must clear corresponding bitmap bits elsewhere.
     */
    override suspend fun clearCluster(cluster: Int, fatStartBytes: LongArray) {
        writeEntryAllFats(cluster, 0, fatStartBytes)
    }

    /**
     * Returns next cluster in chain (raw FAT entry).
     * Kept for compatibility with your original name.
     */
    override suspend fun getNextCluster(data: RandomAccessData, cluster: Int, fatStartByte: Long): Int = readEntry(data, cluster, fatStartByte)

    override fun isEndOfChain(v: Int): Boolean = v.toUInt() >= END_OF_CHAIN
    override fun isFree(v: Int): Boolean = v.toUInt() == 0u
    override fun isBad(v: Int): Boolean = v.toUInt() == BAD_CLUSTER
    override fun isReserved(v: Int): Boolean = v.toUInt() in RESERVED_MIN..RESERVED_MAX

    /**
     * Walks a chain starting from [firstCluster] and returns all cluster numbers in order.
     * Stops on:
     * - EOC
     * - FREE (broken chain)
     * - BAD
     * - RESERVED
     * - cycle
     * - maxSteps reached (safety)
     *
     * This is useful for delete/truncate and for converting contiguous->FAT chain.
     */
    override suspend fun walkChain(
        data: RandomAccessData,
        firstCluster: Int,
        fatStartByte: Long,
        maxSteps: Int
    ): IntArray {
        if (firstCluster < 2) return IntArray(0)

        val out = ArrayList<Int>(min(256, maxSteps))
        val seen = HashSet<Int>(min(256, maxSteps))

        var c = firstCluster
        repeat(maxSteps) {
            if (!seen.add(c)) return out.toIntArray() // cycle
            out.add(c)

            val next = readEntry(data, c, fatStartByte)
            when {
                isEndOfChain(next) -> return out.toIntArray()
                isFree(next) -> return out.toIntArray() // broken chain
                isBad(next) -> return out.toIntArray()
                isReserved(next) -> return out.toIntArray()
                else -> c = next
            }
        }
        return out.toIntArray()
    }

    /**
     * Writes a full chain:
     * clusters[0] -> clusters[1] -> ... -> clusters[last] -> EOC
     *
     * Use this when:
     * - creating a fragmented file
     * - converting a contiguous file into a FAT chain (list the contiguous clusters + newly allocated ones)
     */
    override suspend fun writeChain(clusters: IntArray, fatStartBytes: LongArray) {
        require(clusters.isNotEmpty()) { "clusters is empty" }
        require(fatStartBytes.size in 1..2)
        for (i in 0 until clusters.size - 1) {
            writeEntryAllFats(clusters[i], clusters[i + 1], fatStartBytes)
        }
        writeEntryAllFats(clusters.last(), END_OF_CHAIN.toInt(), fatStartBytes)
    }

    /**
     * Appends [newClusters] to an existing chain that currently ends at [tailCluster].
     *
     * Does:
     * - FAT[tail] = newClusters[0]
     * - link newClusters[i] -> newClusters[i+1]
     * - FAT[lastNew] = EOC
     */
    override suspend fun appendChain(tailCluster: Int, newClusters: IntArray, fatStartBytes: LongArray) {
        require(newClusters.isNotEmpty()) { "newClusters is empty" }
        require(fatStartBytes.size in 1..2)
        writeEntryAllFats(tailCluster, newClusters[0], fatStartBytes)
        for (i in 0 until newClusters.size - 1) {
            writeEntryAllFats(newClusters[i], newClusters[i + 1], fatStartBytes)
        }
        writeEntryAllFats(newClusters.last(), END_OF_CHAIN.toInt(), fatStartBytes)
    }

    /**
     * Detaches the tail of a chain, keeping the first [keepClusters] clusters.
     *
     * Returns:
     * - null if nothing was detached
     * - the first cluster of the detached tail otherwise
     *
     * After this call:
     * - the kept part ends with EOC
     * - the detached tail still has FAT links intact; call [freeChain] on it if you want to clear FAT.
     */
    override suspend fun detachTail(firstCluster: Int, keepClusters: Int, fatStartBytes: LongArray): Int? {
        require(keepClusters >= 0)
        if (firstCluster < 2) return null

        if (keepClusters == 0) return firstCluster

        // Walk just enough to find (keepClusters-1)th and its next
        var c = firstCluster
        for (i in 0 until keepClusters - 1) {
            val next = readEntry(writeableData, c, fatStartBytes[0])
            if (isEndOfChain(next) || isFree(next) || isBad(next) || isReserved(next)) return null
            c = next
        }

        val detachedFirst = readEntry(writeableData, c, fatStartBytes[0])
        if (isEndOfChain(detachedFirst) || isFree(detachedFirst) || isBad(detachedFirst) || isReserved(detachedFirst)) {
            return null
        }

        // Cut chain: FAT[c] = EOC
        writeEntryAllFats(c, END_OF_CHAIN.toInt(), fatStartBytes)
        return detachedFirst
    }

    /**
     * Clears FAT entries for the entire chain starting at [firstCluster] (sets each FAT[cluster]=0).
     *
     * WARNING:
     * - This does NOT update Allocation Bitmap.
     * - This does not zero file data in heap.
     */
    override suspend fun freeChain(firstCluster: Int, fatStartBytes: LongArray, maxSteps: Int) {
        if (firstCluster < 2) return
        val chain = walkChain(writeableData, firstCluster, fatStartBytes[0], maxSteps)
        for (c in chain) {
            writeEntryAllFats(c, 0, fatStartBytes)
        }
    }

    /**
     * Utility for building a contiguous-cluster list (for converting to FAT chain).
     * Example: first=5, count=3 -> [5,6,7]
     */
    override fun buildContiguousClusters(firstCluster: Int, count: Int): IntArray {
        require(firstCluster >= 2)
        require(count >= 0)
        return IntArray(count) { idx -> firstCluster + idx }
    }

    companion object {
        private const val FAT_ENTRY_LENGTH = 4L

        // exFAT markers
        private const val BAD_CLUSTER = 0xFFFFFFF7u
        private const val END_OF_CHAIN = 0xFFFFFFF8u
        private const val RESERVED_MIN = 0xFFFFFFF0u
        private const val RESERVED_MAX = 0xFFFFFFF6u

        // safety: max number of steps when walking a chain (can be set to clusterCount+2 by caller)
        const val DEFAULT_WALK_LIMIT = 1_000_000
    }
}