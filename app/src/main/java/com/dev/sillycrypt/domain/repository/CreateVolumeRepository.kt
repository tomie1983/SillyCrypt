package com.dev.sillycrypt.domain.repository

import android.net.Uri
import com.dev.libsillycript.core.VeracryptData
import com.dev.sillycrypt.domain.entities.CreateVolumeState
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import kotlinx.coroutines.flow.Flow

interface CreateVolumeRepository {
    suspend fun setInitial()
    suspend fun setDescriptor(uri: Uri)
    val state: Flow<CreateVolumeState>
    suspend fun createVolume(volumeData: List<VeracryptData>)
}