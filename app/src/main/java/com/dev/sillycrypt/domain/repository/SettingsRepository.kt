package com.dev.sillycrypt.domain.repository

import com.dev.sillycrypt.domain.entities.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appSettings: Flow<AppSettings>
    suspend fun setTimeout(timeout: Long)
}