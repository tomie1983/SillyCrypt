package com.dev.libsillycript.android.factory

import android.os.ParcelFileDescriptor
import com.dev.exfat.data.FileRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.android.data.RandomAccessFileDescriptor
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume
import com.dev.libsillycript.core.volumes.EncryptionData

class VeracryptDescriptorFactory(
    private val pfd: ParcelFileDescriptor,
    private val encryptionData: EncryptionData,
    private val cache: SharedSectorCache = SharedSectorCache()
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return BaseVeracryptVolume(RandomAccessFileDescriptor(pfd), cache, encryptionData)
    }

    override suspend fun close() {
        encryptionData.xts.close()
    }
}