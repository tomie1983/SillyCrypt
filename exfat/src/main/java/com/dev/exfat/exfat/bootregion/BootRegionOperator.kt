package com.dev.exfat.exfat.bootregion

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.putU16le
import com.dev.exfat.exfat.putU32le
import com.dev.exfat.exfat.readAt
import com.dev.exfat.exfat.u16le
import com.dev.exfat.exfat.u32le
import com.dev.exfat.exfat.u8
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BootRegionOperator(private val writeableData: RandomAccessData) {

    private val metaMutex = Mutex()
    private var constantMetadata: ExFatFIleSystemConstantMetadata? = null

    suspend fun isExFat(data: RandomAccessData): Boolean {
        return try {
            val boot = readBootSector(data, 0L)
            checkExFAT(boot)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    suspend fun readFullFileSystemMetadata(data: RandomAccessData): ExFatFileSystemMetadata {
        val boot = readBootSector(data, 0L)
        val changingMetadata = parseOrThrowChangingMetadata(boot)
        val constantMetadata = constantMetadata ?: parseOrThrowConstantMetadata(boot).also {
            constantMetadata = it
        }
        return ExFatFileSystemMetadata(constantMetadata, changingMetadata)
    }

    private fun checkExFAT(boot: ByteArray) {
        require(boot.size >= MIN_BOOT_HEADER_SIZE) {
            "Boot sector too small: ${boot.size}"
        }

        val fsName = boot.copyOfRange(3, 11).toString(Charsets.US_ASCII)
        if (fsName != "EXFAT   ") throw IllegalArgumentException("Not exFAT (fsName=$fsName)")

        val sigOffset = boot.size - 2
        val sig = u16le(boot, sigOffset)
        if (sig != 0xAA55) throw IllegalArgumentException("Bad signature (0x${sig.toString(16)})")
    }

    private suspend fun readBootSector(data: RandomAccessData, sector0OffsetBytes: Long): ByteArray {
        val header = readAt(data, sector0OffsetBytes, MIN_BOOT_HEADER_SIZE)
        require(header.size >= MIN_BOOT_HEADER_SIZE) {
            "Boot header too small: ${header.size}"
        }

        val bytesPerSector = 1 shl u8(header[0x6C])
        require(bytesPerSector in SUPPORTED_BYTES_PER_SECTOR) {
            "Unsupported bytesPerSector=$bytesPerSector"
        }

        return if (bytesPerSector == MIN_BOOT_HEADER_SIZE) {
            header
        } else {
            readAt(data, sector0OffsetBytes, bytesPerSector)
        }
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
    ): ExFatFileSystemChangingMetadata = metaMutex.withLock {
        val constMeta = readConstantFileSystemMetadata(writeableData)
        val bytesPerSector = constMeta.bytesPerSector

        val mainBoot = readBootSector(writeableData, 0L)
        checkExFAT(mainBoot)

        val current = parseOrThrowChangingMetadata(mainBoot)
        val next = transform(current)
        require(next.volumeFlags in 0..0xFFFF) { "volumeFlags out of range: ${next.volumeFlags}" }
        require(next.percentInUse in 0..100) { "percentInUse out of range: ${next.percentInUse}" }

        writeChangingFieldsIntoBootSector(mainBoot, next)
        writeAt(writeableData, 0L, mainBoot)
        rewriteBootChecksumSector(regionStartOffsetBytes = 0L, bytesPerSector = bytesPerSector)

        if (updateBackupBoot) {
            val backupBoot0Offset = BOOT_REGION_SIZE_SECTORS * bytesPerSector.toLong()
            val backupBoot = readBootSector(writeableData, backupBoot0Offset)
            if (runCatching { checkExFAT(backupBoot) }.isSuccess) {
                writeChangingFieldsIntoBootSector(backupBoot, next)
                writeAt(writeableData, backupBoot0Offset, backupBoot)
                rewriteBootChecksumSector(
                    regionStartOffsetBytes = backupBoot0Offset,
                    bytesPerSector = bytesPerSector
                )
            }
        }

        next
    }

    suspend fun setVolumeFlags(volumeFlags: Int, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        require(volumeFlags in 0..0xFFFF)
        return updateChangingMetadata(updateBackupBoot) { it.copy(volumeFlags = volumeFlags) }
    }

    suspend fun setPercentInUse(percent: Int, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        require(percent in 0..100)
        return updateChangingMetadata(updateBackupBoot) { it.copy(percentInUse = percent) }
    }

    suspend fun setDirty(isDirty: Boolean, updateBackupBoot: Boolean = true): ExFatFileSystemChangingMetadata {
        return updateChangingMetadata(updateBackupBoot) { cur ->
            val dirtyMask = 0x0001
            val vf = if (isDirty) {
                cur.volumeFlags or dirtyMask
            } else {
                cur.volumeFlags and dirtyMask.inv()
            }
            cur.copy(volumeFlags = vf)
        }
    }

    suspend fun invalidateConstantMetadataCache() {
        metaMutex.withLock {
            constantMetadata = null
        }
    }

    private fun writeChangingFieldsIntoBootSector(boot: ByteArray, meta: ExFatFileSystemChangingMetadata) {
        putU16le(boot, 0x6A, meta.volumeFlags)
        boot[0x70] = (meta.percentInUse and 0xFF).toByte()
    }

    private suspend fun rewriteBootChecksumSector(regionStartOffsetBytes: Long, bytesPerSector: Int): Int {
        val regionBytes = readAt(
            writeableData,
            regionStartOffsetBytes,
            CHECKSUM_INPUT_SECTORS * bytesPerSector
        )
        val checksum = computeBootRegionChecksum(regionBytes, regionStart = 0, bytesPerSector = bytesPerSector)
        val checksumSector = ByteArray(bytesPerSector)
        writeBootChecksumSector(checksumSector, sectorIndex = 0, bytesPerSector = bytesPerSector, checksum = checksum)
        val checksumSectorOffset = regionStartOffsetBytes + CHECKSUM_INPUT_SECTORS * bytesPerSector.toLong()
        writeAt(writeableData, checksumSectorOffset, checksumSector)
        return checksum
    }

    private fun computeBootRegionChecksum(bytes: ByteArray, regionStart: Int, bytesPerSector: Int): Int {
        var sum = 0
        val total = CHECKSUM_INPUT_SECTORS * bytesPerSector

        for (i in 0 until total) {
            if (i == 0x6A || i == 0x6B || i == 0x70) continue

            val v = bytes[regionStart + i].toInt() and 0xFF
            sum = (sum ushr 1) or (sum shl 31)
            sum += v
        }
        return sum
    }

    private fun writeBootChecksumSector(out: ByteArray, sectorIndex: Int, bytesPerSector: Int, checksum: Int) {
        val off = sectorIndex * bytesPerSector
        var p = off
        while (p < off + bytesPerSector) {
            putU32le(out, p, checksum)
            p += 4
        }
    }

    suspend fun readConstantFileSystemMetadata(data: RandomAccessData): ExFatFIleSystemConstantMetadata {
        constantMetadata?.let { return it }
        val boot = readBootSector(data, 0L)
        val meta = parseOrThrowConstantMetadata(boot)
        constantMetadata = meta
        return meta
    }

    suspend fun readChangingFileSystemMetadata(data: RandomAccessData): ExFatFileSystemChangingMetadata {
        val boot = readBootSector(data, 0L)
        return parseOrThrowChangingMetadata(boot)
    }

    companion object {
        private const val MIN_BOOT_HEADER_SIZE = 512
        private const val BOOT_REGION_SIZE_SECTORS = 12
        private const val CHECKSUM_INPUT_SECTORS = 11
        private val SUPPORTED_BYTES_PER_SECTOR = setOf(512, 1024, 2048, 4096)
    }
}