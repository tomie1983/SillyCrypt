package com.dev.sillycrypt.domain.entities

import android.os.ParcelFileDescriptor
import com.dev.exfat.exfat.VeracryptVolumeData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed class VolumeOpeningState {
    data class Initial(val data: ImmutableList<VeracryptVolumeData> = persistentListOf<VeracryptVolumeData>()): VolumeOpeningState()
    data class SelectedDescriptor(val descriptor: ParcelFileDescriptor, val name: String): VolumeOpeningState()
}