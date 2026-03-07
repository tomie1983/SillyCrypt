package com.dev.libsillycript.core

import com.dev.libsillycript.core.blockCiphers.BlockCipherFactory
import com.dev.libsillycript.core.blockCiphers.BlockCipherFactoryImpl
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FSFactory
import com.dev.libsillycript.core.fs.FSFactoryImpl
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFFactory
import com.dev.libsillycript.core.kdfs.KDFFactoryImpl
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.keyStore.KeyStoreFactory
import com.dev.exfat.data.FileRandomAccessData
import com.dev.exfat.data.MemoryRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.concat
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.factory.VeracryptFileFactory
import com.dev.libsillycript.core.factory.VeracryptMemoryFactory
import com.dev.libsillycript.core.utils.beLong
import com.dev.libsillycript.core.utils.use
import com.dev.libsillycript.core.utils.writeIntBE
import com.dev.libsillycript.core.utils.writeLongBE
import com.dev.libsillycript.core.utils.writeShortBE
import com.dev.libsillycript.core.utils.writeStringLE
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume
import com.dev.libsillycript.core.volumes.EncryptionData
import com.dev.libsillycript.core.xts.XTSNew
import jdk.internal.net.http.common.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.CRC32

open class VeraCryptMaster(
    private val keyStoreFactory: KeyStoreFactory,
    private val safeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val blockCipherFactory: BlockCipherFactory = BlockCipherFactoryImpl(),
    private val kdfFactory: KDFFactory = KDFFactoryImpl(),
    private val fsFactory: FSFactory = FSFactoryImpl()
) {

    companion object {

        private const val HEADER_SIZE = 512
        private const val SECTOR_SIZE = 512
        private const val HEADER_SALT_SIZE = 64
        private const val XTS_KEY_LENGTH = 32
        private const val SIGNATURE_START = 64
        private const val SIZE_DATA = 100
        private const val OFFSET_DATA = 108
        private const val LENGTH_DATA = 116
        private const val FULL_HEADER_SIZE = 65536L
        private const val MASTER_KEY_OFFSET = 256
        private const val SECOND_KEY_OFFSET = 288
        private const val BLOCK_SIZE = 4096
        private const val FORMAT_VERSION: Short = 5
        private const val PROGRAM_VERSION: Short = 267
        private const val FORMAT_OFFSET = 4
        private const val VERSION_OFFSET = 6
        private const val HIDDEN_SIZE_OFFSET = 28
        private const val SECTOR_SIZE_OFFSET = 128
        private const val FIRST_CRC_OFFSET = 72
        private const val SECOND_CRC_OFFSET = 252
    }

    private val rnd = SecureRandom()

    suspend fun create(
        file: File,
        size: Long,
        password: CharArray,
        ciphers: List<BlockCipherType>,
        kdf: KDFType,
        hiddenSize: Long? = null,
        hiddenPassword: CharArray? = null
    ) {
        create(
            FileRandomAccessData(file),
            size,
            password,
            ciphers,
            kdf,
            hiddenSize,
            hiddenPassword)
    }

    suspend fun create(
        output: RandomAccessData,
        size: Long,
        password: CharArray,
        ciphers: List<BlockCipherType>,
        kdf: KDFType,
        hiddenSize: Long? = null,
        hiddenPassword: CharArray? = null
    ) {
        withContext(safeDispatcher) {
            createUnsafe(output, size, password, ciphers, kdf, hiddenSize, hiddenPassword)
        }
    }

    private suspend fun createUnsafe(
        output: RandomAccessData,
        size: Long,
        password: CharArray,
        ciphers: List<BlockCipherType>,
        kdf: KDFType,
        hiddenSize: Long? = null,
        hiddenPassword: CharArray? = null
    ) {
        /* ── 0. Validation ───────────────────────────────────────────────────── */
        require(size > 2 * FULL_HEADER_SIZE) { "Container is too small" }
        val sizeChanged = size + (BLOCK_SIZE - size % BLOCK_SIZE)

        if (hiddenSize != null) {
            require(hiddenPassword != null) { "Hidden volume password absent" }
            require(hiddenSize > FULL_HEADER_SIZE) { "hidden volume is too small" }
            require(hiddenSize < sizeChanged - FULL_HEADER_SIZE) {
                "There is no enough space for hidden volume"
            }
        }
        /* ── 1. Outer volume header ─────────────────────────────────────────── */
        val outerHeader = buildHeader(
            dataOffset = FULL_HEADER_SIZE * 2L,
            dataSize = sizeChanged - 4 * FULL_HEADER_SIZE,
            password = password,
            kdf = kdf,
            cipherTypes = ciphers,
            isHidden = false
        )

        /* ── 2. Hidden volume header (if needed) ───────────────────────────── */
        val hiddenHeader: ByteArray? = if (hiddenPassword != null && hiddenSize != null) {
            val hiddenOffset =
                sizeChanged - 2L * FULL_HEADER_SIZE - hiddenSize - BLOCK_SIZE  // start of hidden volume data
            buildHeader(
                dataOffset = hiddenOffset,
                dataSize = hiddenSize,
                password = hiddenPassword,
                kdf = kdf,
                cipherTypes = ciphers,
                isHidden = true
            )
        } else {
            null
        }

        /* ── 3. Write everything SEQUENTIALLY to the output ─────────────────── */
        output.use { out ->
            out.write(outerHeader)                                      // 512 bytes
            writeRandom(out, HEADER_SIZE.toLong(), FULL_HEADER_SIZE - HEADER_SIZE, ciphers)
            if (hiddenHeader != null) {
                out.write(hiddenHeader)
            } else {
                writeRandom(out, FULL_HEADER_SIZE, HEADER_SIZE.toLong(), ciphers)
            }
            writeRandom(
                out,
                FULL_HEADER_SIZE + HEADER_SIZE,
                sizeChanged - FULL_HEADER_SIZE - HEADER_SIZE,
                ciphers
            )
        }
    }

    private suspend fun writeRandom(
        output: RandomAccessData,
        offset: Long,
        size: Long,
        cipherTypes: List<BlockCipherType>
    ) {
        val randomKey = ByteArray(XTS_KEY_LENGTH * 2)
        rnd.nextBytes(randomKey)
        val keyStore = keyStoreFactory.get()
        keyStore.setKey(randomKey)
        val xts = XTSNew(keyStore, buildCipherPairsList(cipherTypes))
        var outPos = 0
        var sectorIndex = offset / SECTOR_SIZE    // index of the first sector
        val sectorBuf = ByteArray(SECTOR_SIZE)
        while (outPos < size) {
            rnd.nextBytes(sectorBuf)
            xts.encrypt(sectorBuf, 0, SECTOR_SIZE, sectorIndex)
            output.write(sectorBuf)
            outPos += SECTOR_SIZE
            sectorIndex++
        }
        //clear key
        randomKey.fill(Byte.MIN_VALUE)
    }

    @Throws(GeneralSecurityException::class)
    private suspend fun buildHeader(
        dataOffset: Long,
        dataSize: Long,
        password: CharArray,
        isHidden: Boolean,
        kdf: KDFType,
        cipherTypes: List<BlockCipherType>
    ): ByteArray {

        /* 1. Generate salt + header encryption key */
        val salt = ByteArray(HEADER_SALT_SIZE).also { rnd.nextBytes(it) }
        val headerKey = kdfFactory.create(kdf).derive(
            password, salt
        )                                   // 64 bytes
        //clearing password
        password.fill(Char(0))
        /* 2. Generate volume master keys (64 bytes) */
        val masterKey1 = ByteArray(XTS_KEY_LENGTH).also(rnd::nextBytes)
        val masterKey2 = ByteArray(XTS_KEY_LENGTH).also(rnd::nextBytes)
        /* 3. Build the ‘plaintext’ header section (448 bytes after salt) */
        val headerData = ByteArray(HEADER_SIZE - HEADER_SALT_SIZE)
        headerData.writeStringLE(0, "VERA")                 // signature
        // optionally write format version (2 bytes) and minor version (2 bytes)
        headerData.writeShortBE(FORMAT_OFFSET, FORMAT_VERSION)
        headerData.writeShortBE(VERSION_OFFSET, PROGRAM_VERSION)
        if (isHidden) {
            headerData.writeLongBE(HIDDEN_SIZE_OFFSET, dataSize)
        }
        headerData.writeLongBE(SIZE_DATA - HEADER_SALT_SIZE, dataSize)
        // offsets and sizes (Big-Endian)
        headerData.writeLongBE(OFFSET_DATA - HEADER_SALT_SIZE, dataOffset)
        headerData.writeLongBE(LENGTH_DATA - HEADER_SALT_SIZE, dataSize)
        headerData.writeIntBE(SECTOR_SIZE_OFFSET - HEADER_SALT_SIZE, SECTOR_SIZE)
        // insert the master keys
        System.arraycopy(
            masterKey1,
            0,
            headerData,
            MASTER_KEY_OFFSET - HEADER_SALT_SIZE,
            XTS_KEY_LENGTH
        )
        System.arraycopy(
            masterKey2,
            0,
            headerData,
            SECOND_KEY_OFFSET - HEADER_SALT_SIZE,
            XTS_KEY_LENGTH
        )
        val firstCRCArray = ByteArray(HEADER_SIZE - MASTER_KEY_OFFSET).apply {
            System.arraycopy(headerData, MASTER_KEY_OFFSET - HEADER_SALT_SIZE, this, 0, size)
        }
        val crc32 = CRC32()
        crc32.update(firstCRCArray)
        headerData.writeIntBE(FIRST_CRC_OFFSET - HEADER_SALT_SIZE, crc32.value.toInt())
        val secondCRCArray = ByteArray(SECOND_CRC_OFFSET - HEADER_SALT_SIZE).apply {
            System.arraycopy(headerData, 0, this, 0, size)
        }
        crc32.reset()
        crc32.update(secondCRCArray)
        headerData.writeIntBE(SECOND_CRC_OFFSET - HEADER_SALT_SIZE, crc32.value.toInt())
        crc32.reset()
        /* 4. Encrypt the plaintext header with AES-XTS-512 using tweak = 0 (sector 0) */
        encryptHeader(headerData, headerKey, cipherTypes)
        //clear data
        headerKey.fill(Byte.MIN_VALUE)
        masterKey1.fill(Byte.MIN_VALUE)
        masterKey2.fill(Byte.MIN_VALUE)
        /* 5. Combine salt + encrypted header = 512-byte final header */
        return ByteArray(HEADER_SIZE).apply {
            System.arraycopy(salt, 0, this, 0, HEADER_SALT_SIZE)
            System.arraycopy(headerData, 0, this, HEADER_SALT_SIZE, headerData.size)
        }
    }

    private suspend fun encryptHeader(
        header: ByteArray,
        headerKey: ByteArray,
        cipherTypes: List<BlockCipherType>
    ) {
        val keyStore = keyStoreFactory.get()
        keyStore.setKey(headerKey)
        val xts = XTSNew(keyStore, buildCipherPairsList(cipherTypes))
        xts.encrypt(header, 0, header.size, 0)
        xts.close()
    }

    suspend fun open(
        input: ByteArray,
        password: CharArray,
        kdf: KDFType,
        fsType: FsType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false
    ): String {
        return withContext(safeDispatcher) {
            val data  = openUnsafeRaw(MemoryRandomAccessData(input), password, kdf, cipherTypes, isHidden)
            val volumeFactory = VeracryptMemoryFactory(input, data)
            return@withContext fsFactory.create(fsType, volumeFactory)
        }
    }

    suspend fun open(
        input: File,
        password: CharArray,
        kdf: KDFType,
        fsType: FsType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false): String {
        return withContext(safeDispatcher) {
            val data  = openUnsafeRaw(FileRandomAccessData(input), password, kdf, cipherTypes, isHidden)
            val volumeFactory = VeracryptFileFactory(input, data)
            return@withContext fsFactory.create(fsType, volumeFactory)
        }
    }

    suspend fun openRaw(
        input: ByteArray,
        password: CharArray,
        kdf: KDFType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false,
        cache: SharedSectorCache = SharedSectorCache()
    ): BaseVeracryptVolume {
        return openRaw(MemoryRandomAccessData(input), password, kdf, cipherTypes, isHidden, cache)
    }

    suspend fun openRaw(
        input: File,
        password: CharArray,
        kdf: KDFType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false,
        cache: SharedSectorCache = SharedSectorCache()
    ): BaseVeracryptVolume {
        return openRaw(FileRandomAccessData(input), password, kdf, cipherTypes, isHidden, cache)
    }

    /** Open the volume and return the *entire* decrypted payload without headers */
    @Throws(IOException::class, GeneralSecurityException::class)
    suspend fun openRaw(
        input: RandomAccessData,
        password: CharArray,
        kdf: KDFType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false,
        cache: SharedSectorCache = SharedSectorCache()
    ): BaseVeracryptVolume {
        return withContext(safeDispatcher) {
            BaseVeracryptVolume(input, cache, openUnsafeRaw(input, password, kdf, cipherTypes, isHidden))
        }
    }

    private suspend fun openUnsafeRaw(
        input: RandomAccessData,
        password: CharArray,
        kdf: KDFType,
        cipherTypes: List<BlockCipherType>,
        isHidden: Boolean = false
    ): EncryptionData {

        /* 0. For a hidden volume, skip the secondary (backup) header */
        if (isHidden) input.seek(FULL_HEADER_SIZE)

        /* 1. Read the standard header */
        val header = ByteArray(HEADER_SIZE)
        input.read(header)

        /* 2. Extract salt + derive PBKDF2 key */
        val salt = header.copyOfRange(0, HEADER_SALT_SIZE)
        val headerKey = kdfFactory.create(kdf).derive(
            password, salt
        )
        //clear password
        password.fill(Char(0))

        /* 3. Decrypt the header */
        val decryptedHeader = decryptHeader(header, headerKey, cipherTypes)
        //clear key
        headerKey.fill(Byte.MIN_VALUE)
        if (!decryptedHeader.copyOfRange(SIGNATURE_START, SIGNATURE_START + 4)
                .contentEquals(
                    byteArrayOf(
                        'V'.code.toByte(), 'E'.code.toByte(),
                        'R'.code.toByte(), 'A'.code.toByte()
                    )
                )
        ) throw SecurityException("Неверный пароль или файл не VeraCrypt")

        /* 4. Read volume parameters */
        var dataOffset = beLong(decryptedHeader, OFFSET_DATA)
        if (dataOffset == 0L) dataOffset = 2L * FULL_HEADER_SIZE           // default 0x20000

        val dataSize = beLong(decryptedHeader, LENGTH_DATA)
        if (dataSize == 0L)
            throw IOException("LENGTH_DATA=0")
        /* 5. Retrieve XTS master keys */
        val mKey1 = decryptedHeader.copyOfRange(
            MASTER_KEY_OFFSET,
            MASTER_KEY_OFFSET + XTS_KEY_LENGTH
        )
        val mKey2 = decryptedHeader.copyOfRange(
            SECOND_KEY_OFFSET,
            SECOND_KEY_OFFSET + XTS_KEY_LENGTH
        )
        val xtsKey = concat(mKey1, mKey2)                                // 64 bytes
        /* 6. Seek to the first encrypted data region */
        val bytesAlreadyRead = input.position                           // = HEADER_SIZE (+ FULL_HEADER if skipped)
        input.seek(dataOffset)
        /* 7. Decrypt the payload data */
        val keyStore = keyStoreFactory.get()
        keyStore.setKey(xtsKey)
        val xts = XTSNew(keyStore, buildCipherPairsList(cipherTypes))
        return EncryptionData(xts, dataOffset, dataSize, SECTOR_SIZE)
    }

    /* --- Decrypt the header section (448 bytes) --- */
    @Throws(GeneralSecurityException::class)
    private suspend fun decryptHeader(
        header: ByteArray,
        headerKey: ByteArray,
        cipherTypes: List<BlockCipherType>
    ): ByteArray {
        val keyStore = keyStoreFactory.get()
        keyStore.setKey(headerKey)
        val xts = XTSNew(keyStore, buildCipherPairsList(cipherTypes))
        val cipherPart: ByteArray = header.copyOfRange(HEADER_SALT_SIZE, HEADER_SIZE)
        xts.decrypt(cipherPart, 0, cipherPart.size, 0)
        // reassemble salt + decrypted data
        val full = ByteArray(HEADER_SIZE)
        System.arraycopy(header, 0, full, 0, HEADER_SALT_SIZE)
        System.arraycopy(cipherPart, 0, full, HEADER_SALT_SIZE, cipherPart.size)
        return full
    }

    private fun buildCipherPairsList(types: List<BlockCipherType>): List<XTSNew.CipherPair> {
        return types.map {
            XTSNew.CipherPair(blockCipherFactory,it)
        }
    }
}