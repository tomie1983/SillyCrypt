package com.dev.sillycrypt.presentation.navigation


sealed class AppDestination(
    val route: String,
    val label: String
) {
    data object PickFile : AppDestination(
        route = "pick_file",
        label = "Pick"
    )

    data object CreateFile : AppDestination(
        route = "create_file",
        label = "Create"
    )
}