package com.dev.sillycrypt.data.serializers

import androidx.datastore.core.Serializer
import com.dev.sillycrypt.domain.entities.AppSettings
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettings> {

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) {
                defaultValue
            } else {
                Json.decodeFromString(
                    AppSettings.serializer(),
                    bytes.decodeToString()
                )
            }
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream
    ) {
        val text = Json.encodeToString(
            AppSettings.serializer(),
            t
        )
        output.write(text.encodeToByteArray())
    }
}