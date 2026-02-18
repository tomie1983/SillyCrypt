package com.dev.libsillycript.core.fs

import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume

class FSFactoryImpl: FSFactory {
    override fun create(fsType: FsType, volume: BaseVeracryptVolume): String {
        return when(fsType) {
            FsType.ExFAT -> {
                EXFatVolumesManager.register(volume)
            }
        }
    }
}