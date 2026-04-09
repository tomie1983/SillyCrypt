package com.dev.sillycrypt.presentation.states

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed class CreateVolumeUIState{
    data object Initial: CreateVolumeUIState()
    data class CreateVolumeFormState(
        val name: String,
        val forms: ImmutableList<VolumeCreationForm> = persistentListOf(VolumeCreationForm()),
        val confirmDialog: ConfirmDialog? = null,
        val loading: Boolean = false
    ): CreateVolumeUIState()
}