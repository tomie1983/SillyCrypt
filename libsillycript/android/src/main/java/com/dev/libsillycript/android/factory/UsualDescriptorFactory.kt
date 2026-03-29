package com.dev.libsillycript.android.factory

import android.os.ParcelFileDescriptor
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.android.data.RandomAccessFileDescriptor

class UsualDescriptorFactory(
    private val pfd: ParcelFileDescriptor
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return RandomAccessFileDescriptor(pfd)
    }

    override suspend fun close() {

    }
}