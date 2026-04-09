package com.dev.libsillycript.android.factory

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import com.dev.libsillycript.android.data.RandomAccessFileDescriptor

class UsualDescriptorFactory(
    private val uri: Uri,
    private val context: Context
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return RandomAccessFileDescriptor(context, uri)
    }

    override suspend fun close() {

    }
}