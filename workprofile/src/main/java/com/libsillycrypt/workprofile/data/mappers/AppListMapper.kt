package com.libsillycrypt.workprofile.data.mappers

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.sillycrypt.mapper.Mapper
import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import javax.inject.Inject


class AppListMapper @Inject constructor(
    private val context: Context
): Mapper<List<String>, ImmutableList<ApplicationInfoWithData>> {
    override fun map(data: List<String>): ImmutableList<ApplicationInfoWithData> {
        val workProfileApps = data.toSet()
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).map {
            val title = it.loadLabel(context.packageManager).toString()
            val icon = it.loadIcon(context.packageManager).toBitmap(96, 96)
                .asImageBitmap()
            ApplicationInfoWithData(it,title, it.packageName, icon,it.packageName in workProfileApps)
        }.sortedBy { it.title }.toPersistentList()

    }
}