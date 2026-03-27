package com.dev.libsillycript.core.fs

import com.dev.exfat.data.RandomAccessDataFactory

interface FSFactory {
    fun open(fsType: FsType, volumeFactory: RandomAccessDataFactory): String

    suspend fun create(fsType: FsType, volumeFactory: RandomAccessDataFactory)
}