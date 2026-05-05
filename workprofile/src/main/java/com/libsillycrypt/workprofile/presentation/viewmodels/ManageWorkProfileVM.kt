package com.libsillycrypt.workprofile.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ManageWorkProfileVM @Inject constructor(
    private val workProfileRepository: WorkProfileRepository
): ViewModel() {

    fun isWorkProfileOwner(): Boolean {
        return workProfileRepository.isProfileOwner()
    }

    fun deleteProfile() {
        workProfileRepository.deleteProfile()
    }
}