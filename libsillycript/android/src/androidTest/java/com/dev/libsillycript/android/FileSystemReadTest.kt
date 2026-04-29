package com.dev.libsillycript.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.file.ExFATFile
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycript.core.utils.use
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
class FileSystemReadTest {
    companion object {

        private val mapOfHashes: Map<String, String> = buildMap {
            put("/Raptile feat. Da Liones - Hands up (Форсаж 6).mp3", "7c2e9c92ddaf01212371604a611e1a5280654b551bc440a447263c1b820432af43c5fcdcb5d6ca92017767332b71cac034672a67ce1c3f5e800e7d9fb5a6cc1d")
            put("/Need For Speed Undecover - Музыка из игры.mp3", "779bf3f63a475bb128c0e07bee48cea3b9b52ff32403d5cab694117fdf24fe15e9c3ec34e0487b151196a86fdd1345adbcab36dfaee735469f1ba55119557c18")
            put("/test2/Celdweller - Mindfreak.mp3","98cad4b8546042a4b936ed26fca6c8b03074fd5149009301431bbd11c26eaf6ab6d2d359034134bab1467463699263569ca38d4181016cfc344442786f903bdf")
            put("/test2/test/Hollywood Undead - Everywhere I Go.mp3", "09ccfd4db499ad6b14b1df65c0cd87c68cbe44e721b990d03cfc1f8b9d06909cd4b6f53c85a3cf0e94270299cd8dfd95b6c041e3cb0ad1a3e49fd708c7945aec")
            put("/test2/test/Сыендук - ЗАДРОТСКАЯ ЯРОСТЬ.mp3","fe256dcae5d3c9f978cf6afcbb95931e8c2d3bb2a6e6bc571cc5e4f1d6cf026da6fae7caeb9e817fab0a64eb65f70000c441b7000d5ac1ed3208baad5d29cbc5")
            put("/test/Raptile feat. Da Liones - Hands up (Форсаж 6).mp3","7c2e9c92ddaf01212371604a611e1a5280654b551bc440a447263c1b820432af43c5fcdcb5d6ca92017767332b71cac034672a67ce1c3f5e800e7d9fb5a6cc1d")
            put("/test/Эпичная музыка - из игры Need for speed _ the run.mp3","38eb638024ad6c958e7ea33909e3065f9c402358e0234b63c03de96b6aba341890ccd2006dfa6256a418630f5516f88fd101c10a94cc2f806200facf52171e68")
            put("/test/Linkin Park - One Step Closer.mp3","4045b0e790a304bc29b3e99d584cee2c87ea00428d2c78597c9a90f2d3e132ae48cf3a6e6c329b828b769fe3e78a77583b4cf28797fcce3e5bae46a11646a27b")
            put("/test/test/test/Skillet - Whispers In The Dark Nightcore Mix.mp3","679e4611210f632f0bf4c4b6999ad09e72eed8b0bab9f024cee932c7036fe2425bf7f61172cb00acf02b4369f215109876778bd8770a32adbccd172dc464fc93")
        }

        private val directories: List<String> = listOf(
            "/test2", "/test",
            "/test2/test2", "/test2/test",
            "/test/test", "/test/test/test",
            "/.Trash-1000", "/.Trash-1000/info", "/.Trash-1000/files"
        )

        private lateinit var appContext: Context
        private lateinit var normalFile: File
        private lateinit var secondFile: File

        private lateinit var mainFIle: File

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
        mainFIle = appContext.copyAssetToFiles("test4")
        check(normalFile.exists()) { "Missing asset: ${normalFile.path}" }
    }

    @Test
    fun openOuterVolume_hashMatches() = runTest {
        val fs = AndroidVeracryptMaster(appContext,MutableStateFlow(100)).open(
            normalFile,
            VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES))),
            FsType.ExFAT, "test")
            EXFatVolumesManager.get(fs).getFileFromPath("/").use { file ->
            println(file == null)
            println(file?.isDirectory)
            file?.listFiles()?.forEach {
                println(it.path)
            }
        }
    }

    @Test
    fun checkFileSystemContent() = runTest(timeout = Duration.INFINITE) {
        // Arrange
        val fs = AndroidVeracryptMaster(appContext, MutableStateFlow(100)).open(
            mainFIle,
            VeracryptMode.OpenNormal(VeracryptOpeningData("abc".toCharArray(),
            KDFType.PBKDF2,
            listOf(BlockCipherType.AES))),
            FsType.ExFAT,
            "test"
        )

        val actualDirectories = linkedSetOf<String>()
        val actualHashes = linkedMapOf<String, String>()

        // Act
        EXFatVolumesManager
            .get(fs)
            .getFileFromPath("/")?.use { root ->
            collectTree(
                file = root,
                directoriesOut = actualDirectories,
                fileHashesOut = actualHashes
            )

            // Assert: expected directories exist
            val expectedDirectories = directories.toSet()
            assertTrue(
                "Missing directories: ${expectedDirectories - actualDirectories}\nActual: $actualDirectories",
                actualDirectories.containsAll(expectedDirectories)
            )

            // Assert: expected file paths exist
            val expectedFilePaths = mapOfHashes.keys
            val actualFilePaths = actualHashes.keys.toSet()
            assertTrue(
                "Missing files: ${expectedFilePaths - actualFilePaths}\nActual: $actualFilePaths",
                actualFilePaths.containsAll(expectedFilePaths)
            )

            // Assert: hashes match
            for ((rawPath, expectedHash) in mapOfHashes) {
                val normalizedPath = rawPath
                val actualHash = actualHashes[normalizedPath]
                assertEquals(
                    "Hash mismatch for file: $normalizedPath",
                    expectedHash.lowercase(),
                    actualHash
                )
            }

            // Если хочешь проверить точное совпадение состава дерева, а не только наличие ожидаемых:
            assertEquals("Unexpected directories", expectedDirectories, actualDirectories)
        }
    }

    private suspend fun collectTree(
        file: ExFATFile,
        directoriesOut: MutableSet<String>,
        fileHashesOut: MutableMap<String, String>
    ) {
        val normalizedPath = file.path

        if (file.isDirectory) {
            if (normalizedPath != "/") {
                directoriesOut += normalizedPath
            }

            val children = file.listFiles()
            for (child in children) {
                collectTree(
                    file = child,
                    directoriesOut = directoriesOut,
                    fileHashesOut = fileHashesOut
                )

            }
        } else {
            val bytes = file.readFully()
            try {
                fileHashesOut[normalizedPath] = sha512(bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }

    private fun sha512(data: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-512").digest(data)
        return BigInteger(1, hash).toString(16).padStart(128, '0').lowercase()
    }

    @Test
    fun checkFileSystemCorrect() = runTest {

    }

    @Test
    fun checkFileSystemIncorrect() = runTest {

    }




}