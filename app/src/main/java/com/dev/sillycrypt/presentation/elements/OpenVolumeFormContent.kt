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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.R
import com.dev.sillycrypt.presentation.states.OpenVolumeUIState
import dev.sillycrypt.common.elements.OutlinedSecureIncognitoTextField

@Composable
fun OpenVolumeFormContent(
    state: OpenVolumeUIState.OpenVolumeFormState,
    onPasswordChange: (CharSequence) -> Unit,
    onKdfChange: (KDFType) -> Unit,
    onCipherChange: (BlockCipherType) -> Unit,
    onFsTypeChange: (FsType) -> Unit,
    onPimChange: (CharSequence) -> Unit,
    onIndexChange: (CharSequence) -> Unit,
    onHiddenVolumeChange: (Boolean) -> Unit,
    onProtectHiddenChange: (Boolean) -> Unit,
    onHiddenPasswordChange: (CharSequence) -> Unit,
    onHiddenKdfChange: (KDFType) -> Unit,
    onHiddenCipherChange: (BlockCipherType) -> Unit,
    onHiddenPimChange: (CharSequence) -> Unit,
    onHiddenIndexChange: (CharSequence) -> Unit,
    onOpenClick: () -> Unit
) {
    val pimValue = state.pim.toString().toIntOrNull()
    val indexValue = state.index.toString().toIntOrNull()

    val pimError = when {
        state.pim.isBlank() -> stringResource(R.string.please_enter_pim)
        pimValue == null -> stringResource(R.string.pim_not_a_number)
        pimValue < 0 -> stringResource(R.string.pim_is_too_small)
        else -> null
    }

    val indexError = when {
        state.index.isBlank() -> stringResource(R.string.please_enter_index)
        indexValue == null -> stringResource(R.string.index_is_not_a_number)
        indexValue < 0 -> stringResource(R.string.index_too_small)
        indexValue > 256 -> stringResource(R.string.index_too_big)
        else -> null
    }

    val hiddenPimValue = state.hiddenPim.toString().toIntOrNull()
    val hiddenIndexValue = state.hiddenIndex.toString().toIntOrNull()
    val hiddenPimError = when {
        state.hiddenPim.isBlank() -> stringResource(R.string.please_enter_pim)
        hiddenPimValue == null -> stringResource(R.string.pim_not_a_number)
        hiddenPimValue < 0 -> stringResource(R.string.pim_is_too_small)
        else -> null
    }

    val hiddenIndexError = when {
        state.hiddenIndex.isBlank() -> stringResource(R.string.please_enter_index)
        hiddenIndexValue == null -> stringResource(R.string.index_is_not_a_number)
        hiddenIndexValue < 0 -> stringResource(R.string.index_too_small)
        hiddenIndexValue > 256 -> stringResource(R.string.index_too_big)
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
                text = stringResource(R.string.file, state.name),
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            OutlinedSecureIncognitoTextField(
                modifier = Modifier.fillMaxWidth(),
                initialText = state.password,
                onTextChange = onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
            )
        }

        item {
            EnumDropdown(
                label = stringResource(R.string.kdf),
                selected = state.kdf,
                values = KDFType.entries,
                onSelected = onKdfChange
            )
        }

        item {
            EnumDropdown(
                label = stringResource(R.string.encryption_mode),
                selected = state.cipher,
                values = BlockCipherType.entries,
                onSelected = onCipherChange
            )
        }

        item {
            EnumDropdown(
                label = stringResource(R.string.fs_type),
                selected = state.fsType,
                values = FsType.entries,
                onSelected = onFsTypeChange
            )
        }

        item {
            OutlinedSecureIncognitoTextField(
                initialText = state.pim,
                onTextChange = onPimChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pim)) },
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
            OutlinedSecureIncognitoTextField(
                initialText = state.index,
                onTextChange = onIndexChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.index)) },
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
                Text(stringResource(R.string.hidden_volume))
            }
        }

        if (!state.isHiddenVolume) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.protectHiddenVolume,
                        onCheckedChange = onProtectHiddenChange
                    )
                    Text(stringResource(R.string.protect_hidden_volume))
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
                    OutlinedSecureIncognitoTextField(
                        initialText = state.hiddenPassword,
                        onTextChange = onHiddenPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.password)) },
                    )

                    EnumDropdown(
                        label = stringResource(R.string.kdf),
                        selected = state.hiddenKdf,
                        values = KDFType.entries,
                        onSelected = onHiddenKdfChange
                    )

                    EnumDropdown(
                        label = stringResource(R.string.encryption_mode),
                        selected = state.hiddenCipher,
                        values = BlockCipherType.entries,
                        onSelected = onHiddenCipherChange
                    )

                    OutlinedSecureIncognitoTextField(
                        initialText = state.hiddenPim,
                        onTextChange = onHiddenPimChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pim)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = hiddenPimError != null,
                        supportingText = {
                            if (hiddenPimError != null) {
                                Text(hiddenPimError)
                            }
                        }
                    )

                    OutlinedSecureIncognitoTextField(
                        initialText = state.hiddenIndex,
                        onTextChange = onHiddenIndexChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.index)) },
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
                Text(stringResource(R.string.open_volume))
            }
        }
    }
}