package com.dev.libsillycript.core.fs

import com.dev.exfat.data.RandomAccessDataFactory

interface FSFactory {
    fun open(
        fsType: FsType, volumeFactory: RandomAccessDataFactory,
        name: String
    ): String

    suspend fun create(fsType: FsType, volumeFactory: RandomAccessDataFactory)
}