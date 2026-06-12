package com.dev.sillycrypt.settings.domain.entities

import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList

data class AppSettings(
    val packagesToInstall: ImmutableList<ApplicationInfoWithData>,
    val timeoutMillis: Long,
    val allowScreenshots: Boolean,
    val isAppVisible: Boolean
)
