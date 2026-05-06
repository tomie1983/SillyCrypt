package com.libsillycrypt.workprofile.domain.repository

import com.libsillycrypt.workprofile.domain.entities.WorkProfileSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface WorkProfileRepository {

    val isWorkProfileAvailable: SharedFlow<Boolean>

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
}