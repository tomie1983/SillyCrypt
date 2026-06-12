package com.dev.sillycrypt.settings.presentation.state

import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList

sealed class SettingsScreenState {
    data object Loading: SettingsScreenState()
    data class Settings(
        val packages: ImmutableList<ApplicationInfoWithData>,
        val timeout: Long,
        val appIsVisible: Boolean,
        val isScreenshotsAllowed: Boolean,
        val showDialog: Boolean
    ): SettingsScreenState()
}