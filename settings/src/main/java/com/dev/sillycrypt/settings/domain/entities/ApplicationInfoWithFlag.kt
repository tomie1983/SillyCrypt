package com.dev.sillycrypt.settings.domain.entities

import android.content.pm.ApplicationInfo

data class ApplicationInfoWithFlag(
    val applicationInfo: ApplicationInfo,
    val toInstall: Boolean
)