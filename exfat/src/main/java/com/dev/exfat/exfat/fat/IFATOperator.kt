package com.dev.exfat.exfat.fat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.fat.FATOperator.Companion.DEFAULT_WALK_LIMIT

interface IFATOperator {
    suspend fun writeEntry(cluster: Int, value: Int, fatStartByte: Long)
    suspend fun writeEntryAllFats(cluster: Int, value: Int, fatStartBytes: LongArray)
    suspend fun setEndOfChain(cluster: Int, fatStartBytes: LongArray)
    suspend fun clearCluster(cluster: Int, fatStartBytes: LongArray)
    fun isEndOfChain(v: Int): Boolean
    fun isFree(v: Int): Boolean
    fun isBad(v: Int): Boolean
    fun isReserved(v: Int): Boolean

    suspend fun writeChain(clusters: IntArray, fatStartBytes: LongArray)
    suspend fun appendChain(tailCluster: Int, newClusters: IntArray, fatStartBytes: LongArray)
    suspend fun detachTail(firstCluster: Int, keepClusters: Int, fatStartBytes: LongArray): Int?
    suspend fun freeChain(
        firstCluster: Int,
        fatStartBytes: LongArray,
        maxSteps: Int = DEFAULT_WALK_LIMIT
    )

    fun buildContiguousClusters(firstCluster: Int, count: Int): IntArray
    suspend fun readEntry(data: RandomAccessData, cluster: Int, fatStartByte: Long): Int
    suspend fun getNextCluster(data: RandomAccessData, cluster: Int, fatStartByte: Long): Int
    suspend fun walkChain(
        data: RandomAccessData,
        firstCluster: Int,
        fatStartByte: Long,
        maxSteps: Int
    ): IntArray
}