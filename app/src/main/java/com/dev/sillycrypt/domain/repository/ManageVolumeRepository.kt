package com.dev.sillycrypt.domain.repository

import android.net.Uri
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.fs.FsType
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import kotlinx.coroutines.flow.Flow

interface ManageVolumeRepository {
    suspend fun setInitial()
    suspend fun setDescriptor(uri: Uri)
    suspend fun openVolume(openVolumeData: VeracryptMode, fsType: FsType)

    suspend fun closeVolume(id: String)
    val state: Flow<VolumeOpeningState>
}