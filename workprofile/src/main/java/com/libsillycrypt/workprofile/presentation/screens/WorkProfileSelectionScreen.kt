package com.libsillycrypt.workprofile.presentation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.libsillycrypt.workprofile.presentation.viewmodels.ManageWorkProfileVM

@Composable
fun WorkProfileSelectionScreen(
    isWorkProfileAvailable: State<Boolean>,
    createProfile: () -> Unit,
    innerPadding: PaddingValues,
    viewModel: ManageWorkProfileVM = hiltViewModel()
) {
    val state = viewModel.state.collectAsState()
    val onPackageInstallChanged = remember {
        viewModel::installOrUninstallApp
    }
    val isWorkProfileOwner = remember { viewModel::isWorkProfileOwner }
    val deleteWorkProfile = remember { viewModel::deleteProfile }

    val deleteProfileFromUser = remember { viewModel::deleteProfileFromUser }
    val refreshApps = remember { viewModel::refreshApps }

    if (isWorkProfileAvailable.value) {
        ManageWorkprofileScreen(
            state.value,
            onPackageInstallChanged,
            innerPadding,
            refreshApps,
            deleteProfileFromUser
        )
    } else {
        if (isWorkProfileOwner()) {
            DeleteWorkProfileScreen(innerPadding, deleteWorkProfile)
        } else {
            CreateWorkProfileScreen(createProfile, innerPadding)
        }
    }
}