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
        val (descriptor, name) = context.getFileInfoFromUri(uri)
        check(descriptor != null && name != null) {
             "Descriptor or name of file not found"
        }
        volumeState.value = VolumeOpeningState.SelectedDescriptor(descriptor, name)
    }

    override suspend fun openVolume(openVolumeData: VeracryptMode, fsType: FsType) {
        val currentState = volumeState.value
        check(currentState is VolumeOpeningState.SelectedDescriptor) {
            "State is wrong: expected SelectedDescriptor state"
        }
        master.open(
            currentState.descriptor,
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

    fun Context.getFileInfoFromUri(uri: Uri): Pair<ParcelFileDescriptor?, String?> {
        val pfd = contentResolver.openFileDescriptor(uri, "rw")
        val fileName = queryFileName(uri)
        return pfd to fileName
    }

    fun Context.queryFileName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment
    }
}