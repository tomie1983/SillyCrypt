package com.dev.libsillycript.android.data

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import com.dev.exfat.data.RandomAccessData
import java.io.EOFException
import java.io.IOException

class RandomAccessFileDescriptor(private val pfd: ParcelFileDescriptor): RandomAccessData {

    private val fd = pfd.fileDescriptor

    @Volatile
    private var closed = false

    override fun seek(pos: Long) {
        checkNotClosed()
        require(pos >= 0) { "Position must be >= 0, was $pos" }
        try {
            Os.lseek(fd, pos, OsConstants.SEEK_SET)
        } catch (e: ErrnoException) {
            throw IOException("seek($pos) failed", e)
        }
    }

    override suspend fun read(buf: ByteArray): Int {
        checkNotClosed()
        if (buf.isEmpty()) return 0

        try {
            return Os.read(fd, buf, 0, buf.size)
        } catch (e: ErrnoException) {
            throw IOException("read failed", e)
        }
    }

    override suspend fun write(buf: ByteArray) {
        checkNotClosed()
        if (buf.isEmpty()) return

        var offset = 0
        try {
            while (offset < buf.size) {
                val written = Os.write(fd, buf, offset, buf.size - offset)
                if (written <= 0) {
                    throw EOFException("write returned $written before all bytes were written")
                }
                offset += written
            }
        } catch (e: ErrnoException) {
            throw IOException("write failed", e)
        }
    }

    override suspend fun readFully(): ByteArray {
        checkNotClosed()

        val remaining = size - position
        require(remaining >= 0) { "Negative remaining size: $remaining" }
        if (remaining > Int.MAX_VALUE) {
            throw IOException("Too much data remaining for one ByteArray: $remaining bytes")
        }

        val out = ByteArray(remaining.toInt())
        var offset = 0

        try {
            while (offset < out.size) {
                val read = Os.read(fd, out, offset, out.size - offset)
                if (read == -1 || read == 0) break
                offset += read
            }
        } catch (e: ErrnoException) {
            throw IOException("readFully failed", e)
        }

        return if (offset == out.size) out else out.copyOf(offset)
    }

    override suspend fun close() {
        if (!closed) {
            closed = true
            pfd.close()
        }
    }

    override val size: Long
        get() {
            checkNotClosed()
            return try {
                val st: StructStat = Os.fstat(fd)
                st.st_size
            } catch (e: ErrnoException) {
                throw IOException("size query failed", e)
            }
        }

    override val position: Long
        get() {
            checkNotClosed()
            return try {
                Os.lseek(fd, 0, OsConstants.SEEK_CUR)
            } catch (e: ErrnoException) {
                throw IOException("position query failed", e)
            }
        }

    private fun checkNotClosed() {
        if (closed) throw IOException("RandomAccessData is closed")
    }
}