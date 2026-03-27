package com.dev.exfat.file

/**
 * Future-compatible seekable open options for file-like access.
 *
 * Mapping to Android mode strings:
 * - "r"   -> read
 * - "w"   -> write
 * - "wt"  -> write + truncateOnOpen
 * - "wa"  -> write + append
 * - "rw"  -> read + write
 * - "rwt" -> read + write + truncateOnOpen
 *
 * Note:
 * - current exFAT implementation below is still read-only for mutating operations
 * - these options are introduced now so the API is already MediaProvider-friendly
 */
data class SeekableOpenOptions(
    val read: Boolean,
    val write: Boolean,
    val append: Boolean = false,
    val truncateOnOpen: Boolean = false
) {
    init {
        require(read || write) { "At least one of read/write must be enabled" }
        require(!(append && !write)) { "append=true requires write=true" }
        require(!(truncateOnOpen && !write)) { "truncateOnOpen=true requires write=true" }
    }

    companion object {
        fun readOnly(): SeekableOpenOptions = SeekableOpenOptions(
            read = true,
            write = false
        )

        fun fromAndroidMode(mode: String): SeekableOpenOptions {
            return when (mode) {
                "r" -> SeekableOpenOptions(read = true, write = false)
                "w" -> SeekableOpenOptions(read = false, write = true)
                "wt" -> SeekableOpenOptions(read = false, write = true, truncateOnOpen = true)
                "wa" -> SeekableOpenOptions(read = false, write = true, append = true)
                "rw" -> SeekableOpenOptions(read = true, write = true)
                "rwt" -> SeekableOpenOptions(read = true, write = true, truncateOnOpen = true)
                else -> throw IllegalArgumentException("Unsupported Android mode: $mode")
            }
        }
    }
}