package com.libsillycrypt.core.serialization

import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

open class BaseSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) {
                defaultValue
            } else {
                Json.decodeFromString(
                    serializer,
                    bytes.decodeToString()
                )
            }
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: T,
        output: OutputStream
    ) {
        val text = Json.encodeToString(
            serializer,
            t
        )
        output.write(text.encodeToByteArray())
    }
}