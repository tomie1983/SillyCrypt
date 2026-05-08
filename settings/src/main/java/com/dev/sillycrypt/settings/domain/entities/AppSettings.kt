package com.dev.sillycrypt.settings.domain.entities

import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class AppSettings(
    val packagesToInstall: ImmutableList<ApplicationInfoWithData> = persistentListOf(),
    val timeoutMillis: Long = 1000)
