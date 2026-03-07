package com.dev.libsillycript.core.fs

import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume

interface FSFactory {
    fun create(fsType: FsType, volumeFactory: RandomAccessDataFactory): String
}