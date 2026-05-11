package com.sillycrypt.exfat_browser.domain.repository

import android.net.Uri
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.exfat.file.ExFATFile
import com.sillycrypt.exfat_browser.domain.entities.BrowserDisplayEntry
import kotlinx.coroutines.flow.Flow

interface ExFATBrowserRepository {
    suspend fun setSelectedVolume(volumeData: VeracryptVolumeData)
    suspend fun deleteFile(file: ExFATFile)
    suspend fun createFile(name: String)
    suspend fun createDir(name: String)
    suspend fun copyFile(name: String, uri: Uri)
    val selectedVolume: Flow<VeracryptVolumeData>
    suspend fun listFiles(file: ExFATFile)
    val displayedStateFlow: Flow<BrowserDisplayEntry>
}