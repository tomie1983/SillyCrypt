package com.dev.exfat.data

interface RandomAccessDataFactory {
    fun create(): RandomAccessData
    suspend fun close()
}