package com.dev.sillycrypt.data.repository

import androidx.datastore.core.DataStore
import com.dev.sillycrypt.domain.entities.AppSettings
import com.dev.sillycrypt.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) : SettingsRepository {

    override val appSettings: Flow<AppSettings> = dataStore.data

    override suspend fun setTimeout(timeout: Long) {
        dataStore.updateData { current ->
            current.copy(timeout = timeout)
        }
    }
}