package com.dev.sillycrypt.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.sillycrypt.presentation.elements.ConfirmDialog
import com.dev.sillycrypt.presentation.elements.CreateVolumeFormContent
import com.dev.sillycrypt.presentation.elements.CreateVolumeInitialContent
import com.dev.sillycrypt.presentation.elements.ErrorDialog
import com.dev.sillycrypt.presentation.elements.LoadingContent
import com.dev.sillycrypt.presentation.states.CreateVolumeUIState
import com.dev.sillycrypt.presentation.states.ConfirmDialog
import com.dev.sillycrypt.presentation.viewmodels.CreateVolumeVM

private const val TAG_CREATE = "CreateFileScreen"

@Composable
fun CreateVolumeScreen(
    innerPadding: PaddingValues,
    closeActivity: () -> Unit,
    viewModel: CreateVolumeVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.onFilePicked(uri)
        }
    }

    BackHandler {
        val consumed = viewModel.onBackPressed()
        if (!consumed) {
            closeActivity()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        when (val uiState = state) {
            CreateVolumeUIState.Initial -> {
                CreateVolumeInitialContent(
                    onPickFileClick = {
                        filePickerLauncher.launch("volume.vc")
                    }
                )
            }

            is CreateVolumeUIState.CreateVolumeFormState -> {
                if (uiState.loading) {
                    LoadingContent()
                } else {
                    CreateVolumeFormContent(
                        state = uiState,
                        onPasswordChange = viewModel::updatePassword,
                        onKdfChange = viewModel::updateKdf,
                        onCipherChange = viewModel::updateCipher,
                        onFsTypeChange = viewModel::updateFsType,
                        onPimChange = viewModel::updatePim,
                        onIndexChange = viewModel::updateIndex,
                        onAddLayerClick = viewModel::addVolume,
                        onRemoveLastLayerClick = viewModel::removeLastVolume,
                        onCreateClick = viewModel::createVolume,
                        onSizeChange = { value, index ->
                            viewModel.updateFormSize(value, index)
                        }
                    )
                }

                when (uiState.confirmDialog) {
                    ConfirmDialog.ExitForm -> {
                        ConfirmDialog(
                            title = "Выйти из формы?",
                            message = "Выбранный файл будет сброшен.",
                            confirmText = "Выйти",
                            dismissText = "Остаться",
                            onConfirm = viewModel::confirmExitForm,
                            onDismiss = viewModel::dismissConfirmDialog
                        )
                    }

                    ConfirmDialog.CancelLoading -> {
                        ConfirmDialog(
                            title = "Прервать создание тома?",
                            message = "Создание будет остановлено.",
                            confirmText = "Прервать",
                            dismissText = "Продолжить",
                            onConfirm = viewModel::confirmCancelLoading,
                            onDismiss = viewModel::dismissConfirmDialog
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    errorState?.let { error ->
        ErrorDialog(
            title = error.title,
            message = error.message,
            onDismiss = viewModel::dismissError
        )
    }
}