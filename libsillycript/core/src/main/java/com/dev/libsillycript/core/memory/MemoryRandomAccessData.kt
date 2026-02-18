package com.dev.libsillycript.core.memory

import com.dev.exfat.data.RandomAccessData
import java.nio.ByteBuffer

class MemoryRandomAccessData(private val initial: ByteArray) : RandomAccessData {
    private val bb = ByteBuffer
        .wrap(initial)

    override fun seek(pos: Long) {
        require(pos in 0..bb.limit().toLong()) { "Out of bounds: $pos" }
        bb.position(pos.toInt())
    }

    override suspend fun read(buf: ByteArray): Int {
        val remaining = bb.remaining().coerceAtMost(buf.size)
        if (remaining == 0) return -1
        bb.get(buf, 0, remaining)
        return remaining
    }

    override suspend fun write(buf: ByteArray) {
        bb.put(buf)
    }

    override suspend fun readFully(): ByteArray {
        return initial
    }

    override val size: Long get() = initial.size.toLong()
    override val position: Long get() = bb.position().toLong()
    override suspend fun close() {  }
}