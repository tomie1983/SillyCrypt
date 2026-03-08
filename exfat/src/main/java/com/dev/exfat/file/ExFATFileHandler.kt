package com.dev.exfat.file

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.ExFATFS
import com.dev.exfat.exfat.NodeState

class ExFATFileHandler internal constructor(
    private val fileSystem: ExFATFS,
    private val state: NodeState,
    private val displayPath: String
) : RandomAccessData {

    private val data: RandomAccessData = fileSystem.createDataHandle()
    private var cursor: Long = 0L

    suspend fun listFiles(): List<ExFATFile> {
        val core = state.snapshotCore()
        require(core.isDirectory) { "Not a directory" }

        return fileSystem.listDirectory(
            dirState = state,
            parentDisplayPath = displayPath,
            data = data
        )
    }

    override fun seek(pos: Long) {
        require(pos >= 0L) { "Negative seek position: $pos" }
        cursor = pos
    }

    override suspend fun read(buf: ByteArray): Int {
        val n = fileSystem.readStreamRange(state, cursor, buf, data)
        if (n > 0) cursor += n
        return n
    }

    override suspend fun write(buf: ByteArray) {
        throw UnsupportedOperationException("Write support is not implemented yet")
    }

    override suspend fun readFully(): ByteArray {
        val remaining = (size - cursor).coerceAtLeast(0L)
        if (remaining == 0L) return ByteArray(0)

        if (remaining > Int.MAX_VALUE.toLong()) {
            throw IllegalStateException("Too much remaining data to readFully(): $remaining")
        }

        val out = ByteArray(remaining.toInt())
        var off = 0
        while (off < out.size) {
            val chunk = ByteArray(out.size - off)
            val n = read(chunk)
            if (n <= 0) break
            System.arraycopy(chunk, 0, out, off, n)
            off += n
        }
        return if (off == out.size) out else out.copyOf(off)
    }

    override suspend fun close() {
        data.close()
    }

    override val size: Long
        get() = state.snapshotCore().readableLength

    override val position: Long
        get() = cursor
}