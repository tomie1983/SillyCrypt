package com.dev.sillycrypt.settings.data.entities

import kotlinx.serialization.Serializable

@Serializable
data class AppSettingsData(
    val packagesToInstall: List<String> = listOf(),
    val timeoutMillis: Long = 1000,
    val allowScreenshots: Boolean = false
)