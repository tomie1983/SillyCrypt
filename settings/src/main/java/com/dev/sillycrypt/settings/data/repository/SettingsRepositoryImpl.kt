package com.dev.sillycrypt.settings.data.repository

import androidx.datastore.core.DataStore
import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import com.dev.sillycrypt.settings.domain.repository.SettingsRepository
import com.sillycrypt.mapper.Mapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: DataStore<AppSettingsData>,
    private val mapper: Mapper<AppSettingsData, AppSettings>
): SettingsRepository {
    override val settings: Flow<AppSettings>
        get() = settingsDataStore.data.map {
            mapper.map(it)
        }

    override suspend fun setTimeout(timeout: Long) {
        settingsDataStore.updateData { it.copy(timeoutMillis = timeout) }
    }

    override suspend fun markPackageForInstallation(
        packageName: String,
        install: Boolean
    ) {
        settingsDataStore.updateData {
            val newPackageList = it.packagesToInstall.toMutableList()
            if (install) {
                newPackageList.add(packageName)
            } else {
                newPackageList.remove(packageName)
            }
            it.copy(packagesToInstall = newPackageList)
        }
    }
}