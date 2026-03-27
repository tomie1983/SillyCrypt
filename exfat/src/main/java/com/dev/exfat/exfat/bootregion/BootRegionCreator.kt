package com.dev.exfat.exfat.bootregion

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.ExFATFS.Companion.MB
import com.dev.exfat.exfat.alignUp
import com.dev.exfat.exfat.ceilDiv
import com.dev.exfat.exfat.putU16le
import com.dev.exfat.exfat.putU32le
import com.dev.exfat.exfat.putU64le
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BootRegionCreator(private val data: RandomAccessData) {

    private val metaMutex = Mutex()

    suspend fun createBootRegions(
        volumeLengthBytes: Long = data.size,
        bytesPerSectorShift: Int = BYTES_PER_SECTOR_SHIFT,         // 512 bytes
        sectorsPerClusterShift: Int = SECTORS_PER_CLUSTER_SHIFT,      // 8 sectors -> 4096-byte cluster (with 512B sectors)
        numberOfFats: Int = NUMBER_OF_FATS,
        volumeSerial: Long = (System.currentTimeMillis().toInt().toLong() and 0xFFFF_FFFFL),
        fsRevision: Int = 0x0100,
        volumeFlags: Int = 0,
        percentInUse: Int = 0
    ): ExFatFileSystemMetadata = metaMutex.withLock {
        require(numberOfFats == 1 || numberOfFats == 2)
        require(volumeFlags in 0..0xFFFF)
        require(percentInUse in 0..100)

        val bytesPerSector = 1 shl bytesPerSectorShift
        require(bytesPerSector in setOf(512, 1024, 2048, 4096)) { "Unsupported bytesPerSector=$bytesPerSector" }

        val sectorsPerCluster = 1 shl sectorsPerClusterShift
        require(sectorsPerCluster > 0)

        require(volumeLengthBytes % bytesPerSector.toLong() == 0L) {
            "volumeLengthBytes must be multiple of bytesPerSector"
        }
        val volumeLengthSectors = volumeLengthBytes / bytesPerSector.toLong()
        require(volumeLengthSectors > 0) { "Volume too small" }

        // VeraCrypt/Windows-like alignment: 1 MiB boundaries.
        val alignmentSectors = (MB / bytesPerSector.toLong()).toInt()
        require(alignmentSectors > 0 && MB % bytesPerSector.toLong() == 0L) {
            "1MiB must be divisible by bytesPerSector"
        }

        // VeraCrypt-like FAT offset: exactly 1MiB.
        val fatOffsetSectors = alignmentSectors

        // Iteratively solve for FAT length and cluster count.
        val layout = computeLayoutVeraCryptLike(
            volumeLengthSectors = volumeLengthSectors,
            bytesPerSector = bytesPerSector,
            sectorsPerCluster = sectorsPerCluster,
            numberOfFats = numberOfFats,
            fatOffsetSectors = fatOffsetSectors,
            alignmentSectors = alignmentSectors
        )

        val bytesPerCluster = bytesPerSector.toLong() * sectorsPerCluster.toLong()

        // ---- NEW: compute bitmap + upcase placement ----

        // Allocation bitmap size: 1 bit per cluster in heap
        val bitmapBytes = ceilDiv(layout.clusterCount.toLong(), 8L) // ceil(clusterCount / 8)
        val bitmapClusters = ceilDiv(bitmapBytes, bytesPerCluster).toInt().coerceAtLeast(1)

        // Upcase table: full 128 KiB for max compatibility (65536 * 2 bytes)
        val upcaseBytes = 65536L * 2L
        val upcaseClusters = ceilDiv(upcaseBytes, bytesPerCluster).toInt().coerceAtLeast(1)

        // Root directory cluster is placed right after bitmap + upcase
        val rootDirFirstCluster = 2 + bitmapClusters + upcaseClusters

        require(rootDirFirstCluster >= 2)
        require(rootDirFirstCluster <= layout.clusterCount + 1) {
            "rootDirFirstCluster=$rootDirFirstCluster out of range for clusterCount=${layout.clusterCount}"
        }

        // Build full 24-sector boot area in memory: all zeros, then patch fields.
        val totalBootBytes = 24 * bytesPerSector
        val bootArea = ByteArray(totalBootBytes)

        // Put 0x55AA at the end of sectors 0..10 and 12..22.
        for (sector in 0..10) putSignature55AA(bootArea, sector, bytesPerSector)
        for (sector in 12..22) putSignature55AA(bootArea, sector, bytesPerSector)

        // Write boot sector fields into sector 0 (main) and sector 12 (backup)
        writeBootSector(
            out = bootArea,
            sectorIndex = 0,
            bytesPerSector = bytesPerSector,
            volumeLengthSectors = volumeLengthSectors,
            fatOffsetSectors = fatOffsetSectors,
            fatLengthSectors = layout.fatLengthSectors,
            heapOffsetSectors = layout.heapOffsetSectors,
            clusterCount = layout.clusterCount,
            rootDirFirstCluster = rootDirFirstCluster,
            volumeSerial = volumeSerial,
            fsRevision = fsRevision,
            volumeFlags = volumeFlags,
            bytesPerSectorShift = bytesPerSectorShift,
            sectorsPerClusterShift = sectorsPerClusterShift,
            numberOfFats = numberOfFats,
            driveSelect = 0x80,
            percentInUse = percentInUse
        )
        writeBootSector(
            out = bootArea,
            sectorIndex = 12,
            bytesPerSector = bytesPerSector,
            volumeLengthSectors = volumeLengthSectors,
            fatOffsetSectors = fatOffsetSectors,
            fatLengthSectors = layout.fatLengthSectors,
            heapOffsetSectors = layout.heapOffsetSectors,
            clusterCount = layout.clusterCount,
            rootDirFirstCluster = rootDirFirstCluster,
            volumeSerial = volumeSerial,
            fsRevision = fsRevision,
            volumeFlags = volumeFlags,
            bytesPerSectorShift = bytesPerSectorShift,
            sectorsPerClusterShift = sectorsPerClusterShift,
            numberOfFats = numberOfFats,
            driveSelect = 0x80,
            percentInUse = percentInUse
        )

        // Checksums
        val mainChecksum = computeBootRegionChecksum(bootArea, regionStart = 0, bytesPerSector = bytesPerSector)
        writeBootChecksumSector(bootArea, sectorIndex = 11, bytesPerSector = bytesPerSector, checksum = mainChecksum)

        val backupChecksum = computeBootRegionChecksum(bootArea, regionStart = 12 * bytesPerSector, bytesPerSector = bytesPerSector)
        writeBootChecksumSector(bootArea, sectorIndex = 23, bytesPerSector = bytesPerSector, checksum = backupChecksum)

        // Write
        writeAt(data, 0, bootArea)

        // Cache
        val fatStartByte = fatOffsetSectors.toLong() * bytesPerSector.toLong()
        val heapStartByte = layout.heapOffsetSectors.toLong() * bytesPerSector.toLong()

        val constMeta = ExFatFIleSystemConstantMetadata(
            bytesPerSector = bytesPerSector,
            sectorsPerCluster = sectorsPerCluster,
            bytesPerCluster = bytesPerCluster,
            fatOffsetSectors = fatOffsetSectors,
            fatLengthSectors = layout.fatLengthSectors,
            clusterHeapOffsetSectors = layout.heapOffsetSectors,
            clusterCount = layout.clusterCount,
            rootDirFirstCluster = rootDirFirstCluster.toLong(),
            numberOfFats = numberOfFats,
            volumeSerial = volumeSerial and 0xFFFF_FFFFL,
            fsRevision = fsRevision,
            fatStartByte = fatStartByte,
            heapStartByte = heapStartByte
        )

        ExFatFileSystemMetadata(
            constantMetadata = constMeta,
            changingMetadata = ExFatFileSystemChangingMetadata(volumeFlags, percentInUse)
        )
    }
    // ----------------------------
    // Layout calc (VeraCrypt-like)
    // ----------------------------

    data class LayoutResult(
        val fatLengthSectors: Int,
        val heapOffsetSectors: Int,
        val clusterCount: Int
    )

    // ----------------------------
    // Boot region writing helpers
    // ----------------------------

    private fun writeBootSector(
        out: ByteArray,
        sectorIndex: Int,
        bytesPerSector: Int,
        volumeLengthSectors: Long,
        fatOffsetSectors: Int,
        fatLengthSectors: Int,
        heapOffsetSectors: Int,
        clusterCount: Int,
        rootDirFirstCluster: Int,
        volumeSerial: Long,
        fsRevision: Int,
        volumeFlags: Int,
        bytesPerSectorShift: Int,
        sectorsPerClusterShift: Int,
        numberOfFats: Int,
        driveSelect: Int,
        percentInUse: Int
    ) {
        val off = sectorIndex * bytesPerSector

        // JumpBoot
        out[off + 0] = 0xEB.toByte()
        out[off + 1] = 0x76.toByte()
        out[off + 2] = 0x90.toByte()

        // FileSystemName "EXFAT   "
        val name = "EXFAT   ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(name, 0, out, off + 0x03, 8)

        // MustBeZero stays zero

        // PartitionOffset (0x40): 0 for container
        putU64le(out, off + 0x40, 0L)

        // VolumeLength (0x48): in sectors
        putU64le(out, off + 0x48, volumeLengthSectors)

        // FAT/Heap fields (in sectors)
        putU32le(out, off + 0x50, fatOffsetSectors)
        putU32le(out, off + 0x54, fatLengthSectors)
        putU32le(out, off + 0x58, heapOffsetSectors)
        putU32le(out, off + 0x5C, clusterCount)
        putU32le(out, off + 0x60, rootDirFirstCluster)

        // Volume serial (u32)
        putU32le(out, off + 0x64, (volumeSerial and 0xFFFF_FFFFL).toInt())

        // FS revision (u16)
        putU16le(out, off + 0x68, fsRevision)

        // VolumeFlags (u16) - you asked to set 0
        putU16le(out, off + 0x6A, volumeFlags)

        // Shifts
        out[off + 0x6C] = (bytesPerSectorShift and 0xFF).toByte()
        out[off + 0x6D] = (sectorsPerClusterShift and 0xFF).toByte()

        // NumberOfFats
        out[off + 0x6E] = (numberOfFats and 0xFF).toByte()

        // DriveSelect (dump shows 0x80)
        out[off + 0x6F] = (driveSelect and 0xFF).toByte()

        // PercentInUse
        out[off + 0x70] = (percentInUse and 0xFF).toByte()

        // BootSignature 0x55AA already set by putSignature55AA(), but set explicitly too
        out[off + bytesPerSector - 2] = 0x55.toByte()
        out[off + bytesPerSector - 1] = 0xAA.toByte()
    }

    private fun putSignature55AA(bytes: ByteArray, sectorIndex: Int, bytesPerSector: Int) {
        val off = sectorIndex * bytesPerSector
        bytes[off + bytesPerSector - 2] = 0x55.toByte()
        bytes[off + bytesPerSector - 1] = 0xAA.toByte()
    }

    /**
     * Checksum over sectors 0..10 (11 sectors) of the given boot region.
     * Excludes bytes at offsets 0x6A, 0x6B, 0x70 within the boot sector (sector 0 of that region).
     */
    private fun computeBootRegionChecksum(bytes: ByteArray, regionStart: Int, bytesPerSector: Int): Int {
        var sum = 0
        val total = 11 * bytesPerSector

        for (i in 0 until total) {
            // exclude VolumeFlags (0x6A..0x6B) and PercentInUse (0x70) of the first sector of the region
            if (i == 0x6A || i == 0x6B || i == 0x70) continue

            val v = bytes[regionStart + i].toInt() and 0xFF
            sum = (sum ushr 1) or (sum shl 31) // rotate-right by 1
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

    companion object {
        private const val ITERATIONS_LIMIT = 64

        private const val BYTES_PER_SECTOR_SHIFT = 9

        private const val SECTORS_PER_CLUSTER_SHIFT = 3

        private const val NUMBER_OF_FATS = 1

        private fun computeLayoutVeraCryptLike(
            volumeLengthSectors: Long,
            bytesPerSector: Int,
            sectorsPerCluster: Int,
            numberOfFats: Int,
            fatOffsetSectors: Int,
            alignmentSectors: Int
        ): LayoutResult {
            // initial guess: assume heap starts at 2MiB => safe-ish
            var clusterCount = ((volumeLengthSectors - (2L * alignmentSectors)) / sectorsPerCluster.toLong())
                .toInt()
                .coerceAtLeast(1)

            var fatLength: Int
            var heapOffset: Int

            repeat(ITERATIONS_LIMIT) {
                val fatEntries = clusterCount + 2 // FAT has entries from 0; data clusters start at 2
                val fatBytes = fatEntries.toLong() * 4L
                val fatSectors = ceilDiv(fatBytes, bytesPerSector.toLong()).toInt()

                // VeraCrypt/Windows behavior: FAT length aligned to cluster boundary (your dump: 6 -> 8)
                fatLength = alignUp(fatSectors, sectorsPerCluster)

                val fatEnd = fatOffsetSectors + numberOfFats * fatLength

                // VeraCrypt/Windows behavior: heap aligned to 1MiB boundary (your dump: 2056 -> 4096)
                heapOffset = alignUp(fatEnd, alignmentSectors)

                val newClusterCount = ((volumeLengthSectors - heapOffset.toLong()) / sectorsPerCluster.toLong())
                    .toInt()
                    .coerceAtLeast(0)

                if (newClusterCount == clusterCount) {
                    require(clusterCount > 0) { "Volume too small after layout" }
                    return LayoutResult(
                        fatLengthSectors = fatLength,
                        heapOffsetSectors = heapOffset,
                        clusterCount = clusterCount
                    )
                }
                clusterCount = newClusterCount
            }

            throw IllegalStateException("Failed to converge layout for exFAT")
        }

        fun computeFirstUserDataByte(
            volumeLengthBytes: Long,
            bytesPerSectorShift: Int = BYTES_PER_SECTOR_SHIFT,        // 512 bytes
            sectorsPerClusterShift: Int = SECTORS_PER_CLUSTER_SHIFT,     // 8 sectors -> 4096-byte cluster
            numberOfFats: Int = NUMBER_OF_FATS,
        ): Long {
            require(numberOfFats == 1 || numberOfFats == 2)

            val bytesPerSector = 1 shl bytesPerSectorShift
            require(bytesPerSector in setOf(512, 1024, 2048, 4096)) {
                "Unsupported bytesPerSector=$bytesPerSector"
            }

            val sectorsPerCluster = 1 shl sectorsPerClusterShift
            require(sectorsPerCluster > 0) { "sectorsPerCluster must be > 0" }

            require(volumeLengthBytes % bytesPerSector.toLong() == 0L) {
                "volumeLengthBytes must be multiple of bytesPerSector"
            }

            val volumeLengthSectors = volumeLengthBytes / bytesPerSector.toLong()
            require(volumeLengthSectors > 0) { "Volume too small" }

            val alignmentSectors = (MB / bytesPerSector.toLong()).toInt()
            require(alignmentSectors > 0 && MB % bytesPerSector.toLong() == 0L) {
                "1MiB must be divisible by bytesPerSector"
            }

            val layout = computeLayoutVeraCryptLike(
                volumeLengthSectors = volumeLengthSectors,
                bytesPerSector = bytesPerSector,
                sectorsPerCluster = sectorsPerCluster,
                numberOfFats = numberOfFats,
                fatOffsetSectors = alignmentSectors,
                alignmentSectors = alignmentSectors
            )

            val bytesPerCluster = bytesPerSector.toLong() * sectorsPerCluster.toLong()

            // Allocation bitmap: 1 bit per cluster in heap
            val bitmapBytes = ceilDiv(layout.clusterCount.toLong(), 8L)
            val bitmapClusters = ceilDiv(bitmapBytes, bytesPerCluster).toInt().coerceAtLeast(1)

            // Full upcase table: 65536 UTF-16 code units
            val upcaseBytes = 65536L * 2L
            val upcaseClusters = ceilDiv(upcaseBytes, bytesPerCluster).toInt().coerceAtLeast(1)

            // Root dir occupies 1 cluster and starts right after bitmap + upcase
            val rootDirFirstCluster = 2 + bitmapClusters + upcaseClusters
            require(rootDirFirstCluster <= layout.clusterCount + 1) {
                "Volume too small: rootDirFirstCluster=$rootDirFirstCluster, clusterCount=${layout.clusterCount}"
            }

            val heapStartByte = layout.heapOffsetSectors.toLong() * bytesPerSector.toLong()

            // First non-service cluster = cluster right after root directory cluster
            val firstUserDataCluster = rootDirFirstCluster + 1

            return heapStartByte + (firstUserDataCluster - 2L) * bytesPerCluster
        }
    }
}