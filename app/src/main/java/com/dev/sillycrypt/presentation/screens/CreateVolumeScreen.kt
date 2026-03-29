package com.dev.sillycrypt.presentation.screens

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val TAG_CREATE = "CreateFileScreen"

@Composable
fun CreateVolumeScreen(
    innerPadding: PaddingValues
) {
    val context = LocalContext.current

    val createLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) {
            Log.d(TAG_CREATE, "Создание файла отменено")
            return@rememberLauncherForActivityResult
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        try {
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                Log.d(TAG_CREATE, "Persistable permission granted for created uri=$uri")
            } catch (e: SecurityException) {
                Log.w(TAG_CREATE, "Persistable permission not granted for created uri=$uri", e)
            }

            val pfd: ParcelFileDescriptor? =
                context.contentResolver.openFileDescriptor(uri, "rw")

            if (pfd != null) {
                Log.d(
                    TAG_CREATE,
                    "RW descriptor obtained for created file. uri=$uri, fd=${pfd.fd}, statSize=${safeStatSize(pfd)}"
                )
                pfd.close()
            } else {
                Log.e(TAG_CREATE, "openFileDescriptor returned null for created uri=$uri")
            }
        } catch (e: Exception) {
            Log.e(TAG_CREATE, "Failed to obtain rw descriptor for created uri=$uri", e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Text(
            text = "Вкладка создания файла",
            modifier = Modifier.align(Alignment.Center)
        )

        FloatingActionButton(
            onClick = {
                createLauncher.launch("new_file.bin")
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create file"
            )
        }
    }
}

private fun safeStatSize(pfd: ParcelFileDescriptor): Long? {
    return try {
        pfd.statSize.takeIf { it >= 0 }
    } catch (_: Throwable) {
        null
    }
}