package com.dev.libsillycript.core.factory

import com.dev.exfat.data.FileRandomAccessData
import com.dev.exfat.data.RandomAccessData
import com.dev.exfat.data.RandomAccessDataFactory
import java.io.File

class UsualFileFactory(
    private val file: File,
): RandomAccessDataFactory {
    override fun create(): RandomAccessData {
        return FileRandomAccessData(file)
    }

    override suspend fun close() {

    }
}