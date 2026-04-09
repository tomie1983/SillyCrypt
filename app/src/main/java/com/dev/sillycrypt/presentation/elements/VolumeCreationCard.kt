package com.dev.sillycrypt.presentation.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dev.sillycrypt.presentation.states.VolumeCreationForm
import com.dev.sillycrypt.presentation.validation.VolumeCreationFormValidation

@Composable
fun VolumeCreationCard(
    form: VolumeCreationForm,
    index: Int,
    validation: VolumeCreationFormValidation,
    isLast: Boolean,
    isSingle: Boolean,
    onPasswordChange: (String, Int) -> Unit,
    onKdfChange: (KDFType, Int) -> Unit,
    onCipherChange: (BlockCipherType, Int) -> Unit,
    onFsTypeChange: (FsType, Int) -> Unit,
    onPimChange: (String, Int) -> Unit,
    onIndexChange: (String, Int) -> Unit,
    onSizeChange: (String, Int) -> Unit,
    onAddLayerClick: () -> Unit,
    onRemoveLastLayerClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (form.isHiddenVolume) {
                    "Скрытый слой ${index + 1}"
                } else {
                    "Внешний слой"
                },
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = form.size,
                onValueChange = { onSizeChange(it, index) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Размер") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validation.sizeError != null,
                supportingText = {
                    validation.sizeError?.let { Text(it) }
                }
            )

            OutlinedTextField(
                value = form.password,
                onValueChange = { onPasswordChange(it, index) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation()
            )

            EnumDropdown(
                label = "KDF",
                selected = form.kdf,
                values = KDFType.entries,
                onSelected = { onKdfChange(it, index) }
            )

            EnumDropdown(
                label = "Шифр",
                selected = form.cipher,
                values = BlockCipherType.entries,
                onSelected = { onCipherChange(it, index) }
            )

            EnumDropdown(
                label = "FsType",
                selected = form.fsType,
                values = FsType.entries,
                onSelected = { onFsTypeChange(it, index) }
            )

            OutlinedTextField(
                value = form.pim,
                onValueChange = { onPimChange(it, index) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PIM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validation.pimError != null,
                supportingText = {
                    validation.pimError?.let { Text(it) }
                }
            )

            OutlinedTextField(
                value = form.index,
                onValueChange = { onIndexChange(it, index) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Index") },
                readOnly = form.isIndexFixed,
                enabled = !form.isIndexFixed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validation.indexError != null,
                supportingText = {
                    validation.indexError?.let { Text(it) }
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = form.isHiddenVolume,
                    onCheckedChange = null
                )
                Text("Скрытый слой")
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLast) {
                    Button(onClick = onAddLayerClick) {
                        Text("Добавить слой")
                    }
                }

                if (isLast && !isSingle) {
                    OutlinedButton(onClick = onRemoveLastLayerClick) {
                        Text("Удалить слой")
                    }
                }
            }
        }
    }
}