package com.dev.sillycrypt.settings.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import com.dev.sillycrypt.settings.domain.entities.PackagesAndTimeoutSettings
import com.dev.sillycrypt.settings.domain.repository.SettingsRepository
import com.sillycrypt.mapper.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: DataStore<AppSettingsData>,
    private val mapper: Mapper<AppSettingsData, PackagesAndTimeoutSettings>,
    private val launcherIconVisible: MutableSharedFlow<Boolean>,
    @ApplicationContext private val context: Context,
): SettingsRepository {

    private val component = ComponentName(
        context,
        "${context.packageName}.LauncherAlias"
    )

    override val settings: Flow<AppSettings>
        get() = combine(settingsDataStore.data.map {
            mapper.map(it)
        }, launcherIconVisible) { settingsData, visible ->
            AppSettings(settingsData.packagesToInstall, settingsData.timeoutMillis,
                settingsData.allowScreenshots, visible)
        }

    override suspend fun refreshVisibility() {
        val visible = context.packageManager.getComponentEnabledSetting(
            component
        )
        launcherIconVisible.emit(visible != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override suspend fun setLauncherIconVisible(visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (visible)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        refreshVisibility()
    }


    override suspend fun getAppsToInstall(): List<String> {
        return settingsDataStore.data.first().packagesToInstall
    }

    override suspend fun setTimeout(timeout: Long) {
        settingsDataStore.updateData { it.copy(timeoutMillis = timeout) }
    }

    override suspend fun setScreenshotsStatus(allowed: Boolean) {
        settingsDataStore.updateData { it.copy(allowScreenshots = allowed) }
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