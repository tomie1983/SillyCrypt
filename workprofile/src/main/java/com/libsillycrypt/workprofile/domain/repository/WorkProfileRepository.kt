package com.libsillycrypt.workprofile.domain.repository

import android.content.pm.ApplicationInfo
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import dev.sillycrypt.common.entities.ApplicationInfoWithFlag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WorkProfileRepository {

    val isWorkProfileAvailable: StateFlow<Boolean>

    suspend fun loadFromSettings()
    suspend fun setProfileName(name: String)
    suspend fun setCameraDisabled(value: Boolean)
    suspend fun setScreenCaptureDisabled(value: Boolean)
    suspend fun setAllowClipboardSharing(value: Boolean)
    suspend fun addAppToList(name: String)
    suspend fun removeAppFromList(name: String)
    fun isProfileOwner(): Boolean
    suspend fun setProvisionedStatus(status: Boolean)
    suspend fun startProfileCreation()
    suspend fun refreshWorkProfileStatus()
    fun deleteProfile(): Boolean
    val settings: Flow<WorkProfileSettings>
    suspend fun refershWorkProfileApps()
    val appsStatus: Flow<ImmutableList<ApplicationInfoWithFlag>>
    fun installOrDeleteApp(app: ApplicationInfo, install: Boolean, callback: () -> Unit)
    suspend fun deleteWorkProfile()
    fun installApps(apps: List<String>, callback: () -> Unit)
}