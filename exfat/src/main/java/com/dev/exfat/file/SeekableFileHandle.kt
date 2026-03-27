package com.dev.exfat.file

interface SeekableFileHandle {
    val options: SeekableOpenOptions

    suspend fun getSize(): Long

    /**
     * Reads up to [length] bytes from absolute [position] into [dst] starting at [dstOffset].
     *
     * Returns number of bytes read, or -1 if [position] is at or beyond EOF.
     */
    suspend fun readAt(
        position: Long,
        dst: ByteArray,
        dstOffset: Int = 0,
        length: Int = dst.size - dstOffset
    ): Int

    /**
     * Writes up to [length] bytes from [src] starting at [srcOffset] to absolute [position].
     *
     * Current implementation is intentionally read-only and throws.
     */
    suspend fun writeAt(
        position: Long,
        src: ByteArray,
        srcOffset: Int = 0,
        length: Int = src.size - srcOffset
    ): Int

    /**
     * Truncates file to [newSize].
     *
     * Current implementation is intentionally read-only and throws.
     */
    suspend fun truncate(newSize: Long)

    /**
     * Flushes pending metadata/data to the underlying storage.
     *
     * Current implementation is a no-op because no writes are performed yet.
     */
    suspend fun fsync()

    suspend fun close()
}