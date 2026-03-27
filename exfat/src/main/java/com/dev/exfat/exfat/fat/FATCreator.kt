package com.dev.exfat.exfat.fat

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.bootregion.ExFatFIleSystemConstantMetadata
import com.dev.exfat.exfat.writeAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FATCreator(private val data: RandomAccessData) {

    private val mutex = Mutex()

    suspend fun createEmptyFat(
        meta: ExFatFIleSystemConstantMetadata,
        allocatedChains: List<IntArray>,
    ) = mutex.withLock {
        require(meta.numberOfFats == 1 || meta.numberOfFats == 2) { "Unsupported numberOfFats=${meta.numberOfFats}" }

        val fatLengthBytes = meta.fatLengthSectors.toLong() * meta.bytesPerSector.toLong()
        require(fatLengthBytes >= 8L) { "FAT region is too small: $fatLengthBytes bytes" }

        val fat = ByteArray(fatLengthBytes.toInt())

        // FAT[0] and FAT[1] are reserved in exFAT.
        putU32le(fat, 0, FAT_RESERVED_MEDIA)
        putU32le(fat, 4, FAT_RESERVED_EOC)

        for (chain in allocatedChains) {
            if (chain.isEmpty()) continue
            for (index in chain.indices) {
                val cluster = chain[index]
                require(cluster >= CLUSTERS_OFFSET) {
                    "Reserved allocated cluster must be >= $CLUSTERS_OFFSET, got $cluster"
                }
                require(cluster <= meta.clusterCount + 1) {
                    "Reserved allocated cluster out of range: $cluster, clusterCount=${meta.clusterCount}"
                }
                val value = if (index == chain.lastIndex) END_OF_CHAIN else chain[index + 1]
                putEntry(fat, cluster, value)
            }
        }

        for (fatIndex in 0 until meta.numberOfFats) {
            val start = meta.fatStartByte + fatIndex.toLong() * fatLengthBytes
            writeAt(data, start, fat)
        }
    }

    private fun putEntry(fat: ByteArray, cluster: Int, value: Int) {
        val off = cluster * FAT_ENTRY_SIZE
        require(off + FAT_ENTRY_SIZE <= fat.size) {
            "FAT entry offset out of bounds: cluster=$cluster off=$off fatSize=${fat.size}"
        }
        putU32le(fat, off, value)
    }

    private fun putU32le(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    companion object {
        private const val FAT_ENTRY_SIZE = 4
        private const val CLUSTERS_OFFSET = 2

        private const val FAT_RESERVED_MEDIA = 0xFFFF_FFF8.toInt()
        private const val FAT_RESERVED_EOC = 0xFFFF_FFFF.toInt()
        private const val END_OF_CHAIN = 0xFFFF_FFF8.toInt()
    }
}
