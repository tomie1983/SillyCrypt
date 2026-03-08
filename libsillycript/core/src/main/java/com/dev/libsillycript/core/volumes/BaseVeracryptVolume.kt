package com.dev.libsillycript.core.volumes

import com.dev.exfat.data.RandomAccessData
import com.dev.libsillycript.core.cache.SharedSectorCache
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Arrays
import kotlin.math.min

class BaseVeracryptVolume(
    private val data: RandomAccessData,
    private val cache: SharedSectorCache,
    private val encryptionData: EncryptionData,
    maxCachedSectors: Int = 2048
) : RandomAccessData {

    private val sectorSize = encryptionData.sectorSize
    private var cursor: Long = 0L


    override fun seek(pos: Long) {
        require(pos >= 0L) { "Negative seek: $pos" }
        require(pos <= size) { "Seek beyond volume size: $pos > $size" }
        cursor = pos
    }

    override suspend fun read(buf: ByteArray): Int {
        if (buf.isEmpty()) return 0
        if (cursor >= size) return -1

        val toRead = min(buf.size.toLong(), size - cursor).toInt()
        val absStart = encryptionData.offset + cursor
        val absEndExclusive = absStart + toRead

        val startSector = absStart / sectorSize
        val endSector = (absEndExclusive - 1) / sectorSize
        val startOffset = (absStart % sectorSize).toInt()

        val sectorBuf = ByteArray(sectorSize)
        var outOffset = 0

        try {
            for (sectorIndex in startSector..endSector) {
                readCipherSector(sectorIndex, sectorBuf)
                encryptionData.xts.decryptDataUnit(sectorBuf, 0, sectorSize, sectorIndex)

                val sectorStartAbs = sectorIndex * sectorSize.toLong()
                val copyStart = if (sectorIndex == startSector) startOffset else 0
                val copyEndExclusive = if (sectorIndex == endSector) {
                    (absEndExclusive - sectorStartAbs).toInt()
                } else {
                    sectorSize
                }

                val copyLen = copyEndExclusive - copyStart
                System.arraycopy(sectorBuf, copyStart, buf, outOffset, copyLen)
                outOffset += copyLen
            }
        } finally {
            Arrays.fill(sectorBuf, 0)
        }

        cursor += toRead
        return toRead
    }

    override suspend fun write(buf: ByteArray) {
        if (buf.isEmpty()) return
        require(cursor + buf.size <= size) { "Write beyond volume size" }

        val absStart = encryptionData.offset + cursor
        val absEndExclusive = absStart + buf.size.toLong()

        val startSector = absStart / sectorSize
        val endSector = (absEndExclusive - 1) / sectorSize
        val startOffset = (absStart % sectorSize).toInt()

        val sectorBuf = ByteArray(sectorSize)
        var inOffset = 0

        try {
            for (sectorIndex in startSector..endSector) {
                val sectorStartAbs = sectorIndex * sectorSize.toLong()
                val writeStart = if (sectorIndex == startSector) startOffset else 0
                val writeEndExclusive = if (sectorIndex == endSector) {
                    (absEndExclusive - sectorStartAbs).toInt()
                } else {
                    sectorSize
                }
                val writeLen = writeEndExclusive - writeStart
                val isWholeSectorWrite = writeStart == 0 && writeEndExclusive == sectorSize

                if (isWholeSectorWrite) {
                    System.arraycopy(buf, inOffset, sectorBuf, 0, sectorSize)
                } else {
                    readCipherSector(sectorIndex, sectorBuf)
                    encryptionData.xts.decryptDataUnit(sectorBuf, 0, sectorSize, sectorIndex)
                    System.arraycopy(buf, inOffset, sectorBuf, writeStart, writeLen)
                }

                encryptionData.xts.encryptDataUnit(sectorBuf, 0, sectorSize, sectorIndex)
                writeCipherSector(sectorIndex, sectorBuf)

                inOffset += writeLen
                Arrays.fill(sectorBuf, 0)
            }
        } finally {
            Arrays.fill(sectorBuf, 0)
        }

        cursor += buf.size.toLong()
    }

    override suspend fun readFully(): ByteArray {
        seek(0)
        require(size <= Int.MAX_VALUE.toLong()) { "Volume too large for readFully(): $size" }

        val out = ByteArray(size.toInt())
        var off = 0
        while (off < out.size) {
            val chunk = ByteArray(min(64 * 1024, out.size - off))
            val read = read(chunk)
            if (read <= 0) break
            System.arraycopy(chunk, 0, out, off, read)
            off += read
            Arrays.fill(chunk, 0)
        }
        return if (off == out.size) out else out.copyOf(off)
    }

    override val position: Long
        get() = cursor

    override val size: Long
        get() = encryptionData.size

    suspend fun getHashCode(): String {
        val currentPos = position
        val plain = readFully()
        val hash = MessageDigest.getInstance("SHA-512").digest(plain)
        val result = BigInteger(1, hash).toString(16).padStart(128, '0')
        Arrays.fill(plain, 0)
        seek(currentPos)
        return result
    }

    override suspend fun close() {
        cache.clear()
    }

    private suspend fun readCipherSector(sectorIndex: Long, out: ByteArray) {
        require(out.size == sectorSize) { "Invalid sector buffer size: ${out.size}" }

        cache.withSectorLock(sectorIndex) {
            val cached = cache.getSector(sectorIndex)
            if (cached != null) {
                System.arraycopy(cached, 0, out, 0, sectorSize)
                return@withSectorLock
            }

            val sectorPos = sectorIndex * sectorSize.toLong()
            data.seek(sectorPos)
            readExact(data, out, sectorSize)

            // В репозитории храним только ciphertext
            cache.putSector(sectorIndex, out)
        }
    }

    private suspend fun writeCipherSector(sectorIndex: Long, src: ByteArray) {
        require(src.size == sectorSize) { "Invalid sector buffer size: ${src.size}" }

        cache.withSectorLock(sectorIndex) {
            val sectorPos = sectorIndex * sectorSize.toLong()
            data.seek(sectorPos)
            data.write(src)

            // В кэше тоже ciphertext
            cache.putSector(sectorIndex, src)
        }
    }
    private suspend fun readExact(data: RandomAccessData, out: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val tmp = ByteArray(len - off)
            val n = data.read(tmp)
            if (n <= 0) {
                throw IOException("Incomplete sector read: expected=${len - off}, got=$n")
            }
            System.arraycopy(tmp, 0, out, off, n)
            off += n
            Arrays.fill(tmp, 0)
        }
    }
}