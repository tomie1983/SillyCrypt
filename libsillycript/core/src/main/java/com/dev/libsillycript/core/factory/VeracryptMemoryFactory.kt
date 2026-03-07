package com.dev.libsillycript.core.factory

import com.dev.exfat.data.MemoryRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume
import com.dev.libsillycript.core.volumes.EncryptionData

class VeracryptMemoryFactory(
    private val byteArray: ByteArray,
    private val encryptionData: EncryptionData,
    private val cache: SharedSectorCache = SharedSectorCache(),
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return BaseVeracryptVolume(
            MemoryRandomAccessData(byteArray), cache, encryptionData)
    }

}