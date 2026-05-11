package com.sillycrypt.exfat_browser.domain.entities

import com.dev.exfat.file.ExFATFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class BrowserDisplayEntry(
    val path: String,
    val files: ImmutableList<ExFATFile>
)