package com.dev.libsillycript.core.xts

import com.dev.libsillycript.core.blockCiphers.BlockCipher
import com.dev.libsillycript.core.blockCiphers.BlockCipherFactory
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.keyStore.KeyStore
import java.util.Arrays


class XTSNew(private val keyStore: KeyStore, private val pairs: List<CipherPair>) {
    data class CipherPair(val cipherA: BlockCipher, val cipherB: BlockCipher) {
        constructor(cipherFactory: BlockCipherFactory, cipherType: BlockCipherType): this(
            cipherFactory.getCipher(cipherType), cipherFactory.getCipher(cipherType)
        )
    }

    suspend fun close() {
        keyStore.clearKey()
    }

    /**
     * Шифрование, как xts_encrypt(context,...).
     * Возвращает 0 при успехе.
     */
    suspend fun encrypt(data: ByteArray, offset: Int, length: Int, startSector: Long): Int {
        val key = keyStore.getKey()
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return 0

        var sectorIndex: Long
        var cur: Int
        var left: Int

        // По всем добавленным парам (вперёд), как в C-коде (head -> tail)
        for (pair in pairs) {
            cur = offset
            left = length
            sectorIndex = startSector

            while (left > 0) {
                val incr = if (left >= SECTOR_SIZE) SECTOR_SIZE else left
                if (!isBufferAllZero(data, cur, SECTOR_SIZE)) {
                    xtsEncryptSector(pair.cipherA, pair.cipherB, data, cur, incr, sectorIndex, key)
                }
                cur += incr
                left -= incr
                sectorIndex++
            }
        }
        Arrays.fill(key, 0.toByte())
        return 0
    }

    /**
     * Дешифрование, как xts_decrypt(context,...).
     * Возвращает 0 при успехе.
     */
    suspend fun decrypt(data: ByteArray, offset: Int, length: Int, startSector: Long): Int {
        checkBounds(data, offset, length)
        if (length == 0 || pairs.isEmpty()) return 0
        val key = keyStore.getKey()
        var sectorIndex: Long
        var cur: Int
        var left: Int

        // По всем парам в обратном порядке (tail -> head), как в C-коде
        for (pair in pairs.asReversed()) {
            cur = offset
            left = length
            sectorIndex = startSector

            while (left > 0) {
                val incr = if (left >= SECTOR_SIZE) SECTOR_SIZE else left
                if (!isBufferAllZero(data, cur, SECTOR_SIZE)) {
                    xtsDecryptSector(pair.cipherA, pair.cipherB, data, cur, incr, sectorIndex, key)
                }
                cur += incr
                left -= incr
                sectorIndex++
            }
        }
        Arrays.fill(key, 0.toByte())
        return 0
    }

    // ---------- НИЖЕ — точные строительные блоки, повторяющие логику app-xts.c ----------

    private fun xtsEncryptSector(
        cipherA: BlockCipher,
        cipherB: BlockCipher,
        buffer: ByteArray,
        off: Int,
        len: Int,
        startSector: Long,
        key: ByteArray
    ) {
        require(len >= 0) { "len must be non-negative" }
        if (len == 0) return
        require(len >= BLOCK) {
            "XTS requires at least one full 16-byte block per non-empty sector chunk (len=$len)"
        }

        val hi = off + len
        var pos = off

        val (keyEncryption, keyTweak) = splitKeys(key)

        // tweak = E_B( little_endian(startSector) || 0^64 )
        val tweak = initialTweak(startSector, cipherB, keyTweak)
        keyTweak.fill(Byte.MIN_VALUE)

        // Полные блоки
        while (pos + BLOCK <= hi) {
            xor16InPlace(buffer, pos, tweak)
            cipherA.encryptBlock(buffer, pos, buffer, pos, keyEncryption)
            xor16InPlace(buffer, pos, tweak)
            pos += BLOCK
            gfMulX(tweak)
        }

        // Частичный хвост (<16) — схема "ciphertext stealing"
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
        keyEncryption.fill(Byte.MIN_VALUE)
    }

    private fun xtsDecryptSector(
        cipherA: BlockCipher,
        cipherB: BlockCipher,
        buffer: ByteArray,
        off: Int,
        len: Int,
        startSector: Long,
        key: ByteArray
    ) {
        require(len >= 0) { "len must be non-negative" }
        if (len == 0) return
        require(len >= BLOCK) {
            "XTS requires at least one full 16-byte block per non-empty sector chunk (len=$len)"
        }

        val (keyEncryption, keyTweak) = splitKeys(key)

        val hi = off + len
        var pos = off

        // tweak = E_B( little_endian(startSector) || 0^64 )
        val tweak = initialTweak(startSector, cipherB, keyTweak)
        keyTweak.fill(Byte.MIN_VALUE)
        val tweak2 = ByteArray(BLOCK)

        // Полные блоки
        while (pos + BLOCK <= hi) {
            val remaining = hi - pos
            if (remaining > BLOCK && remaining < 2 * BLOCK) {
                // Ровно 1 полный блок и хвост — сохраним текущий твик как hh2
                System.arraycopy(tweak, 0, tweak2, 0, BLOCK)
                gfMulX(tweak)
            }
            xor16InPlace(buffer, pos, tweak)
            cipherA.decryptBlock(buffer, pos, buffer, pos, keyEncryption)
            xor16InPlace(buffer, pos, tweak)
            pos += BLOCK
            gfMulX(tweak)
        }

        // Частичный хвост (<16): «распаковываем» украденные байты и дешифруем предпоследний блок с tweak2
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
        keyEncryption.fill(Byte.MIN_VALUE)
    }

    // Tweak = E_B( LBA_le || 0^64 )
    private fun initialTweak(sectorIndex: Long, cipherB: BlockCipher, key: ByteArray): ByteArray {
        val t = ByteArray(BLOCK)
        // little-endian запись 64-бит LBA
        var v = sectorIndex
        for (i in 0 until 8) {
            t[i] = (v and 0xFFL).toByte()
            v = v ushr 8
        }
        // t[8..15] уже 0
        cipherB.encryptBlock(t, 0, t, 0, key) // in-place
        return t
    }

    // Умножение твика на x в GF(2^128) с редукцией по 0x87 (как в app-xts.c ветка UNIT_BITS==8)
    private fun gfMulX(x: ByteArray) {
        require(x.size == BLOCK)
        val msb = (x[15].toInt() and 0x80) != 0
        // Сдвиг влево, перенос младшего бита — из старшего бита предыдущего байта
        for (i in 15 downTo 1) {
            val cur = x[i].toInt() and 0xFF
            val prevMsb = (x[i - 1].toInt() and 0x80) ushr 7
            x[i] = ((cur shl 1) and 0xFE or prevMsb).toByte()
        }
        var b0 = (x[0].toInt() and 0xFF) shl 1
        b0 = b0 and 0xFF
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

    private fun isBufferAllZero(buf: ByteArray, off: Int, len: Int): Boolean {
        var i = 0
        val end = off + len
        while (off + i < end) {
            if (buf[off + i].toInt() != 0) return false
            i++
        }
        return true
    }

    private fun checkBounds(data: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "offset/length out of bounds: off=$offset len=$length data=${data.size}"
        }
    }

    private fun splitKeys(key: ByteArray): Pair<ByteArray, ByteArray> {
        val halfKey = key.size / 2
        val key1 = ByteArray(halfKey)
        System.arraycopy(key, 0, key1, 0, halfKey)
        val key2 = ByteArray(halfKey)
        System.arraycopy(key, halfKey, key2, 0, halfKey)
        return key1 to key2
    }

    companion object {
        const val KEY_SIZE = 64            // 2 * 256-bit ключа для AES-256-XTS
        const val SECTOR_SIZE = 512        // XTS_SECTOR_SIZE
        private const val BLOCK = 16        // BYTES_PER_XTS_BLOCK
    }
}