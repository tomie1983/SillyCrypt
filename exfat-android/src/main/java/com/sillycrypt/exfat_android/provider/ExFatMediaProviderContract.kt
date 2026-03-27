package com.sillycrypt.exfat_android.provider

import android.net.Uri
import com.dev.exfat.exfat.normalizeAbsolutePath

/**
 * content://<authority>/<volumeId>/<path inside exfat>
 *
 * Examples:
 * - content://com.dev.exfat.provider.media/vol-123                -> root of volume /
 * - content://com.dev.exfat.provider.media/vol-123/DCIM/a.jpg     -> /DCIM/a.jpg
 */
object ExFatMediaProviderContract {
    const val DEFAULT_AUTHORITY: String = "com.dev.exfat.provider.media"

    data class Target(
        val volumeId: String,
        val absolutePath: String
    )

    fun buildUri(
        volumeId: String,
        absolutePath: String,
        authority: String = DEFAULT_AUTHORITY
    ): Uri {
        require(volumeId.isNotBlank()) { "volumeId must not be blank" }
        val normalized = normalizeAbsolutePath(absolutePath)

        val builder = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(volumeId)

        if (normalized != "/") {
            val parts = normalized.removePrefix("/").split('/')
            for (part in parts) {
                builder.appendPath(part)
            }
        }

        return builder.build()
    }

    fun parse(uri: Uri, authority: String = DEFAULT_AUTHORITY): Target {
        require(uri.scheme == "content") { "Unsupported scheme: ${uri.scheme}" }
        require(uri.authority == authority) {
            "Unexpected authority: ${uri.authority}, expected=$authority"
        }

        val segments = uri.pathSegments
        require(segments.isNotEmpty()) { "URI must start with volumeId: $uri" }

        val volumeId = segments.first()
        val absolutePath = if (segments.size == 1) {
            "/"
        } else {
            normalizeAbsolutePath("/" + segments.drop(1).joinToString("/"))
        }

        return Target(volumeId = volumeId, absolutePath = absolutePath)
    }
}
