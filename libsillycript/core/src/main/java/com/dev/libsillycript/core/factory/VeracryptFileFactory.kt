package com.dev.libsillycript.core.factory

import com.dev.exfat.data.FileRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume
import com.dev.libsillycript.core.volumes.EncryptionData
import kotlinx.coroutines.runBlocking
import java.io.File

class VeracryptFileFactory(
    private val file: File,
    private val encryptionData: EncryptionData,
    private val cache: SharedSectorCache = SharedSectorCache()
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return BaseVeracryptVolume(FileRandomAccessData(file), cache, encryptionData)
    }

    override suspend fun close() {
        encryptionData.xts.close()
    }
}