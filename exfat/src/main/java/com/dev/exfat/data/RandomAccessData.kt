package com.dev.exfat.data

/**
 * Provides random read/write access to an underlying data source.
 */
interface RandomAccessData {

    /**
     * Moves the read/write cursor to the specified absolute byte position.
     *
     * @param pos the zero-based byte index to seek to.
     */
    fun seek(pos: Long)

    /**
     * Reads up to `buf.size` bytes from the current position into [buf].
     *
     * Advances the cursor by the number of bytes actually read.
     *
     * @param buf the buffer to fill with data.
     * @return the number of bytes read, or -1 if end of data is reached.
     */
    suspend fun read(buf: ByteArray): Int

    /**
     * Writes the entire contents of [buf] at the current position.
     *
     * Advances the cursor by `buf.size` bytes.
     *
     * @param buf the data to write.
     */
    suspend fun write(buf: ByteArray)

    /**
     * Reads all remaining bytes from the current position to the end of the data.
     *
     * Advances the cursor to the end.
     *
     * @return a new byte array containing all remaining data.
     */
    suspend fun readFully(): ByteArray

    suspend fun close()

    /**
     * The total size, in bytes, of the underlying data.
     */
    val size: Long

    /**
     * The current cursor position, in bytes, from the beginning of the data.
     */
    val position: Long
}