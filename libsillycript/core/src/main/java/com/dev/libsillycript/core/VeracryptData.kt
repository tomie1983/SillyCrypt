package com.dev.libsillycript.core

import com.dev.libsillycript.core.fs.FsType

data class VeracryptData(
    val size: Long,
    val veracryptOpeningData: VeracryptOpeningData,
    val fsType: FsType,
    val index: Int
)