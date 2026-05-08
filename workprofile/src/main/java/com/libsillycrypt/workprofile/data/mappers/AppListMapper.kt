package com.libsillycrypt.workprofile.data.mappers

import android.content.Context
import android.content.pm.PackageManager
import com.sillycrypt.mapper.Mapper
import dev.sillycrypt.common.entities.ApplicationInfoWithFlag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import javax.inject.Inject


class AppListMapper @Inject constructor(
    private val context: Context
): Mapper<List<String>, ImmutableList<ApplicationInfoWithFlag>> {
    override fun map(data: List<String>): ImmutableList<ApplicationInfoWithFlag> {
        val workProfileApps = data.toSet()
        return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).map {
            ApplicationInfoWithFlag(it,it.packageName in workProfileApps)
        }.toPersistentList()

    }
}