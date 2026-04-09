package com.dev.sillycrypt.data.repository

import android.content.Context
import android.net.Uri
import com.dev.libsillycript.android.AndroidVeracryptMaster
import com.dev.libsillycript.core.VeracryptData
import com.dev.sillycrypt.data.mapper.UriMapper
import com.dev.sillycrypt.domain.entities.CreateVolumeState
import com.dev.sillycrypt.domain.repository.CreateVolumeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class CreateVolumeRepositoryImpl @Inject constructor(
    private val volumeState: MutableStateFlow<CreateVolumeState>,
    private val master: AndroidVeracryptMaster,
    @ApplicationContext private val context: Context
): CreateVolumeRepository {

    private val mapper = UriMapper(context)

    override suspend fun setInitial() {
        volumeState.value = CreateVolumeState.Initial
    }

    override suspend fun setDescriptor(uri: Uri) {
        val name = mapper.map(uri)
        check( name != null) {
            "Name of file not found"
        }
        volumeState.value = CreateVolumeState.SelectedDescriptor(uri, name)
    }

    override val state: Flow<CreateVolumeState>
        get() = volumeState.asStateFlow()

    override suspend fun createVolume(volumeData: List<VeracryptData>) {
        val currentState = volumeState.value
        check(currentState is CreateVolumeState.SelectedDescriptor) {
            "State is wrong: expected SelectedDescriptor state"
        }
        master.create(currentState.uri,volumeData)
        volumeState.value = CreateVolumeState.Initial
    }
}