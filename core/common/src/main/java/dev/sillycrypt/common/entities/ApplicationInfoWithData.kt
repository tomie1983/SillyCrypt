package dev.sillycrypt.common.entities

import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap

data class ApplicationInfoWithData(
    val applicationInfo: ApplicationInfo,
    val title: String,
    val packageName: String,
    val icon: ImageBitmap,
    val toInstall: Boolean
)