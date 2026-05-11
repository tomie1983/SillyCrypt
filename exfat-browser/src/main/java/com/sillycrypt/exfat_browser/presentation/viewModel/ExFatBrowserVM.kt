package com.sillycrypt.exfat_browser.presentation.viewModel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.exfat.file.ExFATFile
import com.sillycrypt.exfat_browser.domain.entities.BrowserDisplayEntry
import com.sillycrypt.exfat_browser.domain.repository.ExFATBrowserRepository
import com.sillycrypt.exfat_browser.presentation.state.ExFATBrowserState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExFatBrowserVM @Inject constructor(
    private val repository: ExFATBrowserRepository
): ViewModel() {

    private var selectedVolumeUuid: String? = null


    val state = combine(repository.displayedStateFlow, repository.selectedVolume) {
            entry: BrowserDisplayEntry, data: VeracryptVolumeData ->
        ExFATBrowserState.Data(data.uuid, entry.path, data.name, entry.files)
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        ExFATBrowserState.Loading
    )

    fun setSelectedVolume(volumeData: VeracryptVolumeData) {
        if (selectedVolumeUuid == volumeData.uuid) return

        selectedVolumeUuid = volumeData.uuid

        viewModelScope.launch {
            repository.setSelectedVolume(volumeData)
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            repository.createFile(name)
        }
    }

    fun createDirectory(name: String) {
        viewModelScope.launch {
            repository.createDir(name)
        }
    }

    fun deleteFile(file: ExFATFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
        }
    }

    fun copyFile(uri: Uri, name: String) {
        viewModelScope.launch {
            repository.copyFile(name, uri)
        }
    }

    fun listFiles(exFATFile: ExFATFile) {
        viewModelScope.launch {
            repository.listFiles(exFATFile)
        }
    }
}