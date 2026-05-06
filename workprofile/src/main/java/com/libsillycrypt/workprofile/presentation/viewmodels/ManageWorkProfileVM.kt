package com.libsillycrypt.workprofile.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libsillycrypt.workprofile.data.utils.ServiceUtils
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageWorkProfileVM @Inject constructor(
    private val workProfileRepository: WorkProfileRepository,
    private val serviceUtils: ServiceUtils
): ViewModel() {

    fun isWorkProfileOwner(): Boolean {
        return workProfileRepository.isProfileOwner()
    }

    fun deleteProfile() {
        workProfileRepository.deleteProfile()
    }

    fun deleteProfileFromUser() {
        viewModelScope.launch {
            if (serviceUtils.deleteWorkProfile()) {
                workProfileRepository.refreshWorkProfileStatus()
            }
        }
    }
}