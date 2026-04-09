package com.dev.sillycrypt.presentation.states

sealed interface ConfirmDialog {
    data object ExitForm : ConfirmDialog
    data object CancelLoading : ConfirmDialog
}