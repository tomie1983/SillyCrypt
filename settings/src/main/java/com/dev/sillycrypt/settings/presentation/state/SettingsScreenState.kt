package com.dev.sillycrypt.settings.presentation.state

import com.dev.sillycrypt.settings.domain.entities.ApplicationInfoWithFlag
import kotlinx.collections.immutable.ImmutableList

sealed class SettingsScreenState {
    data object Loading: SettingsScreenState()
    data class Settings(
        val packages: ImmutableList<ApplicationInfoWithFlag>,
        val timeout: Long,
        val showDialog: Boolean
    ): SettingsScreenState()
}