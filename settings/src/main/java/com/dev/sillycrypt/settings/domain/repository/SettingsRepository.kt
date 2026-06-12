package com.dev.sillycrypt.settings.domain.repository

import android.content.Context
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun getAppsToInstall(): List<String>
    suspend fun setTimeout(timeout: Long)
    suspend fun markPackageForInstallation(packageName: String, install: Boolean)
    suspend fun setLauncherIconVisible(visible: Boolean)
    suspend fun refreshVisibility()
    suspend fun setScreenshotsStatus(allowed: Boolean)
}