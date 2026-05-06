package com.dev.sillycrypt.settings.domain.entities

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class AppSettings(
    val packagesToInstall: ImmutableList<ApplicationInfoWithFlag> = persistentListOf(),
    val timeoutMillis: Long = 1000)
