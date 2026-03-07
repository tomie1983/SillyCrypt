package com.dev.exfat.exfat.bootregion

data class ExFatFileSystemMetadata(
    val constantMetadata: ExFatFIleSystemConstantMetadata,
    val changingMetadata: ExFatFileSystemChangingMetadata,
)
