package com.dev.sillycrypt.settings.presentation.viewmodels

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.sillycrypt.settings.domain.entities.AppSettings
import com.dev.sillycrypt.settings.domain.repository.SettingsRepository
import com.dev.sillycrypt.settings.presentation.state.SettingsScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsVM @Inject constructor(
    private val repository: SettingsRepository,
): ViewModel() {
    private val showDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val settings = combine(showDialog,repository.settings) {
        showDialog: Boolean, settings: AppSettings ->
        SettingsScreenState.Settings(
            settings.packagesToInstall,
            settings.timeoutMillis,
            showDialog
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsScreenState.Loading
    )

    fun setPackageToInstall(app: ApplicationInfo, install: Boolean) {
        viewModelScope.launch {
            repository.markPackageForInstallation(app.packageName, install)
        }
    }

    fun setDialogState(open: Boolean) {
        showDialog.value = open
    }

    fun setTimeoutMillis(timeout: Long) {
        viewModelScope.launch {
            repository.setTimeout(timeout)
        }
    }
}