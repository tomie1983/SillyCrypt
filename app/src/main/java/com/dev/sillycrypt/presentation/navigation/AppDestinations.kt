package com.dev.sillycrypt.presentation.navigation


sealed class AppDestination(
    val route: String,
    val label: String
) {
    data object PickFile : AppDestination(
        route = "pick_file",
        label = "Pick"
    )

    data object ManageFiles : AppDestination(
        route = "manage_files",
        label = "Manage files"
    )

    data object PickFileNav : AppDestination(
        route = "pick_file_nav",
        label = "Pick"
    )

    data object CreateFile : AppDestination(
        route = "create_file",
        label = "Create"
    )

    data object ManageWorkProfile: AppDestination(
        route = "manage_work_frofile",
        label = "Work profile"
    )

    data object Settings: AppDestination(
        route = "settings",
        label = "Settings"
    )
}