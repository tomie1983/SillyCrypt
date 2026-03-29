package com.dev.libsillycript.core.fs

import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.exfat.ExFATCreator
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume

class FSFactoryImpl: FSFactory {
    override suspend fun create(
        fsType: FsType,
        volumeFactory: RandomAccessDataFactory
    ) {
        when(fsType) {
            FsType.ExFAT -> {
                ExFATCreator(volumeFactory).create()
            }
        }
    }

    override fun open(
        fsType: FsType,
        volumeFactory: RandomAccessDataFactory,
        name: String): String {
        return when(fsType) {
            FsType.ExFAT -> {
                EXFatVolumesManager.register(volumeFactory, name)
            }
        }
    }
}