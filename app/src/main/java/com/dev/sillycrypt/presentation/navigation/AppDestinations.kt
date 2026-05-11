package com.dev.sillycrypt.presentation.navigation

import android.net.Uri
import com.dev.exfat.exfat.VeracryptVolumeData


sealed class AppDestination(
    val route: String,
    val label: String
) {
    data object PickFile : AppDestination(
        route = "pick_file",
        label = "Pick"
    )

    data object ManageFiles : AppDestination(
        route = "manage_files/{volumeUuid}/{volumeName}",
        label = "Manage files"
    ) {
        const val ARG_VOLUME_UUID = "volumeUuid"
        const val ARG_VOLUME_NAME = "volumeName"

        fun createRoute(volumeData: VeracryptVolumeData): String {
            return "manage_files/${Uri.encode(volumeData.uuid)}/${Uri.encode(volumeData.name)}"
        }
    }

    data object PickFileNav : AppDestination(
        route = "pick_file_nav",
        label = "Pick"
    )

    data object CreateFile : AppDestination(
        route = "create_file",
        label = "Create"
    )

    data object ManageWorkProfile: AppDestination(
        route = "manage_work_profile",
        label = "Work profile"
    )

    data object Settings: AppDestination(
        route = "settings",
        label = "Settings"
    )
}