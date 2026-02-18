package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

data class ExFatFileSystemChangingMetadata(
    val volumeFlags: Int,
    val percentInUse: Int,
)

data class ExFatFileSystemMetadata(
    val constantMetadata: ExFatFIleSystemConstantMetadata,
    val changingMetadata: ExFatFileSystemChangingMetadata,
)

internal class MetadataOperator(private val data: RandomAccessData) {

    private val metaMutex = Mutex()
    private var constantMetadata: ExFatFIleSystemConstantMetadata? = null

    suspend fun isExFat(): Boolean {
        val boot = readAt(data, 0, BOOT_SECTOR_SIZE)
        try {
            checkExFAT(boot)
            return true
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    suspend fun readConstantFileSystemMetadata(): ExFatFIleSystemConstantMetadata {
        constantMetadata?.let { return it }

        return metaMutex.withLock {
            constantMetadata?.let { return it } // double-check
            val boot = readAt(data,0, BOOT_SECTOR_SIZE)
            val metadata = parseOrThrowConstantMetadata(boot)
            constantMetadata = metadata
            metadata
        }
    }

    suspend fun readChangingFileSystemMetadata(): ExFatFileSystemChangingMetadata {
        val boot = readAt(data,0, BOOT_SECTOR_SIZE)
        return parseOrThrowChangingMetadata(boot)
    }

    suspend fun readFullFileSystemMetadata(): ExFatFileSystemMetadata {
        val boot = readAt(data,0, BOOT_SECTOR_SIZE)
        val changingMetadata = parseOrThrowChangingMetadata(boot)
        val constantMetadata = constantMetadata?: parseOrThrowConstantMetadata(boot)
        return ExFatFileSystemMetadata(constantMetadata, changingMetadata)
    }

    private fun checkExFAT(boot: ByteArray) {
        require(boot.size >= BOOT_SECTOR_SIZE)

        // exFAT filesystem name at offset 3, length 8: "EXFAT   "
        val fsName = boot.copyOfRange(3, 11).toString(Charsets.US_ASCII)
        if (fsName != "EXFAT   ") throw IllegalArgumentException("Not exFAT (fsName=$fsName)")

        // Signature at end: 0x55AA
        val sig = u16le(boot, 510)
        if (sig != 0xAA55) throw IllegalArgumentException("Bad signature (0x${sig.toString(16)})")
    }

    private fun parseOrThrowConstantMetadata(boot: ByteArray): ExFatFIleSystemConstantMetadata {
        checkExFAT(boot)

        val fatOffset = u32le(boot, 0x50)
        val fatLength = u32le(boot, 0x54)
        val heapOffset = u32le(boot, 0x58)
        val clusterCount = u32le(boot, 0x5C)
        val rootDirFirstCluster = u32le(boot, 0x60)

        val volumeSerial = u32le(boot, 0x64).toLong() and 0xFFFF_FFFFL
        val fsRevision = u16le(boot, 0x68)
        val bpsShift = u8(boot[0x6C])
        val spcShift = u8(boot[0x6D])
        val numberOfFats = u8(boot[0x6E])
        val bytesPerSector = 1 shl bpsShift
        val sectorsPerCluster = 1 shl spcShift
        val bytesPerCluster = bytesPerSector.toLong() * sectorsPerCluster.toLong()
        val fatStartByte = fatOffset.toLong() * bytesPerSector.toLong()
        val heapStartByte = heapOffset.toLong() * bytesPerSector.toLong()
        return ExFatFIleSystemConstantMetadata(
            fatOffsetSectors = fatOffset,
            fatLengthSectors = fatLength,
            clusterHeapOffsetSectors = heapOffset,
            clusterCount = clusterCount,
            rootDirFirstCluster = rootDirFirstCluster.toLong(),
            volumeSerial = volumeSerial,
            fsRevision = fsRevision,
            bytesPerSector = bytesPerSector,
            sectorsPerCluster = sectorsPerCluster,
            numberOfFats = numberOfFats,
            bytesPerCluster = bytesPerCluster,
            fatStartByte = fatStartByte,
            heapStartByte = heapStartByte
        )
    }

    private fun parseOrThrowChangingMetadata(boot: ByteArray): ExFatFileSystemChangingMetadata {
        checkExFAT(boot)
        val volumeFlags = u16le(boot, 0x6A)
        val percentInUse = u8(boot[0x70])
        return ExFatFileSystemChangingMetadata(volumeFlags, percentInUse)
    }

    companion object {
        private const val BOOT_SECTOR_SIZE = 512
    }
}