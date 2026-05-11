package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.exfat.bitmap.BitmapCreator
import com.dev.exfat.exfat.bootregion.BootRegionCreator
import com.dev.exfat.exfat.bootregion.BootRegionOperator
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.bootregion.ExFatFileSystemMetadata
import com.dev.exfat.exfat.fat.FATCreator
import com.dev.exfat.exfat.upcase.UpCaseTableCreator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExFATCreator(private val dataFactory: RandomAccessDataFactory) {

    private val mutex = Mutex()

    suspend fun create(
        bytesPerSectorShift: Int = 9,
        sectorsPerClusterShift: Int = 3,
        numberOfFats: Int = 1,
        volumeSerial: Long = (System.currentTimeMillis().toInt().toLong() and 0xFFFF_FFFFL),
        fsRevision: Int = 0x0100,
        volumeFlags: Int = 0,
    ): ExFatFileSystemMetadata = mutex.withLock {
        val data = dataFactory.create()
        try {
            val bootCreator = BootRegionCreator(data)
            val metadata = bootCreator.createBootRegions(
                volumeLengthBytes = data.size,
                bytesPerSectorShift = bytesPerSectorShift,
                sectorsPerClusterShift = sectorsPerClusterShift,
                numberOfFats = numberOfFats,
                volumeSerial = volumeSerial,
                fsRevision = fsRevision,
                volumeFlags = volumeFlags,
                percentInUse = 0,
            )

            val c = metadata.constantMetadata
            val layout = computeSystemLayout(c)
            val upCaseInfo = UpCaseTableCreator(data).createDefaultTable(
                meta = c,
                firstCluster = layout.upcaseFirstCluster,
                reservedClusterCount = layout.upcaseClusterCount
            )

            require(upCaseInfo.firstCluster == layout.upcaseFirstCluster)
            require(upCaseInfo.clusterCount == layout.upcaseClusterCount) {
                "Boot region and upcase layout disagree: boot=${layout.upcaseClusterCount} actual=${upCaseInfo.clusterCount}"
            }

            val fatChains = buildReservedChains(layout)
            val reservedClusters = fatChains.asSequence().flatMap { it.asSequence() }.toList().toIntArray()

            FATCreator(data).createEmptyFat(
                meta = c,
                allocatedChains = fatChains,
            )

            BitmapCreator(data).createEmptyBitmap(
                meta = c,
                bitmapFirstCluster = layout.bitmapFirstCluster,
                allocatedClusters = reservedClusters,
            )

            createEmptyRootDirectory(
                data = data,
                meta = c,
                bitmapFirstCluster = layout.bitmapFirstCluster,
                bitmapDataLength = layout.bitmapDataLength,
                upcaseFirstCluster = upCaseInfo.firstCluster,
                upcaseDataLength = upCaseInfo.dataLength,
                upcaseChecksum = upCaseInfo.checksum,
            )

            val percentInUse = ((reservedClusters.size * 100L) / c.clusterCount.toLong()).toInt()
            val bootOperator = BootRegionOperator(data)
            bootOperator.setPercentInUse(percentInUse, updateBackupBoot = true)

            metadata.copy(
                changingMetadata = metadata.changingMetadata.copy(percentInUse = percentInUse)
            )
        } finally {
            data.close()
        }
    }

    suspend fun close() {
        dataFactory.close()
    }

    private suspend fun createEmptyRootDirectory(
        data: RandomAccessData,
        meta: ExFatFIleSystemConstantMetadata,
        bitmapFirstCluster: Int,
        bitmapDataLength: Long,
        upcaseFirstCluster: Int,
        upcaseDataLength: Long,
        upcaseChecksum: Int,
    ) {
        val rootStart = clusterToByteOffset(meta, meta.rootDirFirstCluster.toInt())
        val clusterBytes = meta.bytesPerCluster.toInt()
        val root = ByteArray(clusterBytes)

        writeBitmapEntry(
            out = root,
            offset = 0,
            firstCluster = bitmapFirstCluster,
            dataLength = bitmapDataLength,
        )

        writeUpcaseEntry(
            out = root,
            offset = DIR_ENTRY_SIZE,
            checksum = upcaseChecksum,
            firstCluster = upcaseFirstCluster,
            dataLength = upcaseDataLength,
        )

        // Third entry remains 0x00 => end-of-directory marker.
        // The rest of the root directory cluster is also zero because the whole buffer was freshly allocated.
        writeAt(data, rootStart, root)
    }

    private fun writeBitmapEntry(
        out: ByteArray,
        offset: Int,
        firstCluster: Int,
        dataLength: Long,
    ) {
        out[offset] = 0x81.toByte() // Allocation Bitmap entry (in-use)
        out[offset + 1] = 0x00 // first/primary allocation bitmap
        putU32le(out, offset + 20, firstCluster)
        putU64le(out, offset + 24, dataLength)
    }

    private fun writeUpcaseEntry(
        out: ByteArray,
        offset: Int,
        checksum: Int,
        firstCluster: Int,
        dataLength: Long,
    ) {
        out[offset] = 0x82.toByte() // Up-case Table entry (in-use)
        putU32le(out, offset + 4, checksum)
        putU32le(out, offset + 20, firstCluster)
        putU64le(out, offset + 24, dataLength)
    }

    private fun buildReservedChains(layout: SystemLayout): List<IntArray> {
        val bitmapChain = IntArray(layout.bitmapClusterCount) { idx -> layout.bitmapFirstCluster + idx }
        val upcaseChain = IntArray(layout.upcaseClusterCount) { idx -> layout.upcaseFirstCluster + idx }
        val rootChain = intArrayOf(layout.rootFirstCluster)
        return listOf(bitmapChain, upcaseChain, rootChain)
    }

    private fun computeSystemLayout(meta: ExFatFIleSystemConstantMetadata): SystemLayout {
        val bitmapBytes = ceilDiv(meta.clusterCount.toLong(), 8L)
        val bitmapClusterCount = ceilDiv(bitmapBytes, meta.bytesPerCluster).toInt().coerceAtLeast(1)
        val upcaseBytes = DEFAULT_UPCASE_TABLE_BYTES
        val upcaseClusterCount = ceilDiv(upcaseBytes, meta.bytesPerCluster).toInt().coerceAtLeast(1)
        val bitmapFirstCluster = CLUSTERS_OFFSET
        val upcaseFirstCluster = bitmapFirstCluster + bitmapClusterCount
        val rootFirstCluster = upcaseFirstCluster + upcaseClusterCount

        require(rootFirstCluster.toLong() == meta.rootDirFirstCluster) {
            "Boot region layout mismatch: expected root=${meta.rootDirFirstCluster}, computed=$rootFirstCluster"
        }

        return SystemLayout(
            bitmapFirstCluster = bitmapFirstCluster,
            bitmapClusterCount = bitmapClusterCount,
            bitmapDataLength = bitmapBytes,
            upcaseFirstCluster = upcaseFirstCluster,
            upcaseClusterCount = upcaseClusterCount,
            rootFirstCluster = rootFirstCluster,
        )
    }

    private fun clusterToByteOffset(meta: ExFatFIleSystemConstantMetadata, cluster: Int): Long {
        return meta.heapStartByte + (cluster.toLong() - CLUSTERS_OFFSET) * meta.bytesPerCluster
    }

    private data class SystemLayout(
        val bitmapFirstCluster: Int,
        val bitmapClusterCount: Int,
        val bitmapDataLength: Long,
        val upcaseFirstCluster: Int,
        val upcaseClusterCount: Int,
        val rootFirstCluster: Int,
    )

    companion object {
        private const val DIR_ENTRY_SIZE = 32
        private const val CLUSTERS_OFFSET = 2
        private const val DEFAULT_UPCASE_TABLE_BYTES = 65536L * 2L
    }
}
