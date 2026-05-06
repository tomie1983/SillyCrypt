package com.libsillycrypt.workprofile.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import com.libsillycrypt.workprofile.data.manager.WorkProfileManager
import com.libsillycrypt.workprofile.data.utils.WorkProfileUtils
import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class WorkProfileRepositoryImpl @Inject constructor(
    private val manager: WorkProfileManager,
    private val workProfileUtils: WorkProfileUtils,
    private val dataStore: DataStore<WorkProfileSettings>,
    private val _isWorkProfileAvailable: MutableSharedFlow<Boolean>,
    @ApplicationContext private val context: Context
): WorkProfileRepository {

    override val isWorkProfileAvailable: SharedFlow<Boolean> = _isWorkProfileAvailable.asSharedFlow()

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