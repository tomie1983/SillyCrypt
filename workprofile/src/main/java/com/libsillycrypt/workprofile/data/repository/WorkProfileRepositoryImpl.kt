package com.libsillycrypt.workprofile.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.datastore.core.DataStore
import com.libsillycrypt.workprofile.data.manager.WorkProfileManager
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.data.utils.WorkProfileUtils
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import com.sillycrypt.mapper.Mapper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sillycrypt.common.entities.ApplicationInfoWithFlag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkProfileRepositoryImpl @Inject constructor(
    private val manager: WorkProfileManager,
    private val workProfileUtils: WorkProfileUtils,
    private val serviceUtils: ServiceUtils,
    private val dataStore: DataStore<WorkProfileSettings>,
    private val _isWorkProfileAvailable: MutableStateFlow<Boolean>,
    private val _workProfileApps: MutableStateFlow<List<String>>,
    private val appListMapper: Mapper<List<String>, ImmutableList<ApplicationInfoWithFlag>>,
    @ApplicationContext private val context: Context
): WorkProfileRepository {

    override val isWorkProfileAvailable: StateFlow<Boolean> = _isWorkProfileAvailable.asStateFlow()

    override val appsStatus: Flow<ImmutableList<ApplicationInfoWithFlag>> = _workProfileApps.map {
        appListMapper.map(it)
    }

    override val settings = dataStore.data

    override suspend fun loadFromSettings() {
        manager.applySettings(settings.first())
    }

    override fun isProfileOwner(): Boolean {
        return manager.isProfileOwner()
    }

    override suspend fun setProfileName(name: String) {
        manager.setProfileName(name)
        dataStore.updateData { it.copy(name = name) }
    }

    override suspend fun setCameraDisabled(value: Boolean) {
        manager.setCameraDisabled(value)
        dataStore.updateData { it.copy(isCameraDisabled = value) }
    }

    override suspend fun setProvisionedStatus(status: Boolean) {
        dataStore.updateData { it.copy(provisioned = status) }
    }

    override suspend fun setScreenCaptureDisabled(value: Boolean) {
        manager.setScreenCaptureDisabled(value)
        dataStore.updateData { it.copy(isScreenCaptureDisabled = value) }
    }

    override suspend fun setAllowClipboardSharing(value: Boolean) {
        manager.setAllowClipboardSharing(value)
        dataStore.updateData { it.copy(allowClipBoardSharing = value) }
    }

    override suspend fun addAppToList(name: String) {
        manager.installExistingPackageSafely(name)
        dataStore.updateData {
            val newList = it.appsList + name
            it.copy(appsList = newList)
        }
    }

    override suspend fun startProfileCreation() {
        manager.startProfileCreation()
    }

    override suspend fun refreshWorkProfileStatus() {
        _isWorkProfileAvailable.emit(workProfileUtils.isWorkProfileAvailable(context))
    }

    override suspend fun refershWorkProfileApps() {
        _workProfileApps.emit(serviceUtils.getAppList())
    }

    override fun installOrDeleteApp(app: ApplicationInfo, install: Boolean, callback: () -> Unit) {
        if (install) {
            serviceUtils.installApp(app, callback)
        } else {
            serviceUtils.uninstallApp(app, callback)
        }
    }

    override suspend fun deleteWorkProfile() {
        if (serviceUtils.deleteWorkProfile()) {
            refreshWorkProfileStatus()
        }
    }

    override fun installApps(apps: List<String>, callback: () -> Unit) {
        serviceUtils.installApps(apps, callback)
    }

    override suspend fun removeAppFromList(name: String) {
        manager.removeAppFromWorkProfile(name)
        dataStore.updateData {
            val newList = it.appsList.toMutableList()
            newList.remove(name)
            it.copy(appsList = newList)
        }
    }

    override fun deleteProfile(): Boolean {
        Log.w("deleteProfile","started")
        return manager.deleteProfile()
    }
}