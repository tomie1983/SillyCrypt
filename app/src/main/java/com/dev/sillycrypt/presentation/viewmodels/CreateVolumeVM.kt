package com.dev.sillycrypt.presentation.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.libsillycript.core.HIDDEN_HEADER_DEFAULT_INDEX
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.libsillycrypt.R
import com.dev.sillycrypt.domain.entities.CreateVolumeState
import com.dev.sillycrypt.domain.repository.CreateVolumeRepository
import com.dev.sillycrypt.presentation.states.CreateVolumeUIState
import com.dev.sillycrypt.presentation.states.ErrorState
import com.dev.sillycrypt.presentation.states.ConfirmDialog
import com.dev.sillycrypt.presentation.states.VolumeCreationForm
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sillycrypt.common.text.UIText
import kotlinx.collections.immutable.toImmutableList
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
class CreateVolumeVM @Inject constructor(
    private val repository: CreateVolumeRepository,
    private val _errorState: MutableStateFlow<ErrorState?>
): ViewModel() {

    private val createVolumeUIStateFLow = MutableStateFlow<CreateVolumeUIState>(
        CreateVolumeUIState.Initial
    )

    val errorState = _errorState.asStateFlow()

    private var openJob: Job? = null

    val state = combine(repository.state, createVolumeUIStateFLow) { repositoryState, uiState ->
        when (repositoryState) {
            is CreateVolumeState.Initial -> {
                CreateVolumeUIState.Initial
            }

            is CreateVolumeState.SelectedDescriptor -> {
                when (uiState) {
                    is CreateVolumeUIState.CreateVolumeFormState -> uiState.copy(name = repositoryState.name)
                    is CreateVolumeUIState.Initial -> CreateVolumeUIState.CreateVolumeFormState(
                        name = repositoryState.name)
                }
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CreateVolumeUIState.Initial
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
                if (repoState is CreateVolumeState.SelectedDescriptor) {
                    createVolumeUIStateFLow.value =
                        CreateVolumeUIState.CreateVolumeFormState(name = repoState.name)
                }
            }.onFailure { error ->
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.file_selection_error),
                    message = UIText.UsualString(error.stackTraceToString())
                )
            }
        }
    }

    fun onBackPressed(): Boolean {
        return when (val currentState = state.value) {
            is CreateVolumeUIState.Initial -> false
            is CreateVolumeUIState.CreateVolumeFormState -> {
                if (currentState.loading) {
                    createVolumeUIStateFLow.update {
                        check(it is CreateVolumeUIState.CreateVolumeFormState)
                        it.copy(confirmDialog = ConfirmDialog.CancelLoading)
                    }
                } else {
                    createVolumeUIStateFLow.update {
                        check(it is CreateVolumeUIState.CreateVolumeFormState)
                        it.copy(confirmDialog = ConfirmDialog.ExitForm)
                    }
                }
                true
            }
        }
    }

    fun updatePassword(value: String, index: Int) = updateForm(index) { copy(password = value) }
    fun updateKdf(value: KDFType, index: Int) = updateForm(index) { copy(kdf = value) }
    fun updateCipher(value: BlockCipherType, index: Int) = updateForm(index) { copy(cipher = value) }
    fun updateFsType(value: FsType, index: Int) = updateForm(index) { copy(fsType = value) }
    fun updatePim(value: String, index: Int) = updateForm(index) { copy(pim = value.filter { it.isDigit() }) }
    fun updateIndex(value: String, index: Int) = updateForm(index) { copy(index = value.filter { it.isDigit() }) }

    fun updateFormSize(value: String, index: Int) = updateForm(index) {
        copy(size = value.filter { it.isDigit() })
    }

    fun addVolume() {
        createVolumeUIStateFLow.update { state ->
            if (state is CreateVolumeUIState.CreateVolumeFormState) {
                val forms = state.forms.toMutableList()
                val isIndexFixed = forms.size == 1
                val index = if (forms.size == 1) {
                    HIDDEN_HEADER_DEFAULT_INDEX.toString()
                } else {
                    "0"
                }

                val isHiddenVolume = forms.isNotEmpty()

                val newEmptyForm = VolumeCreationForm(
                    isIndexFixed = isIndexFixed,
                    index = index,
                    isHiddenVolume = isHiddenVolume
                )
                forms.add(newEmptyForm)
                state.copy(
                    forms = forms.toImmutableList()
                )
            } else {
                state
            }
        }
    }

    fun removeLastVolume() {
        createVolumeUIStateFLow.update { state ->
            if (state is CreateVolumeUIState.CreateVolumeFormState) {
                val forms = state.forms.toMutableList()
                forms.removeLastOrNull()
                state.copy(
                    forms = forms.toImmutableList()
                )
            } else {
                state
            }
        }
    }

    private fun updateForm(index: Int, block: VolumeCreationForm.() -> VolumeCreationForm) {
        createVolumeUIStateFLow.update { state ->
            if (state is CreateVolumeUIState.CreateVolumeFormState) {
                state.copy(
                    forms = state.forms.mapIndexed { i, form ->
                        if (i == index) {
                            form.block()
                        } else {
                            form
                        }
                    }.toImmutableList()
                )
            } else {
                state
            }
        }
    }

    fun createVolume() {
        val current = state.value
        check(current is CreateVolumeUIState.CreateVolumeFormState)

        val data = runCatching { current.forms.map { it.toVeracryptData() } }
            .getOrElse { error ->
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.incorrect_volume_params),
                    message = UIText.UsualString(error.message ?: error.stackTraceToString())
                )
                return
            }
        openJob?.cancel()
        openJob = viewModelScope.launch {
            createVolumeUIStateFLow.update {
                check(it is CreateVolumeUIState.CreateVolumeFormState)
                it.copy(loading = true, confirmDialog = null)
            }
            runCatching {
                repository.createVolume(data)
            }.onSuccess {
                createVolumeUIStateFLow.value = CreateVolumeUIState.Initial
            }.onFailure { error ->
                createVolumeUIStateFLow.update {
                    check(it is CreateVolumeUIState.CreateVolumeFormState)
                    it.copy(loading = false, confirmDialog = null)
                }
                _errorState.value = ErrorState(
                    title = UIText.StringResource(R.string.failed_to_create_volume),
                    message = UIText.UsualString(error.stackTraceToString())
                )
            }
        }
    }

    fun dismissConfirmDialog() {
        createVolumeUIStateFLow.update {
            check(it is CreateVolumeUIState.CreateVolumeFormState)
            it.copy(confirmDialog = null)
        }
    }

    fun confirmExitForm() {
        viewModelScope.launch {
            repository.setInitial()
            createVolumeUIStateFLow.value = CreateVolumeUIState.Initial
        }
    }

    fun confirmCancelLoading() {
        openJob?.cancel()
        createVolumeUIStateFLow.update {
            check(it is CreateVolumeUIState.CreateVolumeFormState)
            it.copy(
                loading = false,
                confirmDialog = null
            )
        }
    }

}