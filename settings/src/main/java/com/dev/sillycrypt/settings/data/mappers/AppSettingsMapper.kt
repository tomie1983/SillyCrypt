package com.dev.sillycrypt.settings.data.mappers

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.dev.sillycrypt.settings.data.entities.AppSettingsData
import com.dev.sillycrypt.settings.domain.entities.PackagesAndTimeoutSettings
import com.sillycrypt.mapper.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.toPersistentList
import javax.inject.Inject

class AppSettingsMapper @Inject constructor(
    @ApplicationContext private val context: Context
): Mapper<AppSettingsData, PackagesAndTimeoutSettings> {
    override fun map(data: AppSettingsData): PackagesAndTimeoutSettings {
        val toInstallSet = data.packagesToInstall.toSet()
        val packagesList = context.packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA).map {
            val title = it.loadLabel(context.packageManager).toString()
            val icon = it.loadIcon(context.packageManager).toBitmap(96, 96)
                .asImageBitmap()
            ApplicationInfoWithData(it, title, it.packageName, icon, it.packageName in toInstallSet)
        }.sortedBy { it.title }
        return PackagesAndTimeoutSettings(timeoutMillis = data.timeoutMillis, packagesToInstall = packagesList.toPersistentList(),
            allowScreenshots = data.allowScreenshots)
    }
}