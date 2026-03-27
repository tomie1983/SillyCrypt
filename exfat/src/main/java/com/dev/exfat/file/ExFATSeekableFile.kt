package com.dev.exfat.file

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.ExFATFS
import com.dev.exfat.exfat.NodeState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExFATSeekableFile internal constructor(
    private val fileSystem: ExFATFS,
    private val state: NodeState,
    val displayPath: String,
    override val options: SeekableOpenOptions
) : SeekableFileHandle {

    private val data: RandomAccessData = fileSystem.createDataHandle()
    private val ioMutex = Mutex()

    @Volatile
    private var closed: Boolean = false

    @Volatile
    private var openInitialized: Boolean = false

    override suspend fun getSize(): Long {
        ensureOpen()
        ensureOpenInitialized()
        return state.snapshotCore().dataLength
    }

    override suspend fun readAt(
        position: Long,
        dst: ByteArray,
        dstOffset: Int,
        length: Int
    ): Int {
        ensureOpen()
        ensureReadable()
        ensureOpenInitialized()
        require(position >= 0L) { "Negative position: $position" }
        require(dstOffset >= 0) { "Negative dstOffset: $dstOffset" }
        require(length >= 0) { "Negative length: $length" }
        require(dstOffset + length <= dst.size) {
            "Destination range out of bounds: dstOffset=$dstOffset length=$length size=${dst.size}"
        }

        if (length == 0) return 0

        return ioMutex.withLock {
            ensureOpen()
            fileSystem.readSeekableRange(state, position, dst, dstOffset, length, data)
        }
    }

    override suspend fun writeAt(
        position: Long,
        src: ByteArray,
        srcOffset: Int,
        length: Int
    ): Int {
        ensureOpen()
        ensureWritable()
        ensureOpenInitialized()
        require(position >= 0L) { "Negative position: $position" }
        require(srcOffset >= 0) { "Negative srcOffset: $srcOffset" }
        require(length >= 0) { "Negative length: $length" }
        require(srcOffset + length <= src.size) {
            "Source range out of bounds: srcOffset=$srcOffset length=$length size=${src.size}"
        }

        if (length == 0) return 0

        return ioMutex.withLock {
            ensureOpen()
            val effectivePosition = if (options.append) state.snapshotCore().dataLength else position
            fileSystem.writeSeekableRange(state, effectivePosition, src, srcOffset, length, data)
        }
    }

    override suspend fun truncate(newSize: Long) {
        ensureOpen()
        ensureWritable()
        ensureOpenInitialized()
        require(newSize >= 0L) { "Negative newSize: $newSize" }

        ioMutex.withLock {
            ensureOpen()
            fileSystem.truncateSeekable(state, newSize)
        }
    }

    override suspend fun fsync() {
        ensureOpen()
        ensureOpenInitialized()
        // RandomAccessData has no fsync primitive. Metadata writes are synchronous.
    }

    override suspend fun close() {
        ioMutex.withLock {
            if (closed) return
            closed = true
            data.close()
        }
    }

    private suspend fun ensureOpenInitialized() {
        if (openInitialized) return
        if (options.truncateOnOpen) {
            fileSystem.truncateSeekable(state, 0L)
        }
        openInitialized = true
    }

    private fun ensureReadable() {
        require(options.read) { "File was not opened for reading: $displayPath" }
    }

    private fun ensureWritable() {
        require(options.write) { "File was not opened for writing: $displayPath" }
    }

    private fun ensureOpen() {
        check(!closed) { "Seekable file handle is already closed: $displayPath" }
    }
}