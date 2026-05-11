package com.sillycrypt.exfat_browser.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.exfat.file.ExFATFile
import com.sillycrypt.exfat_browser.presentation.state.ExFATBrowserState
import com.sillycrypt.exfat_browser.presentation.viewModel.ExFatBrowserVM

@Composable
fun ExFatBrowserRoute(
    volumeData: VeracryptVolumeData,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ExFatBrowserVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(volumeData.uuid) {
        viewModel.setSelectedVolume(volumeData)
    }

    ExFatBrowserScreen(
        modifier = modifier,
        state = state,
        innerPadding = innerPadding,
        onDirectoryClick = viewModel::listFiles,
        onCreateFile = viewModel::createFile,
        onCreateDirectory = viewModel::createDirectory,
        onCopyFile = viewModel::copyFile,
        onDeleteFile = viewModel::deleteFile
    )
}

@Composable
fun ExFatBrowserScreen(
    state: ExFATBrowserState,
    innerPadding: PaddingValues,
    onDirectoryClick: (ExFATFile) -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateDirectory: (String) -> Unit,
    onCopyFile: (uri: Uri, name: String) -> Unit,
    onDeleteFile: (ExFATFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var fabExpanded by remember { mutableStateOf(false) }
    var createDialog by remember { mutableStateOf<CreateDialogType?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val name = context.getFileNameFromUri(uri) ?: "imported-file"
        onCopyFile(uri, name)
    }

    Box(
        modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.BottomEnd
    ) {
        when (state) {
            ExFATBrowserState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ExFATBrowserState.Data -> {
                ExFatBrowserContent(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = state,
                    onDirectoryClick = onDirectoryClick,
                    onFileClick = { file ->
                        context.openExFatFileForEdit(
                            volumeUuid = state.uuid,
                            file = file
                        )
                    },
                    onDeleteFile = onDeleteFile
                )
                ExFatBrowserFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    onCreateFileClick = {
                        fabExpanded = false
                        createDialog = CreateDialogType.File
                    },
                    onCreateDirectoryClick = {
                        fabExpanded = false
                        createDialog = CreateDialogType.Directory
                    },
                    onCopyFileClick = {
                        fabExpanded = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    createDialog?.let { type ->
        CreateEntryDialog(
            type = type,
            onDismiss = { createDialog = null },
            onConfirm = { name ->
                when (type) {
                    CreateDialogType.File -> onCreateFile(name)
                    CreateDialogType.Directory -> onCreateDirectory(name)
                }
                createDialog = null
            }
        )
    }
}

@Composable
private fun ExFatBrowserContent(
    state: ExFATBrowserState.Data,
    onDirectoryClick: (ExFATFile) -> Unit,
    onFileClick: (ExFATFile) -> Unit,
    onDeleteFile: (ExFATFile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = state.path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(
                items = state.files,
                key = { it.path }
            ) { file ->
                ExFatFileListItem(
                    file = file,
                    onClick = {
                        if (file.isDirectory) {
                            onDirectoryClick(file)
                        } else {
                            onFileClick(file)
                        }
                    },
                    onDeleteIconClick = onDeleteFile
                )
            }
        }
    }
}

@Composable
private fun ExFatFileListItem(
    file: ExFATFile,
    onClick: () -> Unit,
    onDeleteIconClick: (ExFATFile) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.InsertDriveFile
                },
                contentDescription = null
            )
        },
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (!file.isDirectory) {
                Text(formatBytes(file.dataLength))
            }
        },
        trailingContent = {
            Icon(
                modifier = Modifier.clickable {
                  onDeleteIconClick(file)
                },
                imageVector = Icons.Filled.Delete,
                contentDescription = null
            )
        }
    )
}

@Composable
private fun ExFatBrowserFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateFileClick: () -> Unit,
    onCreateDirectoryClick: () -> Unit,
    onCopyFileClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "fabRotation"
    )

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onCreateFileClick,
                    icon = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    text = {
                        Text("Создать файл")
                    }
                )

                ExtendedFloatingActionButton(
                    onClick = onCreateDirectoryClick,
                    icon = {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    },
                    text = {
                        Text("Создать папку")
                    }
                )

                ExtendedFloatingActionButton(
                    onClick = onCopyFileClick,
                    icon = {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                    },
                    text = {
                        Text("Скопировать файл")
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) }
        ) {
            Icon(
                modifier = Modifier.rotate(rotation),
                imageVector = Icons.Default.Add,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun CreateEntryDialog(
    type: CreateDialogType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    val title = when (type) {
        CreateDialogType.File -> "Создать файл"
        CreateDialogType.Directory -> "Создать папку"
    }

    val label = when (type) {
        CreateDialogType.File -> "Название файла"
        CreateDialogType.Directory -> "Название папки"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(label)
                },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim())
                }
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private enum class CreateDialogType {
    File,
    Directory
}

private fun Context.openExFatFileForEdit(
    volumeUuid: String,
    file: ExFATFile
) {
    val editUri = Uri.parse("$volumeUuid:${file.path}")

    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(editUri, "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    try {
        startActivity(Intent.createChooser(intent, "Открыть файл"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.getFileNameFromUri(uri: Uri): String? {
    if (uri.scheme == "content") {
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor.use {
            if (it != null && it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return it.getString(index)
                }
            }
        }
    }

    return uri.lastPathSegment
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)

    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)

    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}