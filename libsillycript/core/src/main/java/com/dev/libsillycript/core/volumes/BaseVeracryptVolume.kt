package com.dev.libsillycript.core.volumes

import com.dev.exfat.data.RandomAccessData
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.min

class BaseVeracryptVolume(
    private val data: RandomAccessData,
    private val encryptionData: EncryptionData
) : RandomAccessData {

    override fun seek(pos: Long) {
        if (pos > encryptionData.size) {
            throw RuntimeException()
        }
        val position = pos + encryptionData.offset
        data.seek(position)
    }

    override suspend fun read(buf: ByteArray): Int {
        if (position + buf.size > size) {
            return -1
        }
        val initialSector = data.position / encryptionData.sectorSize
        val initialOffset = (data.position % encryptionData.sectorSize).toInt()
        val endSector = (data.position + buf.size) / encryptionData.sectorSize
        val endOffset = ((data.position + buf.size) % encryptionData.sectorSize).toInt()
        decryptSectors(buf, initialSector, endSector, initialOffset, endOffset)
        return buf.size
    }

    private suspend fun decryptSectors(
        resultBuffer: ByteArray,
        startSector: Long,
        endSector: Long,
        initialOffset: Int,
        endOffset: Int
    ): Int {
        val sectorBuf = ByteArray(encryptionData.sectorSize)
        data.seek(startSector * encryptionData.sectorSize)
        for (sectorIndex in startSector..endSector) {
            val read = data.read(sectorBuf)
            if (read < encryptionData.sectorSize)
                throw IOException("Incomplete sector read: $read bytes")
            encryptionData.xts.decrypt(sectorBuf, 0, read, sectorIndex)
            val currentOffset = encryptionData.sectorSize * (sectorIndex - startSector).toInt()
            if (sectorIndex == startSector) {
                System.arraycopy(
                    sectorBuf,
                    initialOffset,
                    resultBuffer,
                    0,
                    min(encryptionData.sectorSize - initialOffset, resultBuffer.size)
                )
            } else if (sectorIndex == endSector) {
                System.arraycopy(sectorBuf, 0, resultBuffer, currentOffset, endOffset)
            } else {
                System.arraycopy(
                    sectorBuf,
                    0,
                    resultBuffer,
                    currentOffset,
                    encryptionData.sectorSize
                )
            }
        }
        data.seek(endSector * encryptionData.sectorSize + endOffset)
        return resultBuffer.size
    }

    private suspend fun encryptSectors(
        plainData: ByteArray,
        startSector: Long,
        endSector: Long,
        initialOffset: Int,
        endOffset: Int
    ) {
        val sectorBuf = ByteArray(encryptionData.sectorSize)
        data.seek(startSector * encryptionData.sectorSize)
        for (sectorIndex in startSector..endSector) {
            val currentOffset = encryptionData.sectorSize * (sectorIndex - startSector).toInt()
            if (sectorIndex == startSector) {
                data.read(sectorBuf)
                encryptionData.xts.decrypt(sectorBuf,0,encryptionData.sectorSize,sectorIndex)
                System.arraycopy(
                    plainData,
                    0,
                    sectorBuf,
                    initialOffset,
                    min(encryptionData.sectorSize - initialOffset, plainData.size)
                )
                data.seek(startSector * encryptionData.sectorSize)
            } else if (sectorIndex == endSector) {
                data.read(sectorBuf)
                encryptionData.xts.decrypt(sectorBuf,0,encryptionData.sectorSize,sectorIndex)
                System.arraycopy(
                    plainData,
                    plainData.size - endOffset,
                    sectorBuf,
                    endOffset,
                    endOffset
                )
                data.seek(endSector * encryptionData.sectorSize)
            } else {
                System.arraycopy(plainData, currentOffset, sectorBuf, 0, encryptionData.sectorSize)
            }
            encryptionData.xts.encrypt(sectorBuf,0,encryptionData.sectorSize,sectorIndex)
            data.write(sectorBuf)
        }
        data.seek(endSector * encryptionData.sectorSize + endOffset)
    }

    override suspend fun write(buf: ByteArray) {
        if (position + buf.size > size) {
            throw RuntimeException()
        }
        val initialSector = data.position / encryptionData.sectorSize
        val initialOffset = (data.position % encryptionData.sectorSize).toInt()
        val endSector = (data.position + buf.size) / encryptionData.sectorSize
        val endOffset = ((data.position + buf.size) % encryptionData.sectorSize).toInt()
        encryptSectors(buf, initialSector, endSector, initialOffset, endOffset)
    }

    override suspend fun readFully(): ByteArray {
        seek(0)
        if (size > Int.MAX_VALUE) {
            throw RuntimeException()
        }
        val buffer = ByteArray(size.toInt())
        val initialSector = data.position / encryptionData.sectorSize
        val endSector = (data.position + size) / encryptionData.sectorSize
        decryptSectors(buffer, initialSector, endSector, 0, 0)
        return buffer
    }

    override val position: Long
        get() = data.position - encryptionData.offset

    override val size: Long
        get() = encryptionData.size

    suspend fun getHashCode(): String {
            val data = readFully()
            val hash = MessageDigest.getInstance("SHA-512").digest(data)
            val result = BigInteger(1, hash).toString(16).padStart(128, '0')
            seek(0)
            return result
        }

    override suspend fun close() {
        encryptionData.xts.close()
    }
}