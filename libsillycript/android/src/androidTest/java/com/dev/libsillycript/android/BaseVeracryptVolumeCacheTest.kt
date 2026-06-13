package com.dev.libsillycript.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dev.exfat.data.FileRandomAccessData
import com.dev.libsillycript.core.VeracryptData
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.factory.UsualFileFactory
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.utils.use
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class BaseVeracryptVolumeCacheTest {

    private lateinit var tempDir: File

    private val appContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        tempDir = appContext.cacheDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun repeatedReadingUsesCache() = runTest {
        // Arrange
        val outerFile = File(tempDir, "container.tc")
        createTestContainerWithKnownPlaintext(
            outerFile = outerFile,
            plainSize = 409600,
            fillByte = 0x11
        )

        AndroidVeracryptMaster(appContext, MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(VeracryptOpeningData(
                    "abc".toCharArray(),
                    KDFType.PBKDF2,
                    listOf(BlockCipherType.AES))
                ),
            )
            .use { volume ->
                val first = ByteArray(128)
                volume.seek(0)
                val firstRead = volume.read(first)

                // Act
                corruptCiphertextBytes(outerFile, position = 0L, length = 512)

                val second = ByteArray(128)
                volume.seek(0)
                val secondRead = volume.read(second)

                // Assert
                assert(firstRead == 128)
                assert(secondRead == 128)
                assert(second.contentEquals(first))
            }
    }

    @Test
    fun cacheIsShared() = runTest {
        // Arrange
        val outerFile = File(tempDir, "container.tc")
        createTestContainerWithKnownPlaintext(
            outerFile = outerFile,
            plainSize = 409600,
            fillByte = 0x33
        )

        val cache = SharedSectorCache()

        AndroidVeracryptMaster(appContext, MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES))),
                cache = cache
            )
            .use { volume1 ->
                val first = ByteArray(128)
                volume1.seek(0)
                volume1.read(first)

                corruptCiphertextBytes(outerFile, position = 131072, length = 512)

                AndroidVeracryptMaster(appContext,MutableStateFlow(100))
                    .openRaw(
                        outerFile,
                        VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES))),
                        cache = cache
                    )
                    .use { volume2 ->
                        val second = ByteArray(128)

                        // Act
                        volume2.seek(0)
                        volume2.read(second)

                        // Assert
                        assert(second.contentEquals(first))
                    }
            }
    }

    @Test
    fun cacheIsCleared() = runTest {
        val outerFile = File(tempDir, "container.tc")
        createTestContainerWithKnownPlaintext(
            outerFile = outerFile,
            plainSize = 4096,
            fillByte = 0x22
        )

        val sectorsCache = SharedSectorCache(maxCachedSectors = 64)

        AndroidVeracryptMaster(appContext, MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(
                    VeracryptOpeningData(
                        "abc".toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES)
                    )
                ),
                sectorsCache
            )
            .use { volume ->
                val before = ByteArray(128)
                volume.seek(0)
                volume.read(before)

                val cachedSector = sectorsCache.cachedSectorKeysSnapshot().first()
                corruptCiphertextBytes(
                    outerFile,
                    position = cachedSector * 512L,
                    length = 512
                )

                sectorsCache.clear()

                val after = ByteArray(128)
                volume.seek(0)

                val result = runCatching { volume.read(after) }

                assert(result.isFailure || !after.contentEquals(before))
            }
    }

    @Test
    fun cacheIsUpdated() = runTest {
        // Arrange
        val outerFile = File(tempDir, "container.tc")
        createTestContainerWithKnownPlaintext(
            outerFile = outerFile,
            plainSize = 409600,
            fillByte = 0x44
        )

        AndroidVeracryptMaster(appContext, MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES))),
            )
            .use { volume ->
                val original = ByteArray(32)
                volume.seek(100)
                volume.read(original)

                val patch = "HELLO".encodeToByteArray()

                // Act
                volume.seek(100)
                volume.write(patch)

                val actual = ByteArray(32)
                volume.seek(100)
                volume.read(actual)

                // Assert
                assert(actual.copyOfRange(0, patch.size).contentEquals(patch))
                assert(actual.copyOfRange(patch.size, actual.size)
                    .contentEquals(original.copyOfRange(patch.size, original.size)))
            }
    }

    @Test
    fun partialWriteChangesCache() = runTest {
        // Arrange
        val outerFile = File(tempDir, "container.tc")
        createTestContainerWithKnownPlaintext(
            outerFile = outerFile,
            plainSize = 409600,
            fillByte = 0x55
        )

        AndroidVeracryptMaster(appContext, MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES))),
            )
            .use { volume ->
                val before = ByteArray(512)
                volume.seek(0)
                volume.read(before)

                val patch = byteArrayOf(1, 2, 3, 4, 5, 6)
                val patchOffset = 200L

                // Act
                volume.seek(patchOffset)
                volume.write(patch)

                val after = ByteArray(512)
                volume.seek(0)
                volume.read(after)

                // Assert
                assert(after.copyOfRange(0, patchOffset.toInt())
                    .contentEquals(before.copyOfRange(0, patchOffset.toInt())))
                assert(after.copyOfRange(patchOffset.toInt(), patchOffset.toInt() + patch.size)
                    .contentEquals(patch))
                assert(after.copyOfRange(patchOffset.toInt() + patch.size, 512)
                    .contentEquals(before.copyOfRange(patchOffset.toInt() + patch.size, 512)))
            }
    }

    private fun corruptCiphertextBytes(file: File, position: Long, length: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(position)
            repeat(length) {
                val old = raf.read()
                raf.seek(raf.filePointer - 1)
                raf.write((old xor 0xFF) and 0xFF)
            }
        }
    }

    private suspend fun createTestContainerWithKnownPlaintext(
        outerFile: File,
        plainSize: Int,
        fillByte: Byte
    ) {
        AndroidVeracryptMaster(appContext, MutableStateFlow(100)).createRaw(
            factory = UsualFileFactory(outerFile),
            listOf(VeracryptData(
                plainSize.toLong(),
                VeracryptOpeningData(
                    "abc".toCharArray(),
                    KDFType.PBKDF2,
                    listOf(BlockCipherType.AES),
                ),
                FsType.ExFAT,
                0
            )
            ),
        )

        AndroidVeracryptMaster(appContext,MutableStateFlow(100))
            .openRaw(
                outerFile,
                VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES)
            )))
            .use { volume ->
                val data = ByteArray(volume.size.toInt()) { fillByte }
                volume.seek(0)
                volume.write(data)
            }
    }


}