package com.dev.sillycrypt.presentation.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.libsillycript.core.HIDDEN_HEADER_DEFAULT_INDEX
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import com.dev.sillycrypt.domain.repository.ManageVolumeRepository
import com.dev.sillycrypt.presentation.states.OpenVolumeConfirmDialog
import com.dev.sillycrypt.presentation.states.OpenVolumeErrorState
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpenVolumeVM @Inject constructor(
    private val repository: ManageVolumeRepository,
): ViewModel() {

    private val openVolumeUIStateFlow = MutableStateFlow<OpenVolumeUIState>(OpenVolumeUIState.Initial())

    private val _errorState = MutableStateFlow<OpenVolumeErrorState?>(null)

    val errorState = _errorState.asStateFlow()

    private var openJob: Job? = null

    val state = combine(repository.state, openVolumeUIStateFlow) { repositoryState, uiState ->
        when (repositoryState) {
            is VolumeOpeningState.Initial -> {
                OpenVolumeUIState.Initial(repositoryState.data)
            }

            is VolumeOpeningState.SelectedDescriptor -> {
                when (uiState) {
                    is OpenVolumeUIState.OpenVolumeFormState -> uiState.copy(name = repositoryState.name)
                    is OpenVolumeUIState.Initial -> OpenVolumeUIState.OpenVolumeFormState(name = repositoryState.name)
                }
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        OpenVolumeUIState.Initial()
    )

    fun dismissError() {
        _errorState.value = null
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.setDescriptor(uri)
            }.onSuccess {
                val repoState = repository.state.first()
                if (repoState is VolumeOpeningState.SelectedDescriptor) {
                    openVolumeUIStateFlow.value =
                        OpenVolumeUIState.OpenVolumeFormState(name = repoState.name)
                }
            }.onFailure { error ->
                _errorState.value = OpenVolumeErrorState(
                    title = "Ошибка выбора файла",
                    message = error.stackTraceToString()
                )
            }
        }
    }


    fun closeVolume(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.closeVolume(id)
            }.onFailure { error ->
                _errorState.value = OpenVolumeErrorState(
                    title = "Ошибка закрытия тома",
                    message = error.stackTraceToString()
                )
            }
        }
    }

    fun updatePassword(value: String) = updateForm { copy(password = value) }
    fun updateKdf(value: KDFType) = updateForm { copy(kdf = value) }
    fun updateCipher(value: BlockCipherType) = updateForm { copy(cipher = value) }
    fun updateFsType(value: FsType) = updateForm { copy(fsType = value) }
    fun updatePim(value: String) = updateForm { copy(pim = value.filter { it.isDigit() }) }
    fun updateIndex(value: String) = updateForm { copy(index = value.filter { it.isDigit() }) }

    fun updateIsHiddenVolume(value: Boolean) {
        updateForm {
            copy(
                isHiddenVolume = value,
                protectHiddenVolume = if (value) false else protectHiddenVolume,
                index = if (value) HIDDEN_HEADER_DEFAULT_INDEX.toString() else "0"
            )
        }
    }

    fun updateProtectHiddenVolume(value: Boolean) =
        updateForm { copy(protectHiddenVolume = value) }

    fun updateHiddenPassword(value: String) = updateForm { copy(hiddenPassword = value) }
    fun updateHiddenKdf(value: KDFType) = updateForm { copy(hiddenKdf = value) }
    fun updateHiddenCipher(value: BlockCipherType) = updateForm { copy(hiddenCipher = value) }
    fun updateHiddenPim(value: String) = updateForm { copy(hiddenPim = value.filter { it.isDigit() }) }
    fun updateHiddenIndex(value: String) = updateForm { copy(hiddenIndex = value.filter { it.isDigit() }) }

    fun onBackPressed(): Boolean {
        return when (val currentState = state.value) {
            is OpenVolumeUIState.Initial -> false
            is OpenVolumeUIState.OpenVolumeFormState -> {
                if (currentState.loading) {
                    openVolumeUIStateFlow.update {
                        check(it is OpenVolumeUIState.OpenVolumeFormState)
                        it.copy(confirmDialog = OpenVolumeConfirmDialog.CancelLoading)
                    }
                } else {
                    openVolumeUIStateFlow.update {
                        check(it is OpenVolumeUIState.OpenVolumeFormState)
                        it.copy(confirmDialog = OpenVolumeConfirmDialog.ExitForm)
                    }
                }
                true
            }
        }
    }


    private fun updateForm(block: OpenVolumeUIState.OpenVolumeFormState.() -> OpenVolumeUIState.OpenVolumeFormState) {
        openVolumeUIStateFlow.update { state ->
            if (state is OpenVolumeUIState.OpenVolumeFormState) {
                state.block()
            } else {
                state
            }
        }
    }

    fun openVolume() {
        val current = state.value
        check(current is OpenVolumeUIState.OpenVolumeFormState)

        val mode = runCatching { current.toVeracryptMode() }
            .getOrElse { error ->
                _errorState.value = OpenVolumeErrorState(
                    title = "Некорректные параметры",
                    message = error.message ?: error.stackTraceToString()
                )
                return
            }
        openJob?.cancel()
        openJob = viewModelScope.launch {
            openVolumeUIStateFlow.update {
                check(it is OpenVolumeUIState.OpenVolumeFormState)
                it.copy(loading = true, confirmDialog = null)
            }
            runCatching {
                repository.openVolume(mode, current.fsType)
            }.onSuccess {
                openVolumeUIStateFlow.value = OpenVolumeUIState.Initial()
            }.onFailure { error ->
                openVolumeUIStateFlow.update {
                    check(it is OpenVolumeUIState.OpenVolumeFormState)
                    it.copy(loading = false, confirmDialog = null)
                }
                _errorState.value = OpenVolumeErrorState(
                    title = "Ошибка открытия тома",
                    message = error.stackTraceToString()
                )
            }
        }
    }

    fun dismissConfirmDialog() {
        openVolumeUIStateFlow.update {
            check(it is OpenVolumeUIState.OpenVolumeFormState)
            it.copy(confirmDialog = null)
        }
    }

    fun confirmExitForm() {
        viewModelScope.launch {
            repository.setInitial()
            openVolumeUIStateFlow.value = OpenVolumeUIState.Initial()
        }
    }

    fun confirmCancelLoading() {
        openJob?.cancel()
        openVolumeUIStateFlow.update {
            check(it is OpenVolumeUIState.OpenVolumeFormState)
            it.copy(
                loading = false,
                confirmDialog = null
            )
        }
    }
}