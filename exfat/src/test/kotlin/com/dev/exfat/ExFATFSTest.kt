package com.dev.exfat.exfat

import com.dev.exfat.data.MemoryRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.exfat.file.ExFATFile
import com.dev.exfat.file.SeekableOpenOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ExFATFSTest {

    class MemoryFactory(
        private val byteArray: ByteArray
    ) : RandomAccessDataFactory {
        override fun create(): RandomAccessData {
            return MemoryRandomAccessData(byteArray)
        }

        override suspend fun close() {}
    }

    @Test
    fun createFileSystem_shouldCreateValidEmptyExFatVolume() = runTest {
        val fixture = createFixture()

        assertTrue(fixture.fs.isExFat())

        val root = fixture.fs.root()
        assertEquals("/", root.name)
        assertEquals("/", root.path)
        assertTrue(root.isDirectory)

        val files = root.listFiles()
        assertTrue(files.isEmpty())

        fixture.fs.close()
    }

    @Test
    fun createAndWriteFile_shouldPersistAfterReopeningFileSystem() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()
        val file = root.createFile("hello.txt")
        val expected = "Hello from exFAT".encodeToByteArray()

        file.writeAllBytes(expected)

        assertArrayEquals(expected, file.readAllBytesSeekable())
        assertEquals(expected.size.toLong(), file.dataLength)

        fixture.fs.close()

        val reopened = fixture.reopen()
        val reopenedFile = reopened.getFileFromPath("/hello.txt")

        assertNotNull(reopenedFile)
        assertFalse(reopenedFile!!.isDirectory)
        assertArrayEquals(expected, reopenedFile.readAllBytesSeekable())

        reopened.close()
    }

    @Test
    fun editFile_shouldOverwriteAppendAndTruncateContent() = runTest {
        val fixture = createFixture()

        val file = fixture.fs.root().createFile("edit.txt")

        file.writeAllBytes("Hello world".encodeToByteArray())

        file.openSeekable(SeekableOpenOptions.fromAndroidMode("rw")).useSuspend { handle ->
            handle.writeAt(
                position = 6,
                src = "exFAT".encodeToByteArray()
            )
        }

        assertEquals("Hello exFAT", file.readAllBytesSeekable().decodeToString())

        file.openSeekable(SeekableOpenOptions.fromAndroidMode("rw")).useSuspend { handle ->
            val currentSize = handle.getSize()
            handle.writeAt(
                position = currentSize,
                src = "!!!".encodeToByteArray()
            )
            handle.fsync()
        }

        assertEquals("Hello exFAT!!!", file.readAllBytesSeekable().decodeToString())

        file.openSeekable(SeekableOpenOptions.fromAndroidMode("rw")).useSuspend { handle ->
            handle.truncate("Hello".length.toLong())
            handle.fsync()
        }

        assertEquals("Hello", file.readAllBytesSeekable().decodeToString())

        fixture.fs.close()
    }

    @Test
    fun deleteFile_shouldRemoveFileAndPersistAfterReopening() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()
        val file = root.createFile("delete-me.txt")
        file.writeAllBytes("temporary".encodeToByteArray())

        assertNotNull(fixture.fs.getFileFromPath("/delete-me.txt"))

        val deleted = file.delete()

        assertTrue(deleted)
        assertNull(fixture.fs.getFileFromPath("/delete-me.txt"))

        fixture.fs.close()

        val reopened = fixture.reopen()
        assertNull(reopened.getFileFromPath("/delete-me.txt"))

        reopened.close()
    }

    @Test
    fun createDirectoriesAndFiles_shouldReturnExpectedFilesByPath() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()
        val docs = root.createDirectory("docs")
        val images = root.createDirectory("images")

        val readme = docs.createFile("readme.txt")
        val nested = docs.createDirectory("nested")
        val note = nested.createFile("note.txt")
        val photo = images.createFile("photo.bin")

        readme.writeAllBytes("readme".encodeToByteArray())
        note.writeAllBytes("note".encodeToByteArray())
        photo.writeAllBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertTrue(fixture.fs.getFileFromPath("/docs")!!.isDirectory)
        assertTrue(fixture.fs.getFileFromPath("/docs/nested")!!.isDirectory)
        assertFalse(fixture.fs.getFileFromPath("/docs/readme.txt")!!.isDirectory)
        assertFalse(fixture.fs.getFileFromPath("/docs/nested/note.txt")!!.isDirectory)
        assertFalse(fixture.fs.getFileFromPath("/images/photo.bin")!!.isDirectory)

        assertNull(fixture.fs.getFileFromPath("/missing"))
        assertNull(fixture.fs.getFileFromPath("/docs/missing.txt"))

        fixture.fs.close()

        val reopened = fixture.reopen()

        assertNotNull(reopened.getFileFromPath("/docs/readme.txt"))
        assertNotNull(reopened.getFileFromPath("/docs/nested/note.txt"))
        assertNotNull(reopened.getFileFromPath("/images/photo.bin"))

        reopened.close()
    }

    @Test
    fun recursiveWalk_shouldFindAllExpectedFilesAndMatchHashes() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()

        val expectedFiles = linkedMapOf(
            "/a.txt" to "A".encodeToByteArray(),
            "/dir1/b.txt" to "BBBB".encodeToByteArray(),
            "/dir1/dir2/c.bin" to ByteArray(4096) { it.toByte() },
            "/dir1/dir2/d.txt" to "Тест unicode имени и содержимого".encodeToByteArray(),
            "/music/Неизвестен - новый дух большой боли.mp3" to ByteArray(100_000) { index ->
                (index * 31).toByte()
            }
        )

        createTree(root, expectedFiles)

        val actualHashes = walkFileHashes(root)
        val expectedHashes = expectedFiles.mapValues { (_, bytes) -> sha256(bytes) }

        assertEquals(expectedHashes.keys, actualHashes.keys)
        assertEquals(expectedHashes, actualHashes)

        fixture.fs.close()

        val reopened = fixture.reopen()
        val reopenedHashes = walkFileHashes(reopened.root())

        assertEquals(expectedHashes.keys, reopenedHashes.keys)
        assertEquals(expectedHashes, reopenedHashes)

        reopened.close()
    }

    @Test
    fun deleteEmptyDirectory_shouldRemoveDirectoryAndPersistAfterReopening() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()
        val dir = root.createDirectory("empty-dir")

        assertNotNull(fixture.fs.getFileFromPath("/empty-dir"))

        val deleted = dir.delete()

        assertTrue(deleted)
        assertNull(fixture.fs.getFileFromPath("/empty-dir"))

        fixture.fs.close()

        val reopened = fixture.reopen()

        assertNull(reopened.getFileFromPath("/empty-dir"))

        reopened.close()
    }

    @Test
    fun deleteDirectoryRecursively_shouldRemoveAllChildrenAndPersistAfterReopening() = runTest {
        val fixture = createFixture()

        val root = fixture.fs.root()

        val expectedFiles = linkedMapOf(
            "/project/readme.md" to "# Project".encodeToByteArray(),
            "/project/src/main.kt" to "fun main() = Unit".encodeToByteArray(),
            "/project/assets/data.bin" to ByteArray(8192) { (it % 251).toByte() }
        )

        createTree(root, expectedFiles)

        assertNotNull(fixture.fs.getFileFromPath("/project/readme.md"))
        assertNotNull(fixture.fs.getFileFromPath("/project/src/main.kt"))
        assertNotNull(fixture.fs.getFileFromPath("/project/assets/data.bin"))

        val projectDir = fixture.fs.getFileFromPath("/project")
        assertNotNull(projectDir)
        assertTrue(projectDir!!.isDirectory)

        projectDir.deleteRecursively()

        assertNull(fixture.fs.getFileFromPath("/project"))
        assertNull(fixture.fs.getFileFromPath("/project/readme.md"))
        assertNull(fixture.fs.getFileFromPath("/project/src/main.kt"))
        assertNull(fixture.fs.getFileFromPath("/project/assets/data.bin"))

        fixture.fs.close()

        val reopened = fixture.reopen()

        assertNull(reopened.getFileFromPath("/project"))
        assertNull(reopened.getFileFromPath("/project/readme.md"))
        assertNull(reopened.getFileFromPath("/project/src/main.kt"))
        assertNull(reopened.getFileFromPath("/project/assets/data.bin"))

        reopened.close()
    }

    @Test
    fun concurrentOperations_shouldCreateWriteReadAndDeleteDifferentFilesSafely() = runTest {
        val fixture = createFixture(volumeSizeBytes = 96 * 1024 * 1024)

        val root = fixture.fs.root()
        val dir = root.createDirectory("concurrent")

        val expected = (0 until 40).associate { index ->
            val path = "/concurrent/file-$index.bin"
            val bytes = ByteArray(16 * 1024 + index) { pos ->
                ((pos * 17 + index) and 0xFF).toByte()
            }
            path to bytes
        }

        coroutineScope {
            expected.map { (path, bytes) ->
                async {
                    val name = path.substringAfterLast('/')
                    val file = dir.createFile(name)
                    file.writeAllBytes(bytes)
                }
            }.awaitAll()
        }

        val actualHashes = walkFileHashes(root)
            .filterKeys { it.startsWith("/concurrent/") }

        val expectedHashes = expected.mapValues { (_, bytes) -> sha256(bytes) }

        assertEquals(expectedHashes.keys, actualHashes.keys)
        assertEquals(expectedHashes, actualHashes)

        coroutineScope {
            expected.keys
                .filterIndexed { index, _ -> index % 2 == 0 }
                .map { path ->
                    async {
                        val file = fixture.fs.getFileFromPath(path)
                        assertNotNull(file)
                        assertTrue(file!!.delete())
                    }
                }
                .awaitAll()
        }

        for ((index, path) in expected.keys.withIndex()) {
            val file = fixture.fs.getFileFromPath(path)

            if (index % 2 == 0) {
                assertNull(file)
            } else {
                assertNotNull(file)
            }
        }

        fixture.fs.close()

        val reopened = fixture.reopen()

        for ((index, path) in expected.keys.withIndex()) {
            val file = reopened.getFileFromPath(path)

            if (index % 2 == 0) {
                assertNull(file)
            } else {
                assertNotNull(file)
                assertArrayEquals(expected.getValue(path), file!!.readAllBytesSeekable())
            }
        }

        reopened.close()
    }

    private suspend fun createFixture(
        volumeSizeBytes: Int = 64 * 1024 * 1024
    ): ExFatFixture {
        val bytes = ByteArray(volumeSizeBytes)
        val factory = MemoryFactory(bytes)

        ExFATCreator(factory).create(
            bytesPerSectorShift = 9,
            sectorsPerClusterShift = 3,
            numberOfFats = 1
        )

        val fs = ExFATFS(
            dataFactory = factory,
            name = "memory-exfat"
        )

        assertTrue(fs.isExFat())

        return ExFatFixture(
            bytes = bytes,
            factory = factory,
            fs = fs
        )
    }

    private data class ExFatFixture(
        val bytes: ByteArray,
        val factory: MemoryFactory,
        val fs: ExFATFS
    ) {
        fun reopen(): ExFATFS {
            return ExFATFS(
                dataFactory = factory,
                name = "memory-exfat"
            )
        }
    }

    private suspend fun createTree(
        root: ExFATFile,
        files: Map<String, ByteArray>
    ) {
        val dirs = mutableMapOf<String, ExFATFile>()
        dirs["/"] = root

        for ((path, bytes) in files) {
            require(path.startsWith("/")) { "Only absolute paths are supported in test: $path" }

            val parts = path.removePrefix("/").split("/")
            require(parts.isNotEmpty())

            var currentDir = root
            var currentPath = "/"

            for (dirName in parts.dropLast(1)) {
                currentPath = joinTestPath(currentPath, dirName)

                currentDir = dirs.getOrPut(currentPath) {
                    currentDir.createDirectory(dirName)
                }
            }

            val fileName = parts.last()
            val file = currentDir.createFile(fileName)
            file.writeAllBytes(bytes)
        }
    }

    private suspend fun walkFileHashes(root: ExFATFile): Map<String, String> {
        val result = linkedMapOf<String, String>()

        suspend fun walk(file: ExFATFile) {
            if (file.isDirectory) {
                file.listFiles()
                    .sortedWith(
                        compareBy<ExFATFile> { it.path.count { ch -> ch == '/' } }
                            .thenBy { it.path }
                    )
                    .forEach { child ->
                        walk(child)
                    }
            } else {
                result[file.path] = sha256(file.readAllBytesSeekable())
            }
        }

        walk(root)

        return result
    }

    private suspend fun ExFATFile.deleteRecursively() {
        if (isDirectory) {
            val children = listFiles()

            for (child in children) {
                child.deleteRecursively()
            }
        }

        assertTrue(
            "Failed to delete ${path}",
            delete()
        )
    }

    private suspend fun ExFATFile.writeAllBytes(bytes: ByteArray) {
        openSeekable(SeekableOpenOptions.fromAndroidMode("rwt")).useSuspend { handle ->
            if (bytes.isNotEmpty()) {
                handle.writeAt(
                    position = 0,
                    src = bytes,
                    srcOffset = 0,
                    length = bytes.size
                )
            }

            handle.truncate(bytes.size.toLong())
            handle.fsync()
        }
    }

    private suspend fun ExFATFile.readAllBytesSeekable(): ByteArray {
        return openSeekable(SeekableOpenOptions.readOnly()).useSuspend { handle ->
            val size = handle.getSize()
            require(size <= Int.MAX_VALUE) {
                "Test helper cannot read huge file into memory: $size"
            }

            if (size == 0L) {
                return@useSuspend ByteArray(0)
            }

            val out = ByteArray(size.toInt())
            var offset = 0

            while (offset < out.size) {
                val read = handle.readAt(
                    position = offset.toLong(),
                    dst = out,
                    dstOffset = offset,
                    length = out.size - offset
                )

                if (read <= 0) break

                offset += read
            }

            if (offset == out.size) out else out.copyOf(offset)
        }
    }

    private suspend inline fun <T> com.dev.exfat.file.SeekableFileHandle.useSuspend(
        block: suspend (com.dev.exfat.file.SeekableFileHandle) -> T
    ): T {
        try {
            return block(this)
        } finally {
            close()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private fun joinTestPath(parent: String, child: String): String {
        return if (parent == "/") {
            "/$child"
        } else {
            "$parent/$child"
        }
    }
}