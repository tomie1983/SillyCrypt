package com.dev.sillycrypt.presentation.elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState

@Composable
fun OpenVolumeFormContent(
    state: OpenVolumeUIState.OpenVolumeFormState,
    onPasswordChange: (String) -> Unit,
    onKdfChange: (KDFType) -> Unit,
    onCipherChange: (BlockCipherType) -> Unit,
    onFsTypeChange: (FsType) -> Unit,
    onPimChange: (String) -> Unit,
    onIndexChange: (String) -> Unit,
    onHiddenVolumeChange: (Boolean) -> Unit,
    onProtectHiddenChange: (Boolean) -> Unit,
    onHiddenPasswordChange: (String) -> Unit,
    onHiddenKdfChange: (KDFType) -> Unit,
    onHiddenCipherChange: (BlockCipherType) -> Unit,
    onHiddenPimChange: (String) -> Unit,
    onHiddenIndexChange: (String) -> Unit,
    onOpenClick: () -> Unit
) {
    val pimValue = state.pim.toIntOrNull()
    val indexValue = state.index.toIntOrNull()

    val pimError = when {
        state.pim.isBlank() -> "Введите PIM"
        pimValue == null -> "PIM должен быть числом"
        pimValue < 0 -> "PIM должен быть >= 0"
        else -> null
    }

    val indexError = when {
        state.index.isBlank() -> "Введите index"
        indexValue == null -> "Index должен быть числом"
        indexValue < 0 -> "Index должен быть >= 0"
        indexValue > 256 -> "Index должен быть <= 256"
        else -> null
    }

    val hiddenPimValue = state.hiddenPim.toIntOrNull()
    val hiddenIndexValue = state.hiddenIndex.toIntOrNull()

    val hiddenPimError = when {
        state.hiddenPim.isBlank() -> "Введите PIM скрытого тома"
        hiddenPimValue == null -> "PIM скрытого тома должен быть числом"
        hiddenPimValue < 0 -> "PIM скрытого тома должен быть >= 0"
        else -> null
    }

    val hiddenIndexError = when {
        state.hiddenIndex.isBlank() -> "Введите index скрытого тома"
        hiddenIndexValue == null -> "Index скрытого тома должен быть числом"
        hiddenIndexValue < 0 -> "Index скрытого тома должен быть >= 0"
        hiddenIndexValue > 256 -> "Index скрытого тома должен быть <= 256"
        else -> null
    }

    val mainValid = state.password.isNotBlank() &&
            pimError == null &&
            indexError == null

    val hiddenValid = if (!state.isHiddenVolume && state.protectHiddenVolume) {
        state.hiddenPassword.isNotBlank() &&
                hiddenPimError == null &&
                hiddenIndexError == null
    } else {
        true
    }

    val openEnabled = mainValid && hiddenValid

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Файл: ${state.name}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation()
            )
        }

        item {
            EnumDropdown(
                label = "KDF",
                selected = state.kdf,
                values = KDFType.entries,
                onSelected = onKdfChange
            )
        }

        item {
            EnumDropdown(
                label = "Шифр",
                selected = state.cipher,
                values = BlockCipherType.entries,
                onSelected = onCipherChange
            )
        }

        item {
            EnumDropdown(
                label = "FsType",
                selected = state.fsType,
                values = FsType.entries,
                onSelected = onFsTypeChange
            )
        }

        item {
            OutlinedTextField(
                value = state.pim,
                onValueChange = onPimChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PIM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = pimError != null,
                supportingText = {
                    if (pimError != null) {
                        Text(pimError)
                    }
                }
            )
        }

        item {
            OutlinedTextField(
                value = state.index,
                onValueChange = onIndexChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Index") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = indexError != null,
                supportingText = {
                    if (indexError != null) {
                        Text(indexError)
                    }
                }
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.isHiddenVolume,
                    onCheckedChange = onHiddenVolumeChange
                )
                Text("Скрытый том")
            }
        }

        if (!state.isHiddenVolume) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.protectHiddenVolume,
                        onCheckedChange = onProtectHiddenChange
                    )
                    Text("Защитить скрытый том")
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = !state.isHiddenVolume && state.protectHiddenVolume,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.hiddenPassword,
                        onValueChange = onHiddenPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль скрытого тома") },
                        visualTransformation = PasswordVisualTransformation()
                    )

                    EnumDropdown(
                        label = "KDF скрытого тома",
                        selected = state.hiddenKdf,
                        values = KDFType.entries,
                        onSelected = onHiddenKdfChange
                    )

                    EnumDropdown(
                        label = "Шифр скрытого тома",
                        selected = state.hiddenCipher,
                        values = BlockCipherType.entries,
                        onSelected = onHiddenCipherChange
                    )

                    OutlinedTextField(
                        value = state.hiddenPim,
                        onValueChange = onHiddenPimChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIM скрытого тома") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = hiddenPimError != null,
                        supportingText = {
                            if (hiddenPimError != null) {
                                Text(hiddenPimError)
                            }
                        }
                    )

                    OutlinedTextField(
                        value = state.hiddenIndex,
                        onValueChange = onHiddenIndexChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Index скрытого тома") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = hiddenIndexError != null,
                        supportingText = {
                            if (hiddenIndexError != null) {
                                Text(hiddenIndexError)
                            }
                        }
                    )
                }
            }
        }

        item {
            Button(
                onClick = onOpenClick,
                enabled = openEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть том")
            }
        }
    }
}