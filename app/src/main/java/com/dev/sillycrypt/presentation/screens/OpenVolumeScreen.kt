package com.dev.sillycrypt.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.libsillycrypt.R
import com.dev.sillycrypt.presentation.elements.ConfirmDialog
import com.dev.sillycrypt.presentation.elements.ErrorDialog
import com.dev.sillycrypt.presentation.elements.OpenVolumeFormContent
import com.dev.sillycrypt.presentation.elements.OpenVolumeInitialContent
import com.dev.sillycrypt.presentation.elements.LoadingContent
import com.dev.sillycrypt.presentation.states.ConfirmDialog
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState
import com.dev.sillycrypt.presentation.viewmodels.OpenVolumeVM

@Composable
fun OpenVolumeScreen(
    innerPadding: PaddingValues,
    closeActivity: () -> Unit,
    onVolumeClick: (VeracryptVolumeData) -> Unit,
    viewModel: OpenVolumeVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument()
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
            is OpenVolumeUIState.Initial -> {
                OpenVolumeInitialContent(
                    volumes = uiState.data,
                    onVolumeClick = onVolumeClick,
                    onCloseVolume = viewModel::closeVolume,
                    onAddClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
            }

            is OpenVolumeUIState.OpenVolumeFormState -> {
                if (uiState.loading) {
                    LoadingContent()
                } else {
                    OpenVolumeFormContent(
                        state = uiState,
                        onPasswordChange = viewModel::updatePassword,
                        onKdfChange = viewModel::updateKdf,
                        onCipherChange = viewModel::updateCipher,
                        onFsTypeChange = viewModel::updateFsType,
                        onPimChange = viewModel::updatePim,
                        onIndexChange = viewModel::updateIndex,
                        onHiddenVolumeChange = viewModel::updateIsHiddenVolume,
                        onProtectHiddenChange = viewModel::updateProtectHiddenVolume,
                        onHiddenPasswordChange = viewModel::updateHiddenPassword,
                        onHiddenKdfChange = viewModel::updateHiddenKdf,
                        onHiddenCipherChange = viewModel::updateHiddenCipher,
                        onHiddenPimChange = viewModel::updateHiddenPim,
                        onHiddenIndexChange = viewModel::updateHiddenIndex,
                        onOpenClick = viewModel::openVolume
                    )
                }

                when (uiState.confirmDialog) {
                    ConfirmDialog.ExitForm -> {
                        ConfirmDialog(
                            title = stringResource(R.string.exit_form),
                            message = stringResource(R.string.selected_file_forgotten),
                            confirmText = stringResource(R.string.exit),
                            dismissText = stringResource(R.string.stay),
                            onConfirm = viewModel::confirmExitForm,
                            onDismiss = viewModel::dismissConfirmDialog
                        )
                    }

                    ConfirmDialog.CancelLoading -> {
                        ConfirmDialog(
                            title = stringResource(R.string.cancel_volume_opening),
                            message = stringResource(R.string.volume_would_not_be_opened),
                            confirmText = stringResource(R.string.stop),
                            dismissText = stringResource(R.string.continue_opening),
                            onConfirm = viewModel::confirmCancelLoading,
                            onDismiss = viewModel::dismissConfirmDialog
                        )
                    }

                    null -> Unit
                }
            }
        }
    }

    val context = LocalContext.current

    errorState?.let { error ->
        ErrorDialog(
            title = error.title.asString(context),
            message = error.message.asString(context),
            onDismiss = viewModel::dismissError
        )
    }
}