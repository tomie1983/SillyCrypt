package com.libsillycrypt.workprofile.presentation.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.libsillycrypt.workprofile.presentation.viewmodels.ManageWorkProfileVM

@Composable
fun WorkProfileSelectionScreen(
    isWorkProfileAvailable: State<Boolean>,
    createProfile: () -> Unit,
    deleteProfileFromUser: () -> Unit,
    innerPadding: PaddingValues,
    viewModel: ManageWorkProfileVM = hiltViewModel()
) {

    val isWorkProfileOwner = remember { viewModel::isWorkProfileOwner }
    val deleteWorkProfile = remember { viewModel::deleteProfile }

    if (isWorkProfileAvailable.value) {
        ManageWorkprofileScreen(innerPadding, deleteProfileFromUser)
    } else {
        if (isWorkProfileOwner()) {
            DeleteWorkProfileScreen(innerPadding, deleteWorkProfile)
        } else {
            CreateWorkProfileScreen(createProfile, innerPadding)
        }
    }
}