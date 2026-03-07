package com.dev.exfat.exfat.bootregion

data class ExFatFileSystemChangingMetadata(
    val volumeFlags: Int,
    val percentInUse: Int,
)