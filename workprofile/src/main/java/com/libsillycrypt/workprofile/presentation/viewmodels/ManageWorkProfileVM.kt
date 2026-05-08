package com.libsillycrypt.workprofile.presentation.viewmodels

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageWorkProfileVM @Inject constructor(
    private val workProfileRepository: WorkProfileRepository,
): ViewModel() {

    val state = workProfileRepository.appsStatus.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        persistentListOf()
    )

    fun installOrUninstallApp(app: ApplicationInfo, install: Boolean) {
        workProfileRepository.installOrDeleteApp(app, install, ::refreshAppList)
    }

    private fun refreshAppList() {
        viewModelScope.launch {
            workProfileRepository.refershWorkProfileApps()
        }
    }

    suspend fun refreshApps() {
        workProfileRepository.refershWorkProfileApps()
    }

    fun isWorkProfileOwner(): Boolean {
        return workProfileRepository.isProfileOwner()
    }

    fun deleteProfile() {
        workProfileRepository.deleteProfile()
    }

    fun deleteProfileFromUser() {
        viewModelScope.launch {
            workProfileRepository.deleteWorkProfile()
        }
    }
}