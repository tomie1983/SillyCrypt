package com.dev.libsillycript.android

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dev.exfat.data.FileRandomAccessData
import com.dev.exfat.data.MemoryRandomAccessData
import com.dev.libsillycript.android.blockCiphers.BlockCipherNativeFactory
import com.dev.libsillycript.android.data.RandomAccessFileDescriptor
import com.dev.libsillycript.android.factory.UsualDescriptorFactory
import com.dev.libsillycript.android.factory.VeracryptDescriptorFactory
import com.dev.libsillycript.core.VeraCryptMaster
import com.dev.libsillycript.android.keystore.AndroidKeyStoreFactory
import com.dev.libsillycript.core.VeracryptData
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.VeracryptOpeningData
import com.dev.libsillycript.core.cache.SharedSectorCache
import com.dev.libsillycript.core.factory.UsualFileFactory
import com.dev.libsillycript.core.factory.VeracryptFileFactory
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.volumes.BaseVeracryptVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor

class AndroidVeracryptMaster(
    private val context: Context,
    timeoutFlow: Flow<Long>
): VeraCryptMaster(
    keyStoreFactory = AndroidKeyStoreFactory(timeoutFlow),
    blockCipherFactory = BlockCipherNativeFactory(),
) {
    suspend fun create(
        uri: Uri,
        data: List<VeracryptData>,
    ) {
        withContext(safeDispatcher) {
            val newData = alignSizesToSectors(data)
            verifyVeracryptLayoutForFS(newData)
            createRaw(UsualDescriptorFactory(uri, context), newData, true)
            newData.forEach { veracryptData ->
                createFileSystem(
                    uri,
                    veracryptData.veracryptOpeningData,
                    veracryptData.index,
                    veracryptData.fsType
                )
                veracryptData.veracryptOpeningData.password.fill('?')
            }
        }
    }

    suspend fun createFileSystem(
        uri: Uri,
        data: VeracryptOpeningData,
        index: Int,
        fsType: FsType
    ) {
        val encryptionData = openUnsafeRaw(
            RandomAccessFileDescriptor(context, uri),
            VeracryptMode.OpenNormal(data, index),
        )
        val volumeFactory = VeracryptDescriptorFactory(uri, context, encryptionData)
        fsFactory.create(fsType, volumeFactory)
    }

    suspend fun open(
        uri: Uri,
        data: VeracryptMode,
        fsType: FsType,
        name: String
    ): String {
        return withContext(safeDispatcher) {
            val data  = openUnsafeRaw(
                input = RandomAccessFileDescriptor(context, uri),
                data = data,
            )
            val volumeFactory = VeracryptDescriptorFactory(uri, context, data)
            return@withContext fsFactory.open(fsType, volumeFactory, name)
        }
    }

    suspend fun openRaw(
        uri: Uri,
        data: VeracryptMode,
        cache: SharedSectorCache = SharedSectorCache(),
    ): BaseVeracryptVolume {
        return openRaw(
            input = RandomAccessFileDescriptor(context, uri),
            data = data,
            cache = cache,
        )
    }
}