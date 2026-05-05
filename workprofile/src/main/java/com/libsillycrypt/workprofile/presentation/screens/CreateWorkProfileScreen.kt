package com.libsillycrypt.workprofile.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.libsillycrypt.workprofile.R

@Composable
fun CreateWorkProfileScreen(
    createWorkProfile: () -> Unit,
    innerPadding: PaddingValues
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = createWorkProfile) {
            Text(stringResource(R.string.create_workprofile))
        }
    }
}