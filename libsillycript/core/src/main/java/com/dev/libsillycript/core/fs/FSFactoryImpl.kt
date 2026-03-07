package com.dev.libsillycript.core.fs

import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume

class FSFactoryImpl: FSFactory {
    override fun create(fsType: FsType, volumeFactory: RandomAccessDataFactory): String {
        return when(fsType) {
            FsType.ExFAT -> {
                EXFatVolumesManager.register(volumeFactory)
            }
        }
    }
}