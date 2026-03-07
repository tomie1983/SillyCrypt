package com.dev.libsillycript.core.xts

import com.dev.libsillycript.core.blockCiphers.BlockCipher
import com.dev.libsillycript.core.blockCiphers.BlockCipherFactory
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.keyStore.KeyStore
import java.util.Arrays
import kotlin.math.min


class XTSNew(
    private val keyStore: KeyStore,
    private val pairs: List<CipherPair>
) {
    data class CipherPair(val cipherA: BlockCipher, val cipherB: BlockCipher) {
        constructor(cipherFactory: BlockCipherFactory, cipherType: BlockCipherType) : this(
            cipherFactory.getCipher(cipherType),
            cipherFactory.getCipher(cipherType)
        )
    }

    suspend fun close() {
        keyStore.clearKey()
    }

    /**
     * Encrypt one data unit.
     * Обычно data unit = 1 сектор.
     */
    suspend fun encryptDataUnit(
        data: ByteArray,
        offset: Int,
        length: Int,
        dataUnitIndex: Long
    ): Boolean {
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return false
        require(length >= BLOCK) {
            "XTS requires at least one full 16-byte block per non-empty data unit (len=$length)"
        }

        val key = keyStore.getKey()
        try {
            for (pair in pairs) {
                xtsEncryptDataUnit(
                    cipherA = pair.cipherA,
                    cipherB = pair.cipherB,
                    buffer = data,
                    off = offset,
                    len = length,
                    dataUnitIndex = dataUnitIndex,
                    key = key
                )
            }
            return true
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * Decrypt one data unit.
     * Обычно data unit = 1 сектор.
     */
    suspend fun decryptDataUnit(
        data: ByteArray,
        offset: Int,
        length: Int,
        dataUnitIndex: Long
    ): Boolean {
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return false
        require(length >= BLOCK) {
            "XTS requires at least one full 16-byte block per non-empty data unit (len=$length)"
        }

        val key = keyStore.getKey()
        try {
            for (pair in pairs.asReversed()) {
                xtsDecryptDataUnit(
                    cipherA = pair.cipherA,
                    cipherB = pair.cipherB,
                    buffer = data,
                    off = offset,
                    len = length,
                    dataUnitIndex = dataUnitIndex,
                    key = key
                )
            }
            return true
        } finally {
            Arrays.fill(key, 0)
        }
    }

    /**
     * Совместимость со старым API: шифрует несколько подряд идущих data units.
     * Можно удалить, если нигде больше не используется.
     */
    suspend fun encrypt(
        data: ByteArray,
        offset: Int,
        length: Int,
        startSector: Long,
        dataUnitSize: Int = length
    ): Boolean {
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return false
        require(dataUnitSize >= BLOCK) { "dataUnitSize must be >= $BLOCK" }

        var cur = offset
        var left = length
        var sectorIndex = startSector

        while (left > 0) {
            val chunk = min(left, dataUnitSize)
            encryptDataUnit(data, cur, chunk, sectorIndex)
            cur += chunk
            left -= chunk
            sectorIndex++
        }
        return true
    }

    /**
     * Совместимость со старым API: дешифрует несколько подряд идущих data units.
     * Можно удалить, если нигде больше не используется.
     */
    suspend fun decrypt(
        data: ByteArray,
        offset: Int,
        length: Int,
        startSector: Long,
        dataUnitSize: Int = length
    ): Boolean {
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return false
        require(dataUnitSize >= BLOCK) { "dataUnitSize must be >= $BLOCK" }

        var cur = offset
        var left = length
        var sectorIndex = startSector

        while (left > 0) {
            val chunk = min(left, dataUnitSize)
            decryptDataUnit(data, cur, chunk, sectorIndex)
            cur += chunk
            left -= chunk
            sectorIndex++
        }
        return true
    }

    private fun xtsEncryptDataUnit(
        cipherA: BlockCipher,
        cipherB: BlockCipher,
        buffer: ByteArray,
        off: Int,
        len: Int,
        dataUnitIndex: Long,
        key: ByteArray
    ) {
        val hi = off + len
        var pos = off

        val (keyEncryption, keyTweak) = splitKeys(key)
        try {
            val tweak = initialTweak(dataUnitIndex, cipherB, keyTweak)
            try {
                while (pos + BLOCK <= hi) {
                    xor16InPlace(buffer, pos, tweak)
                    cipherA.encryptBlock(buffer, pos, buffer, pos, keyEncryption)
                    xor16InPlace(buffer, pos, tweak)
                    pos += BLOCK
                    gfMulX(tweak)
                }

                if (pos < hi) {
                    require(pos - BLOCK >= off) {
                        "XTS partial-block encryption requires at least one full block before tail"
                    }
                    val tp = pos - BLOCK
                    var p = pos
                    while (p < hi) {
                        val tmp = buffer[p - BLOCK]
                        buffer[p - BLOCK] = buffer[p]
                        buffer[p] = tmp
                        p++
                    }
                    xor16InPlace(buffer, tp, tweak)
                    cipherA.encryptBlock(buffer, tp, buffer, tp, keyEncryption)
                    xor16InPlace(buffer, tp, tweak)
                }
            } finally {
                Arrays.fill(tweak, 0)
            }
        } finally {
            Arrays.fill(keyEncryption, 0)
            Arrays.fill(keyTweak, 0)
        }
    }

    private fun xtsDecryptDataUnit(
        cipherA: BlockCipher,
        cipherB: BlockCipher,
        buffer: ByteArray,
        off: Int,
        len: Int,
        dataUnitIndex: Long,
        key: ByteArray
    ) {
        val hi = off + len
        var pos = off

        val (keyEncryption, keyTweak) = splitKeys(key)
        try {
            val tweak = initialTweak(dataUnitIndex, cipherB, keyTweak)
            val tweak2 = ByteArray(BLOCK)
            try {
                while (pos + BLOCK <= hi) {
                    val remaining = hi - pos
                    if (remaining > BLOCK && remaining < 2 * BLOCK) {
                        System.arraycopy(tweak, 0, tweak2, 0, BLOCK)
                        gfMulX(tweak)
                    }

                    xor16InPlace(buffer, pos, tweak)
                    cipherA.decryptBlock(buffer, pos, buffer, pos, keyEncryption)
                    xor16InPlace(buffer, pos, tweak)
                    pos += BLOCK
                    gfMulX(tweak)
                }

                if (pos < hi) {
                    val tp = pos - BLOCK
                    var p = pos
                    while (p < hi) {
                        val tmp = buffer[p - BLOCK]
                        buffer[p - BLOCK] = buffer[p]
                        buffer[p] = tmp
                        p++
                    }
                    xor16InPlace(buffer, tp, tweak2)
                    cipherA.decryptBlock(buffer, tp, buffer, tp, keyEncryption)
                    xor16InPlace(buffer, tp, tweak2)
                }
            } finally {
                Arrays.fill(tweak, 0)
                Arrays.fill(tweak2, 0)
            }
        } finally {
            Arrays.fill(keyEncryption, 0)
            Arrays.fill(keyTweak, 0)
        }
    }

    private fun initialTweak(dataUnitIndex: Long, cipherB: BlockCipher, key: ByteArray): ByteArray {
        val t = ByteArray(BLOCK)
        var v = dataUnitIndex
        for (i in 0 until 8) {
            t[i] = (v and 0xFFL).toByte()
            v = v ushr 8
        }
        cipherB.encryptBlock(t, 0, t, 0, key)
        return t
    }

    private fun gfMulX(x: ByteArray) {
        require(x.size == BLOCK)

        val msb = (x[15].toInt() and 0x80) != 0
        for (i in 15 downTo 1) {
            val cur = x[i].toInt() and 0xFF
            val prevMsb = (x[i - 1].toInt() and 0x80) ushr 7
            x[i] = (((cur shl 1) and 0xFE) or prevMsb).toByte()
        }

        var b0 = ((x[0].toInt() and 0xFF) shl 1) and 0xFF
        if (msb) b0 = b0 xor 0x87
        x[0] = b0.toByte()
    }

    private fun xor16InPlace(buf: ByteArray, off: Int, mask: ByteArray) {
        var i = 0
        while (i < BLOCK) {
            buf[off + i] = (buf[off + i].toInt() xor (mask[i].toInt() and 0xFF)).toByte()
            i++
        }
    }

    private fun checkBounds(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "offset/length out of bounds: off=$offset len=$length data=${data.size}"
        }
    }

    private fun splitKeys(key: ByteArray): Pair<ByteArray, ByteArray> {
        val halfKey = key.size / 2
        val key1 = ByteArray(halfKey)
        val key2 = ByteArray(halfKey)
        System.arraycopy(key, 0, key1, 0, halfKey)
        System.arraycopy(key, halfKey, key2, 0, halfKey)
        return key1 to key2
    }

    companion object {
        private const val BLOCK = 16
    }
}