package com.dev.sillycrypt.settings.presentation.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dev.sillycrypt.settings.presentation.state.SettingsScreenState
import com.dev.sillycrypt.settings.presentation.viewmodels.SettingsVM
import dev.sillycrypt.common.entities.ApplicationInfoWithData
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    settingsViewModel: SettingsVM = hiltViewModel()
) {
    val state = settingsViewModel.settings.collectAsState()
    val onPackageInstallChanged = remember {
        settingsViewModel::setPackageToInstall
    }
    val onTimeoutChanged = remember {
        settingsViewModel::setTimeoutMillis
    }

    val setDialogState = remember {
        settingsViewModel::setDialogState
    }

    val hideApp = remember {
        settingsViewModel::hideApp
    }

    val settingsState = state.value

    when (settingsState) {
        SettingsScreenState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is SettingsScreenState.Settings -> {
            SettingsContent(
                state = settingsState,
                innerPadding = innerPadding,
                onPackageInstallChanged = onPackageInstallChanged,
                onTimeoutChanged = onTimeoutChanged,
                setDialogState = setDialogState,
                hideApp = hideApp
            )
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsScreenState.Settings,
    innerPadding: PaddingValues,
    onPackageInstallChanged: (app: ApplicationInfo, install: Boolean) -> Unit,
    onTimeoutChanged: (Long) -> Unit,
    setDialogState: (Boolean) -> Unit,
    hideApp: (Boolean) -> Unit
) {
    var timeoutText by remember(state.timeout) {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        SettingsSectionTitle("Key destruction timeout")

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = timeoutText,
            onValueChange = { value ->
                if (value.all { it.isDigit() }) {
                    timeoutText = value
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(state.timeout.toString())
            },
            singleLine = true,
            label = {
                Text("Timeout millis")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    timeoutText.toLongOrNull()?.let(onTimeoutChanged)
                }
            ),
            trailingIcon = {
                TextButton(
                    onClick = {
                        timeoutText.toLongOrNull()?.let(onTimeoutChanged)
                    },
                    enabled = timeoutText.toLongOrNull() != null
                ) {
                    Text("Save")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("Work profile settings")

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    setDialogState(true)
                },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp
        ) {
            Text(
                text = "Choose apps to install",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionTitle("App hiding settings")

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    setDialogState(true)
                },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hide app icon from launcher",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(!state.appIsVisible, hideApp)
            }
        }
    }



    if (state.showDialog) {
        AppsToInstallDialog(
            apps = state.packages,
            onDismiss = {
                setDialogState(false)
            },
            onPackageInstallChanged = onPackageInstallChanged
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun AppsToInstallDialog(
    apps: ImmutableList<ApplicationInfoWithData>,
    onDismiss: () -> Unit,
    onPackageInstallChanged: (app: ApplicationInfo, install: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose apps to install")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = apps,
                    key = { it.applicationInfo.packageName }
                ) { appWithFlag ->
                    AppListItem(
                        modifier = Modifier.animateItem(),
                        appWithFlag = appWithFlag,
                        onPackageInstallChanged = onPackageInstallChanged
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        }
    )
}

