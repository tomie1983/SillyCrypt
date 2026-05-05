package com.libsillycrypt.workprofile.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libsillycrypt.workprofile.domain.repository.WorkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DummyActivityVM @Inject constructor(
    private val repository: WorkProfileRepository
): ViewModel() {

    fun setProvisionedStatus(status: Boolean) {
        viewModelScope.launch {
            repository.setProvisionedStatus(status)
        }
    }

    fun deleWorkProfile() {
        repository.deleteProfile()
    }
}