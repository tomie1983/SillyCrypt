package com.dev.libsillycript.core.fs

import com.dev.libsillycript.core.volumes.BaseVeracryptVolume

interface FSFactory {
    fun create(fsType: FsType, volume: BaseVeracryptVolume): String
}