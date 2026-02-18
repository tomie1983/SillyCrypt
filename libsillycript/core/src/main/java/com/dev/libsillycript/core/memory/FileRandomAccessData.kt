package com.dev.libsillycript.core.memory

import com.dev.libsillycript.core.utils.concat
import java.io.File
import java.io.RandomAccessFile
import kotlin.compareTo


class FileRandomAccessData(file: File, mode: String = "rw"): RandomAccessData {
    private val raf = RandomAccessFile(file, mode)

    override fun seek(pos: Long) = raf.seek(pos)
    override suspend fun read(buf: ByteArray): Int = raf.read(buf)
    override suspend fun readFully(): ByteArray {
        seek(0)
        val result = ByteArray(0)
        while (true) {
            val buffer = ByteArray(BUFFER_SIZE)
            val read = read(buffer)
            if (read <= 0) break
            concat(result,buffer.copyOf(read))
        }
        return result
    }

    override val size: Long get() = raf.length()
    override suspend fun write(buf: ByteArray) { raf.write(buf) }
    override val position: Long get() = raf.filePointer
    override suspend fun close() = raf.close()

    companion object {
        private const val BUFFER_SIZE = 128
    }
}