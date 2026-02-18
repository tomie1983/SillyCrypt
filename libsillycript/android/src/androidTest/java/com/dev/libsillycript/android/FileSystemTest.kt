package com.dev.libsillycript.android

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dev.exfat.exfat.ExFATVeracryptFS
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.utils.use
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.Security

@RunWith(AndroidJUnit4::class)
class FileSystemTest {
    companion object {

        private const val EXPECTED_OUTER_HASH  =
            "35d248353b6ec70320d7e69ea521f0bcd6102312ceb5d2173ba1a9accef2e3df49eaacb45011c31b01a349bb028d8f7f0e1d3448a4e4acb3b0ddf0077e5d175e"
        private lateinit var appContext: Context
        private lateinit var normalFile: File
        private lateinit var secondFile: File

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
        normalFile = appContext.copyAssetToFiles("test3")
        secondFile = appContext.copyAssetToFiles("test")
        check(normalFile.exists()) { "Missing asset: ${normalFile.path}" }
    }

    @Test
    fun openOuterVolume_hashMatches() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            normalFile,
            "abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)).use { vol ->
            val actual = vol.getHashCode()
            Log.w("result",actual)
            assertEquals("SHA-512 hash mismatch for outer volume", EXPECTED_OUTER_HASH, actual)
        }
    }

    @Test
    fun checkFileSystemCorrect() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            normalFile,
            "abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)).use { vol ->
            val fileSystem = ExFATVeracryptFS(vol)
            assert(fileSystem.isExFat())
            Log.w("FS metadata", fileSystem.readFullFileSystemMetadata().toString())
            fileSystem.listRoot().forEach {
                Log.w("File name ${it.name}","${it.dataLength} ${it.isDirectory}")
            }
        }
    }

    @Test
    fun checkFileSystemIncorrect() = runTest {
        AndroidVeracryptMaster(MutableStateFlow(100)).openRaw(
            secondFile,
            "abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES)).use { vol ->
            val fileSystem = ExFATVeracryptFS(vol)
            assert(!fileSystem.isExFat())
        }
    }




}