package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData

internal class FATOperator(private val data: RandomAccessData) {
    suspend fun getNextCluster(cluster: Int, fatStartByte: Long): Int {
        val pos = fatStartByte + cluster.toLong() * FAT_ENTRY_LENGTH
        val b = readAt(data, pos, FAT_ENTRY_LENGTH.toInt())
        return u32le(b, 0)
    }

    fun isEndOfChain(v: Int): Boolean = v >= END_OF_CHAIN.toInt()

    companion object {
        private const val FAT_ENTRY_LENGTH = 4L
        private const val END_OF_CHAIN = 0xFFFFFFF8
    }
}