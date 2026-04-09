package com.dev.sillycrypt.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.libsillycript.android.AndroidVeracryptMaster
import com.dev.libsillycript.core.VeracryptMode
import com.dev.libsillycript.core.fs.FsType
import com.dev.sillycrypt.data.mapper.UriMapper
import com.dev.sillycrypt.domain.entities.VolumeOpeningState
import com.dev.sillycrypt.domain.repository.ManageVolumeRepository
import com.sillycrypt.exfat_android.provider.ExFatDocumentsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import javax.inject.Inject

class ManageVolumeRepositoryImpl @Inject constructor(
    private val volumeState: MutableStateFlow<VolumeOpeningState>,
    private val master: AndroidVeracryptMaster,
    @ApplicationContext private val context: Context
): ManageVolumeRepository {

    private val mapper = UriMapper(context)

    override val state = combine(volumeState, EXFatVolumesManager.activeVolumes) {
        currentState: VolumeOpeningState, activeVolumes: List<VeracryptVolumeData> ->
        when(currentState) {
            is VolumeOpeningState.SelectedDescriptor -> currentState
            is VolumeOpeningState.Initial -> VolumeOpeningState.Initial(activeVolumes.toImmutableList())
        }
    }

    override suspend fun setInitial() {
        volumeState.value = VolumeOpeningState.Initial()
    }

    override suspend fun setDescriptor(uri: Uri) {
        val name = mapper.map(uri)
        check(name != null) {
             "Name of file not found"
        }
        volumeState.value = VolumeOpeningState.SelectedDescriptor(uri, name)
    }

    override suspend fun openVolume(openVolumeData: VeracryptMode, fsType: FsType) {
        val currentState = volumeState.value
        check(currentState is VolumeOpeningState.SelectedDescriptor) {
            "State is wrong: expected SelectedDescriptor state"
        }
        master.open(
            currentState.uri,
            openVolumeData,
            fsType,
            currentState.name
        )
        ExFatDocumentsProvider.notifyRootsChanged(context, "${context.packageName}.documents")
        volumeState.value = VolumeOpeningState.Initial()
    }

    override suspend fun closeVolume(id: String) {
        EXFatVolumesManager.unregister(id)
        ExFatDocumentsProvider.notifyRootsChanged(context, "${context.packageName}.documents")
    }
}