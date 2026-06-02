package com.dev.sillycrypt.data.mapper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sillycrypt.mapper.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UriMapper @Inject constructor(
    @ApplicationContext private val context: Context
): Mapper<Uri, String?> {
    override fun map(data: Uri): String? {
        val fileName = queryFileName(data)
        return fileName
    }

    fun queryFileName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment
    }
}