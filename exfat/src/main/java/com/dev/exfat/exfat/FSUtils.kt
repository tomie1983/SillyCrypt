package com.dev.exfat.exfat

import com.dev.exfat.data.RandomAccessData
import java.nio.charset.Charset
import kotlin.math.min

internal suspend fun readAt(data: RandomAccessData, pos: Long, size: Int): ByteArray {
    require(size >= 0)
    val out = ByteArray(size)
    data.seek(pos)
    data.read(out)
    return out
}


fun normalizeAbsolutePath(path: String): String {
    require(path.isNotEmpty()) { "Path must not be empty" }
    require(path.startsWith("/")) { "Only absolute paths are supported: $path" }

    if (path == "/") return "/"

    val rawParts = path.split('/')
    val parts = ArrayList<String>(rawParts.size)
    for (p in rawParts) {
        if (p.isEmpty()) continue
        require(p != ".") { "'.' is not supported in path: $path" }
        require(p != "..") { "'..' is not supported in path: $path" }
        parts += p
    }

    return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
}

fun decodeUtf16Le(a: ByteArray, off: Int, len: Int): String {
    val safeEnd = min(a.size, off + len)
    if (off >= safeEnd) return ""
    return a.copyOfRange(off, safeEnd).toString(Charsets.UTF_16LE)
}


fun ByteArray.copySliceView(offset: Int, len: Int): ByteArray {
    // This helper intentionally returns a new array; the caller copies read bytes back manually.
    // In this file, it is used only in readAllReadableBytes() where the result is copied by readStreamRange() through a temporary call.
    // To avoid confusion, keep len exact.
    require(offset >= 0 && len >= 0 && offset + len <= size)
    return ByteArray(len)
}

fun splitAbsolutePath(normalizedAbsolutePath: String): List<String> {
    if (normalizedAbsolutePath == "/") return emptyList()
    return normalizedAbsolutePath.removePrefix("/").split('/')
}

fun joinPath(parent: String, childName: String): String {
    return if (parent == "/") "/$childName" else "$parent/$childName"
}
suspend fun writeAt(data: RandomAccessData, pos: Long, bytes: ByteArray) {
    data.seek(pos)
    data.write(bytes)
}

fun putU16le(b: ByteArray, off: Int, v: Int) {
    b[off] = (v and 0xFF).toByte()
    b[off + 1] = ((v ushr 8) and 0xFF).toByte()
}

fun putU32le(b: ByteArray, off: Int, v: Int) {
    b[off] = (v and 0xFF).toByte()
    b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    b[off + 2] = ((v ushr 16) and 0xFF).toByte()
    b[off + 3] = ((v ushr 24) and 0xFF).toByte()
}

fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

fun alignUp(value: Int, alignment: Int): Int =
    ((value + alignment - 1) / alignment) * alignment

fun putU64le(b: ByteArray, off: Int, v: Long) {
    putU32le(b, off, (v and 0xFFFF_FFFFL).toInt())
    putU32le(b, off + 4, ((v ushr 32) and 0xFFFF_FFFFL).toInt())
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

fun concat(a: ByteArray, b: ByteArray): ByteArray {
    val result = a.copyOf(a.size + b.size)
    System.arraycopy(b, 0, result, a.size, b.size)
    return result
}

internal fun u64le(b: ByteArray, off: Int): Long {
    val lo = u32le(b, off).toLong() and 0xFFFF_FFFFL
    val hi = u32le(b, off + 4).toLong() and 0xFFFF_FFFFL
    return lo or (hi shl 32)
}