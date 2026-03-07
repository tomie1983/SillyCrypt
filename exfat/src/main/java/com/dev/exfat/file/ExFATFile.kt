package com.dev.exfat.file

import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.exfat.ExFATFS

class ExFATFile internal constructor(
    private val fileSystem: ExFATFS,
    internal val meta: ExFATFS.ExFatNodeMetadata
) : RandomAccessData {

    private val handler: ExFATFileHandler = ExFATFileHandler(fileSystem, meta)

    val name: String get() = if (meta.path == "/") "/" else meta.name
    val path: String get() = meta.path
    val isDirectory: Boolean get() = meta.isDirectory
    val attributes: Int get() = meta.attributes
    val firstCluster: Long get() = meta.firstCluster
    val dataLength: Long get() = meta.dataLength
    val validDataLength: Long get() = meta.validDataLength
    val noFatChain: Boolean get() = meta.noFatChain

    suspend fun listFiles(): List<ExFATFile> = handler.listFiles()

    override fun seek(pos: Long) = handler.seek(pos)

    override suspend fun read(buf: ByteArray): Int = handler.read(buf)

    override suspend fun write(buf: ByteArray) = handler.write(buf)

    override suspend fun readFully(): ByteArray = handler.readFully()

    override suspend fun close() = handler.close()

    override val size: Long
        get() = handler.size

    override val position: Long
        get() = handler.position
}
