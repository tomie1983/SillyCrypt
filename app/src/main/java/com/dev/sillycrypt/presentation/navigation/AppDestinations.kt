package com.dev.sillycrypt.presentation.navigation

import android.net.Uri
import androidx.annotation.StringRes
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.sillycrypt.R

sealed class AppDestination(
    val route: String,
    @StringRes val label: Int
) {
    data object PickFile : AppDestination(
        route = "pick_file",
        label = R.string.pick
    )

    data object ManageFiles : AppDestination(
        route = "manage_files/{volumeUuid}/{volumeName}",
        label = R.string.manage_files
    ) {
        const val ARG_VOLUME_UUID = "volumeUuid"
        const val ARG_VOLUME_NAME = "volumeName"

        fun createRoute(volumeData: VeracryptVolumeData): String {
            return "manage_files/${Uri.encode(volumeData.uuid)}/${Uri.encode(volumeData.name)}"
        }
    }

    data object PickFileNav : AppDestination(
        route = "pick_file_nav",
        label = R.string.pick
    )

    data object CreateFile : AppDestination(
        route = "create_file",
        label = R.string.create
    )

    data object ManageWorkProfile: AppDestination(
        route = "manage_work_profile",
        label = R.string.work_profile
    )

    data object Settings: AppDestination(
        route = "settings",
        label = R.string.settings
    )
}