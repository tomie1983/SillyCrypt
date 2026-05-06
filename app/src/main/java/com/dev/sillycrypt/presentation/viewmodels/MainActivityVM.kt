package com.dev.sillycrypt.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.sillycrypt.settings.domain.repository.SettingsRepository
import com.libsillycrypt.workprofile.data.utils.AuthenticationUtility
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityVM @Inject constructor(
    private val workProfileRepository: WorkProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val authenticationUtility: AuthenticationUtility,
    private val serviceUtils: ServiceUtils
): ViewModel() {

    init {
        refreshWorkProfileStatus()
    }

    val isWorkProfileAvailable = workProfileRepository.isWorkProfileAvailable


    fun refreshWorkProfileStatus() {
        viewModelScope.launch {
            workProfileRepository.refreshWorkProfileStatus()
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            authenticationUtility.reset()
            workProfileRepository.setProvisionedStatus(false)
        }
    }

    fun tryInstallApps() {
        viewModelScope.launch {
            Log.w("installCheck",workProfileRepository.settings.first().provisioned.toString())
            if (!workProfileRepository.settings.first().provisioned) {
                serviceUtils.installApps(settingsRepository.getAppsToInstall())
                workProfileRepository.setProvisionedStatus(true)
            }
        }
    }
}