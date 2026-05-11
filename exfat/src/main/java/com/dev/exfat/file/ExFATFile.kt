package com.dev.exfat.file

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.ExFATFS
import com.dev.exfat.exfat.NodeState

class ExFATFile internal constructor(
    private val fileSystem: ExFATFS,
    internal val state: NodeState,
    private val displayName: String,
    private val displayPath: String
) : RandomAccessData {

    private val handler = ExFATFileHandler(
        fileSystem = fileSystem,
        state = state,
        displayPath = displayPath
    )

    val name: String
        get() = displayName

    val path: String
        get() = displayPath

    val isDirectory: Boolean
        get() = state.snapshotCore().isDirectory

    val attributes: Int
        get() = state.snapshotCore().attributes

    val firstCluster: Long
        get() = state.snapshotCore().firstCluster

    val dataLength: Long
        get() = state.snapshotCore().dataLength

    val validDataLength: Long
        get() = state.snapshotCore().validDataLength

    val noFatChain: Boolean
        get() = state.snapshotCore().noFatChain

    suspend fun listFiles(): List<ExFATFile> = handler.listFiles()

    suspend fun deleteFile(name: String): Boolean {
        require(isDirectory) { "Not a directory: $displayPath" }
        val childPath = if (path == "/") {
            "/$name"
        } else {
            "$path/$name"
        }

        return fileSystem.delete(childPath)
    }

    suspend fun delete(): Boolean {
        return fileSystem.delete(path)
    }

    suspend fun deleteRecursively(): Boolean {
        return fileSystem.delete(path, recursive = true)
    }

    suspend fun createFile(name: String): ExFATFile {
        require(isDirectory) { "Not a directory: $displayPath" }
        return fileSystem.createChild(state, displayPath, name, isDirectory = false)
    }

    suspend fun createDirectory(name: String): ExFATFile {
        require(isDirectory) { "Not a directory: $displayPath" }
        return fileSystem.createChild(state, displayPath, name, isDirectory = true)
    }

    suspend fun mkdir(name: String): ExFATFile = createDirectory(name)

    /**
     * Opens a seekable handle for this file.
     *
     * Directories are rejected because this API is meant for regular-file style access,
     * including future MediaProvider integration.
     */
    fun openSeekable(
        options: SeekableOpenOptions = SeekableOpenOptions.readOnly()
    ): ExFATSeekableFile {
        require(!isDirectory) { "Seekable file access is not supported for directories: $displayPath" }
        return fileSystem.openSeekable(state, displayPath, options)
    }

    override fun seek(pos: Long) = handler.seek(pos)

    override suspend fun read(buf: ByteArray): Int = handler.read(buf)

    override suspend fun write(buf: ByteArray) = handler.write(buf)

    override suspend fun readFully(): ByteArray = handler.readFully()

    override suspend fun close() = handler.close()

    override val size: Long
        get() = handler.size

    override val position: Long
        get() = handler.position
}