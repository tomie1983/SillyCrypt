package com.dev.sillycrypt.presentation.states

sealed interface OpenVolumeConfirmDialog {
    data object ExitForm : OpenVolumeConfirmDialog
    data object CancelLoading : OpenVolumeConfirmDialog
}