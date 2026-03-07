package com.dev.exfat.exfat.bootregion

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.putU16le
import com.dev.exfat.exfat.readAt
import com.dev.exfat.exfat.u16le
import com.dev.exfat.exfat.u32le
import com.dev.exfat.exfat.u8
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BootRegionOperator(private val writeableData: RandomAccessData) {

    private var constantMetadata: ExFatFIleSystemConstantMetadata? = null

    suspend fun isExFat(data: RandomAccessData): Boolean {
        val boot = readAt(data, 0, BOOT_SECTOR_SIZE)
        try {
            checkExFAT(boot)
            return true
        } catch (_: IllegalArgumentException) {
            return false
        }
    }

    suspend fun readFullFileSystemMetadata(data: RandomAccessData): ExFatFileSystemMetadata {
        val boot = readAt(data, 0, BOOT_SECTOR_SIZE)
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

    suspend fun updateChangingMetadata(
        updateBackupBoot: Boolean = true,
        transform: (ExFatFileSystemChangingMetadata) -> ExFatFileSystemChangingMetadata
    ): ExFatFileSystemChangingMetadata {
        val boot = readAt(writeableData, 0, BOOT_SECTOR_SIZE)
        checkExFAT(boot)

        val current = parseOrThrowChangingMetadata(boot)
        val next = transform(current)

        // Patch sector 0
        writeChangingFieldsIntoBootSector(boot, next)
        writeAt(writeableData, 0, boot)

        // Patch backup boot sector 12 as well (recommended)
        if (updateBackupBoot) {
            val bytesPerSector = readConstantFileSystemMetadata(writeableData).bytesPerSector
            val backupBoot0Offset = BOOT_REGION_SIZE * bytesPerSector.toLong()
            val backupBoot = readAt(writeableData, backupBoot0Offset, BOOT_SECTOR_SIZE)
            // Only patch if backup looks like exFAT too (don’t brick weird images)
            if (runCatching { checkExFAT(backupBoot) }.isSuccess) {
                writeChangingFieldsIntoBootSector(backupBoot, next)
                writeAt(writeableData, backupBoot0Offset, backupBoot)
            }
        }

        return next
    }

    suspend fun setVolumeFlags(volumeFlags: Int, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        require(volumeFlags in 0..0xFFFF)
        return updateChangingMetadata(updateBackupBoot) { it.copy(volumeFlags = volumeFlags) }
    }

    suspend fun setPercentInUse(percent: Int, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        require(percent in 0..100) // по spec это 0..100 (иногда 0xFF как unknown; если надо — расширишь)
        return updateChangingMetadata(updateBackupBoot) { it.copy(percentInUse = percent) }
    }

    /**
     * Convenience: set/clear the "dirty" flag in volumeFlags (bit layout зависит от spec/драйвера).
     * Обычно dirty = bit0 (0x0001) — но проверь под твой сценарий.
     */
    suspend fun setDirty(isDirty: Boolean, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        return updateChangingMetadata(updateBackupBoot) { cur ->
            val dirtyMask = 0x0001
            val vf = if (isDirty) (cur.volumeFlags or dirtyMask) else (cur.volumeFlags and dirtyMask.inv())
            cur.copy(volumeFlags = vf)
        }
    }

    /**
     * If you ever perform operations that effectively "reformat/resize" (rare),
     * you MUST clear cached constant metadata.
     */
    suspend fun invalidateConstantMetadataCache() { constantMetadata = null }


    private fun writeChangingFieldsIntoBootSector(boot: ByteArray, meta: ExFatFileSystemChangingMetadata) {
        // volumeFlags u16le at 0x6A
        putU16le(boot, 0x6A, meta.volumeFlags)
        // percentInUse u8 at 0x70
        boot[0x70] = (meta.percentInUse and 0xFF).toByte()
    }

    // ----------------------------
    // Existing reads (optional)
    // ----------------------------

    suspend fun readConstantFileSystemMetadata(data: RandomAccessData): ExFatFIleSystemConstantMetadata {
        constantMetadata?.let { return it }
        val boot = readAt(data, 0L, BOOT_SECTOR_SIZE)
        val meta = parseOrThrowConstantMetadata(boot)
        constantMetadata = meta
        return meta
    }

    suspend fun readChangingFileSystemMetadata(data: RandomAccessData): ExFatFileSystemChangingMetadata {
        val boot = readAt(data, 0L, BOOT_SECTOR_SIZE)
        return parseOrThrowChangingMetadata(boot)
    }


    companion object {
        private const val BOOT_SECTOR_SIZE = 512
        private const val BOOT_REGION_SIZE = 12
    }
}