package com.dev.libsillycript.core

import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.factory.UsualFileFactory
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.keyStore.KeyStoreFactoryUnsafeImpl
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.utils.use
import junit.framework.TestCase
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.Security
import kotlin.random.Random


class VeracryptCreateTest {

    private lateinit var scratch: File // temp container for each test

    companion object {
        private const val OUTER_PWD = "pwd"
        private const val HIDDEN_PWD = "hidden"
        private const val SECOND_HIDDEN_PWD = "hidden2"

        @BeforeClass
        @JvmStatic
        fun setupBc() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @Before
    fun setUp() {
        val rootDir = System.getProperty("user.dir")
        val dir = File("$rootDir/src/test/assets")
        scratch = File.createTempFile("vc_copy", null, dir)
        scratch.delete() // createTempFile touched FS — remove and create clean on write
    }

    @After
    fun tearDown() {
        if (this::scratch.isInitialized) scratch.delete()
    }

    // ------------------------------------------------------------------------
    // 1.  Outer-only volume   (5 MB)
    // ------------------------------------------------------------------------
    @Test
    fun createOuterVolume_thenReadWrite() = runTest {
        val sizeBytes = 5 * 1024 * 1024L // 5 MB

        // 1) create ----------------------------------------------------------

        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).createRaw(
            factory = UsualFileFactory(scratch),
            data = listOf(
                VeracryptData(
                    size = sizeBytes,
                    veracryptOpeningData = VeracryptOpeningData(
                        OUTER_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    FsType.ExFAT,
                    0
                )
            )
        )

        assertTrue("Container not written", scratch.length() > 0)

        // 2) open & verify read/write ---------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenNormal(VeracryptOpeningData(OUTER_PWD.toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))
        ).use { vol ->
            // basic invariants
            assertTrue("volume.size must be <= container size", vol.size <= sizeBytes)
            TestCase.assertEquals("Initial position not 0", 0, vol.position)

            // round-trip at offset 1 MiB to avoid headers
            val offset = 1 * 1024 * 1024L
            val data = Random.nextBytes(1024) // 1 KiB random block

            vol.seek(offset)
            vol.write(data)

            val back = ByteArray(data.size)
            vol.seek(offset)
            vol.read(back)

            assertArrayEquals("Data mismatch after round-trip", data, back)
        }
    }

    // ------------------------------------------------------------------------
    // 2.  Outer + Hidden      (outer 10 MB, hidden 2 MB)
    // ------------------------------------------------------------------------
    @Test
    fun createVolumeWithHidden_thenReadWrite() = runTest {
        val outerSize = 10 * 1024 * 1024L // total container size
        val hiddenSize = 2 * 1024 * 1024L // dedicated hidden payload

        // 1) create ----------------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).createRaw(
            factory = UsualFileFactory(scratch),
            data = listOf(
                VeracryptData(
                    size = outerSize,
                    veracryptOpeningData = VeracryptOpeningData(
                        OUTER_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    FsType.ExFAT,
                    0
                ),
                VeracryptData(
                    size = hiddenSize,
                    veracryptOpeningData = VeracryptOpeningData(
                        HIDDEN_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    fsType = FsType.ExFAT,
                    HIDDEN_HEADER_DEFAULT_INDEX
                )
            ),
        )


        // 2-A) open *outer* --------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenNormal(VeracryptOpeningData(OUTER_PWD.toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))).use { outer ->
            val txt = "Hello outer!".toByteArray()
            outer.seek(512) // keep far away from hidden
            outer.write(txt)
            val read = ByteArray(txt.size)
            outer.seek(512)
            outer.read(read)
            assertArrayEquals("Outer round-trip failed", txt, read)
        }

        // 2-B) open *hidden* -------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenHidden(VeracryptOpeningData(HIDDEN_PWD.toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))
        ).use { hidden ->
            val txt = "Hello hidden!".toByteArray()
            hidden.seek(0) // safe – hidden header lies before this
            hidden.write(txt)

            val read = ByteArray(txt.size)
            hidden.seek(0)
            hidden.read(read)
            assertArrayEquals("Hidden round-trip failed", txt, read)
        }
    }

    // ------------------------------------------------------------------------
    // 2.  Outer + Hidden      (outer 10 MB, hidden 2 MB)
    // ------------------------------------------------------------------------
    @Test
    fun createAdditionalHiddenVolume_thenReadWrite() = runTest {
        val outerSize = 10 * 1024 * 1024L // total container size
        val hiddenSize = 2 * 1024 * 1024L // dedicated hidden payload
        val secondHiddenSize = 1 * 1024 * 1024L

        // 1) create ----------------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).createRaw(
            factory = UsualFileFactory(scratch),
            data = listOf(
                VeracryptData(
                    size = outerSize,
                    veracryptOpeningData = VeracryptOpeningData(
                        OUTER_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    FsType.ExFAT,
                    0
                ),
                VeracryptData(
                    size = hiddenSize,
                    veracryptOpeningData = VeracryptOpeningData(
                        HIDDEN_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    fsType = FsType.ExFAT,
                    HIDDEN_HEADER_DEFAULT_INDEX
                ),
                VeracryptData(
                    size = secondHiddenSize,
                    veracryptOpeningData = VeracryptOpeningData(
                        SECOND_HIDDEN_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    fsType = FsType.ExFAT,
                    1
                )
            ),
        )


        // 2-A) open *outer* --------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenNormal(VeracryptOpeningData(OUTER_PWD.toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES)))).use { outer ->
            val txt = "Hello outer!".toByteArray()
            outer.seek(512) // keep far away from hidden
            outer.write(txt)
            val read = ByteArray(txt.size)
            outer.seek(512)
            outer.read(read)
            assertArrayEquals("Outer round-trip failed", txt, read)
        }

        // 2-B) open *hidden* -------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenHidden(VeracryptOpeningData(HIDDEN_PWD.toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES)))
        ).use { hidden ->
            val txt = "Hello hidden!".toByteArray()
            hidden.seek(0) // safe – hidden header lies before this
            hidden.write(txt)

            val read = ByteArray(txt.size)
            hidden.seek(0)
            hidden.read(read)
            assertArrayEquals("Hidden round-trip failed", txt, read)
        }

        // 2-B) open second *hidden* -------------------------------------------------
        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenHidden(VeracryptOpeningData(SECOND_HIDDEN_PWD.toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES)),1)
        ).use { hidden ->
            val txt = "Hello hidden 2!".toByteArray()
            hidden.seek(0) // safe – hidden header lies before this
            hidden.write(txt)

            val read = ByteArray(txt.size)
            hidden.seek(0)
            hidden.read(read)
            assertArrayEquals("Hidden round-trip failed", txt, read)
        }
    }

    // ------------------------------------------------------------------------
    // 3.  Boundary check – writing beyond volume must throw
    // ------------------------------------------------------------------------
    @Test(expected = RuntimeException::class)
    fun writeBeyondEnd_throws() = runTest {
        val sizeBytes = 2 * 1024 * 1024L

        // create 2 MB outer-only container

        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).createRaw(
            factory = UsualFileFactory(scratch),
            data = listOf(
                VeracryptData(sizeBytes,
                    VeracryptOpeningData(
                        OUTER_PWD.toCharArray(),
                        KDFType.PBKDF2,
                        listOf(BlockCipherType.AES),
                    ),
                    FsType.ExFAT,
                    0
                )
            )
        )


        VeraCryptMaster(KeyStoreFactoryUnsafeImpl()).openRaw(
            scratch,
            VeracryptMode.OpenNormal(VeracryptOpeningData(OUTER_PWD.toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))).use { vol ->
            vol.seek(vol.size - 512)
            val overflow = ByteArray(1024) // deliberately crosses boundary
            vol.write(overflow)            // should throw RuntimeException
        }
    }
}