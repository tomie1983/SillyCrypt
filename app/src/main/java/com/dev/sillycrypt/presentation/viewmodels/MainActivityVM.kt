package com.dev.sillycrypt.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libsillycrypt.workprofile.data.utils.AuthenticationUtility
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityVM @Inject constructor(
    private val workProfileRepository: WorkProfileRepository,
    private val authenticationUtility: AuthenticationUtility
): ViewModel() {

    init {
        refreshWorkProfileStatus()
    }

    val isWorkProfileAvailable = workProfileRepository.isWorkProfileAvailable.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )


    fun refreshWorkProfileStatus() {
        viewModelScope.launch {
            workProfileRepository.refreshWorkProfileStatus()
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            authenticationUtility.reset()
        }
    }
}