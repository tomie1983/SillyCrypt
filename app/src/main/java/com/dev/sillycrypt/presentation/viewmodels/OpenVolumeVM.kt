package com.dev.sillycrypt.presentation.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.libsillycript.core.HIDDEN_HEADER_DEFAULT_INDEX
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycrypt.R
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import com.dev.sillycrypt.domain.repository.ManageVolumeRepository
import com.dev.sillycrypt.presentation.states.ConfirmDialog
import com.dev.sillycrypt.presentation.states.ErrorState
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sillycrypt.common.text.UIText
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
    private val openVolumeUIStateFlow: MutableStateFlow<OpenVolumeUIState>,
    private val _errorState: MutableStateFlow<ErrorState?>
): ViewModel() {

    val errorState = _errorState.asStateFlow()

    private var openJob: Job? = null

    val state = combine(repository.state, openVolumeUIStateFlow) {
        repositoryState, uiState ->
        when (repositoryState) {
            is VolumeOpeningState.Initial -> {
                OpenVolumeUIState.Initial(repositoryState.data)
            }

            is VolumeOpeningState.SelectedDescriptor -> {
                when (uiState) {
                    is OpenVolumeUIState.OpenVolumeFormState ->
                        uiState.copy(name = repositoryState.name)
                    is OpenVolumeUIState.Initial ->
                        OpenVolumeUIState.OpenVolumeFormState(name = repositoryState.name)
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
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.failed_to_select_file),
                    message = UIText.UsualString(error.stackTraceToString())
                )
            }
        }
    }


    fun closeVolume(id: String) {
        viewModelScope.launch {
            runCatching {
                repository.closeVolume(id)
            }.onFailure { error ->
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.failed_to_close_volume),
                    message = UIText.UsualString(error.stackTraceToString())
                )
            }
        }
    }

    fun updatePassword(value: CharSequence) = updateForm { copy(password = value) }
    fun updateKdf(value: KDFType) = updateForm { copy(kdf = value) }
    fun updateCipher(value: BlockCipherType) = updateForm { copy(cipher = value) }
    fun updateFsType(value: FsType) = updateForm { copy(fsType = value) }
    fun updatePim(value: CharSequence) = updateForm { copy(pim = value) }
    fun updateIndex(value: CharSequence) = updateForm { copy(index = value) }

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

    fun updateHiddenPassword(value: CharSequence) = updateForm { copy(hiddenPassword = value) }
    fun updateHiddenKdf(value: KDFType) = updateForm { copy(hiddenKdf = value) }
    fun updateHiddenCipher(value: BlockCipherType) = updateForm { copy(hiddenCipher = value) }
    fun updateHiddenPim(value: CharSequence) = updateForm { copy(hiddenPim = value) }
    fun updateHiddenIndex(value: CharSequence) = updateForm { copy(hiddenIndex = value) }

    fun onBackPressed(): Boolean {
        return when (val currentState = state.value) {
            is OpenVolumeUIState.Initial -> false
            is OpenVolumeUIState.OpenVolumeFormState -> {
                if (currentState.loading) {
                    openVolumeUIStateFlow.update {
                        check(it is OpenVolumeUIState.OpenVolumeFormState)
                        it.copy(confirmDialog = ConfirmDialog.CancelLoading)
                    }
                } else {
                    openVolumeUIStateFlow.update {
                        check(it is OpenVolumeUIState.OpenVolumeFormState)
                        it.copy(confirmDialog = ConfirmDialog.ExitForm)
                    }
                }
                true
            }
        }
    }


    private fun updateForm(block: OpenVolumeUIState.OpenVolumeFormState.() ->
    OpenVolumeUIState.OpenVolumeFormState) {
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
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.incorrect_volume_params),
                    message = UIText.UsualString(error.message ?: error.stackTraceToString())
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
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.failed_to_open),
                    message = UIText.UsualString(error.stackTraceToString())
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