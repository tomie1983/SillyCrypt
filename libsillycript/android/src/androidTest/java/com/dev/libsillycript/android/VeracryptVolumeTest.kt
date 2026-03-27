package com.dev.libsillycript.android

import org.junit.runner.RunWith
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.utils.use
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.Security
import kotlin.random.Random
import kotlin.ranges.coerceAtMost

@RunWith(AndroidJUnit4::class)
class VeracryptVolumeTest {

    companion object {
        private const val EXPECTED_NORMAL_HASH =
            "dc6a85c075343b36b3a6289c152427ddcc430168827064eb867e09721310e39b9dc0fe15597d686bac504d8cb47f40c967b15ccc7a9b8e28574928519bfcf511" // test  (outer)
        private const val EXPECTED_OUTER_HASH  =
            "5e3073514879065f280effe8d58d3f3e5a4edb410765773842c254b8528f191411b5b22391c4fb300552b96519020b249e7a72d73738c98c42c96c01f383f0d6" // test2 (outer)
        private const val EXPECTED_HIDDEN_HASH =
            "e114e13dd8756afb830bda50331d7040c5d99fbd28a93f72aed3517f978f7b9cf60f5bd495a458e3a01419b679573d70bde3794e0528ffc79cb95d69ab3e3968" // test2 (hidden)

        private lateinit var appContext: Context
        private lateinit var normalFile: File
        private lateinit var outerFile: File

        private fun Context.copyAssetToFiles(name: String): File {
            val dest = File(filesDir, name)
            assets.open(name).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return dest
        }

        @BeforeClass
        @JvmStatic
        fun oneTimeSetup() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @Before
    fun setUp() {
        appContext =InstrumentationRegistry.getInstrumentation().targetContext

        // Копируем тестовые контейнеры из assets в приватную папку
        normalFile = appContext.copyAssetToFiles("test")
        outerFile  = appContext.copyAssetToFiles("test2")

        check(normalFile.exists()) { "Missing asset: ${normalFile.path}" }
        check(outerFile.exists())  { "Missing asset: ${outerFile.path}" }
    }

    // ---------- POSITIVE CASES ----------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun openNormalVolume_hashMatches() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            normalFile,
            VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))).use { vol ->
            Log.w("hashcode", "start")
            val actual = vol.getHashCode()
            Log.w("hashcode", "end")
            assertEquals("SHA-512 hash mismatch for normal volume", EXPECTED_NORMAL_HASH, actual)
        }
    }

    @Test
    fun openOuterVolume_hashMatches() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            outerFile,
            VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))).use { vol ->
            val actual = vol.getHashCode()
            assertEquals("SHA-512 hash mismatch for outer volume", EXPECTED_OUTER_HASH, actual)
        }
    }

    @Test
    fun openHiddenVolume_hashMatches() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            outerFile,
            VeracryptMode.OpenHidden(VeracryptOpeningData("abcd".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))
        ).use { vol ->
            val actual = vol.getHashCode()
            assertEquals("SHA-512 hash mismatch for hidden volume", EXPECTED_HIDDEN_HASH, actual)
        }
    }

    // ---------- NEGATIVE / ERROR CASES --------------------------------------------------

    @Test(expected = SecurityException::class)
    fun wrongPassword_throwsSecurityException() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            normalFile,
            VeracryptMode.OpenNormal(VeracryptOpeningData("wrong".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)))
        ).close()
    }

    // ---------- SEEK / WRITE / READ ROUND-TRIP -----------------------------------------

    @Test
    fun seek_write_read_roundTrip() = runTest {
        val tmp = File.createTempFile("vc_copy", null, appContext.cacheDir)
        try {
            normalFile.copyTo(tmp, overwrite = true)

            AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
                tmp,
                VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
                KDFType.PBKDF2,
                listOf(BlockCipherType.AES)))).use { vol ->
                val sectorSize = vol.size.coerceAtMost(4096).toInt()
                val dataToWrite = Random.nextBytes(128)
                val offset = 2L * sectorSize

                vol.seek(offset)
                vol.write(dataToWrite)

                val readBack = ByteArray(dataToWrite.size)
                vol.seek(offset)
                vol.read(readBack)

                assertArrayEquals("Data read back differs from data written", dataToWrite, readBack)
            }
        } finally {
            tmp.delete()
        }
    }
}