package com.dev.exfat.exfat.bootregion

data class ExFatFIleSystemConstantMetadata(
    val bytesPerSector: Int,
    val sectorsPerCluster: Int,
    val bytesPerCluster: Long,
    val fatOffsetSectors: Int,
    val fatLengthSectors: Int,
    val clusterHeapOffsetSectors: Int,
    val clusterCount: Int,
    val rootDirFirstCluster: Long,
    val numberOfFats: Int,
    val volumeSerial: Long,
    val fsRevision: Int,
    val fatStartByte: Long,
    val heapStartByte: Long
)