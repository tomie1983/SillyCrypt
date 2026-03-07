package com.dev.libsillycript.core.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * Reads an 8-byte big-endian signed long value from the specified offset in the byte array.
 *
 * @param buf the source byte array
 * @param off the starting index in the array to read the 8 bytes
 * @return the decoded Long value
 */
fun beLong(buf: ByteArray, off: Int): Long {
    return ByteBuffer
        .wrap(buf, off, 8)
        .order(ByteOrder.BIG_ENDIAN)
        .getLong()
}

/**
 * Writes a 64-bit long value into this array in big-endian byte order.
 *
 * @receiver the destination byte array
 * @param offset the starting index at which to write the 8 bytes
 * @param value the Long value to encode
 */
fun ByteArray.writeLongBE(offset: Int, value: Long) {
    for (i in 0..7) {
        this[offset + i] = ((value ushr (56 - 8 * i)) and 0xFF).toByte()
    }
}

/**
 * Writes a 32-bit integer value into this array in big-endian byte order.
 *
 * @receiver the destination byte array
 * @param offset the starting index at which to write the 4 bytes
 * @param value the Int value to encode
 */
fun ByteArray.writeIntBE(offset: Int, value: Int) {
    for (i in 0..3) {
        this[offset + i] = ((value ushr (24 - 8 * i)) and 0xFF).toByte()
    }
}

/**
 * Writes a 16-bit short value into this array in big-endian byte order.
 *
 * @receiver the destination byte array
 * @param offset the starting index at which to write the 2 bytes
 * @param value the Short value to encode
 */
fun ByteArray.writeShortBE(offset: Int, value: Short) {
    this[offset]     = ((value.toInt() ushr 8) and 0xFF).toByte()
    this[offset + 1] = (value.toInt() and 0xFF).toByte()
}

/**
 * Writes the given string's UTF-8 bytes into this array starting at the specified offset.
 *
 * @receiver the destination byte array
 * @param off the starting index at which to begin copying the string bytes
 * @param s the String to write into the array
 */
fun ByteArray.writeStringLE(off: Int, s: String) =
    s.toByteArray().copyInto(this, off)

