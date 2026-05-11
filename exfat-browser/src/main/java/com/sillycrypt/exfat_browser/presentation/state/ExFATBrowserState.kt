package com.sillycrypt.exfat_browser.presentation.state

import com.dev.exfat.file.ExFATFile
import kotlinx.collections.immutable.ImmutableList

sealed class ExFATBrowserState {
    data object Loading: ExFATBrowserState()
    data class Data(
        val uuid: String,
        val path: String,
        val name: String,
        val files: ImmutableList<ExFATFile>
    ): ExFATBrowserState()
}