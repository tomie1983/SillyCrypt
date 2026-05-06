package com.dev.sillycrypt.settings.domain.repository

import com.dev.sillycrypt.settings.domain.entities.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTimeout(timeout: Long)
    suspend fun markPackageForInstallation(packageName: String, install: Boolean)
}