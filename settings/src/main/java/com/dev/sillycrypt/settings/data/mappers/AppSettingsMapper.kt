package com.dev.sillycrypt.settings.data.mappers

import android.content.Context
import android.content.pm.PackageManager
import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import com.dev.sillycrypt.settings.domain.entities.ApplicationInfoWithFlag
import com.sillycrypt.mapper.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toPersistentList
import javax.inject.Inject

class AppSettingsMapper @Inject constructor(
    @ApplicationContext private val context: Context
): Mapper<AppSettingsData, AppSettings> {
    override fun map(data: AppSettingsData): AppSettings {
        val toInstallSet = data.packagesToInstall.toSet()
        val packagesList = context.packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA).map {
            ApplicationInfoWithFlag(it, it.packageName in toInstallSet)
        }
        return AppSettings(timeoutMillis = data.timeoutMillis, packagesToInstall = packagesList.toPersistentList())
    }
}