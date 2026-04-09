package com.dev.sillycrypt.domain.entities

import android.net.Uri
import android.os.ParcelFileDescriptor

sealed class CreateVolumeState {
    data object Initial: CreateVolumeState()
    data class SelectedDescriptor(val uri: Uri, val name: String): CreateVolumeState()
}