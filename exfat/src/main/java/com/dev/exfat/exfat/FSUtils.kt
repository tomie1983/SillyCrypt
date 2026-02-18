package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import java.nio.charset.Charset

internal suspend fun readAt(data: RandomAccessData, pos: Long, size: Int): ByteArray {
    require(size >= 0)
    val out = ByteArray(size)
    data.seek(pos)
    data.read(out)
    return out
}


internal fun concatChunks(parts: List<ByteArray>, total: Int): ByteArray {
    val out = ByteArray(total)
    var off = 0
    for (p in parts) {
        System.arraycopy(p, 0, out, off, p.size)
        off += p.size
    }
    return out
}

internal fun decodeUtf16le(bytes: ByteArray, off: Int, len: Int): String {
    // Strip trailing 0x0000 pairs later; here decode raw
    return bytes.copyOfRange(off, off + len).toString(Charset.forName("UTF-16LE"))
}

internal fun u8(b: Byte): Int = b.toInt() and 0xFF

internal fun u16le(b: ByteArray, off: Int): Int {
    val lo = u8(b[off])
    val hi = u8(b[off + 1])
    return lo or (hi shl 8)
}

internal fun u32le(b: ByteArray, off: Int): Int {
    val b0 = u8(b[off])
    val b1 = u8(b[off + 1])
    val b2 = u8(b[off + 2])
    val b3 = u8(b[off + 3])
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

internal fun u64le(b: ByteArray, off: Int): Long {
    val lo = u32le(b, off).toLong() and 0xFFFF_FFFFL
    val hi = u32le(b, off + 4).toLong() and 0xFFFF_FFFFL
    return lo or (hi shl 32)
}