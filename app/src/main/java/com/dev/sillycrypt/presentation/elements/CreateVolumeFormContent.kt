package com.dev.sillycrypt.presentation.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.libsillycript.core.blockCiphers.BlockCipherType
import com.dev.libsillycript.core.fs.FsType
import com.dev.libsillycript.core.kdfs.KDFType
import com.dev.sillycrypt.presentation.states.CreateVolumeUIState
import com.dev.sillycrypt.presentation.validation.validateVolumeCreationForm

@Composable
fun CreateVolumeFormContent(
    state: CreateVolumeUIState.CreateVolumeFormState,
    onPasswordChange: (String, Int) -> Unit,
    onKdfChange: (KDFType, Int) -> Unit,
    onCipherChange: (BlockCipherType, Int) -> Unit,
    onFsTypeChange: (FsType, Int) -> Unit,
    onPimChange: (String, Int) -> Unit,
    onIndexChange: (String, Int) -> Unit,
    onSizeChange: (String, Int) -> Unit,
    onAddLayerClick: () -> Unit,
    onRemoveLastLayerClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    val formsValidation = state.forms.map { form -> validateVolumeCreationForm(form) }
    val createEnabled = formsValidation.all { it.isValid } &&
            state.forms.all { it.password.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Файл: ${state.name}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        itemsIndexed(
            items = state.forms,
            key = { index, _ -> index }
        ) { index, form ->
            val validation = formsValidation[index]
            val isLast = index == state.forms.lastIndex
            val isSingle = state.forms.size == 1

            VolumeCreationCard(
                form = form,
                index = index,
                validation = validation,
                isLast = isLast,
                isSingle = isSingle,
                onPasswordChange = onPasswordChange,
                onKdfChange = onKdfChange,
                onCipherChange = onCipherChange,
                onFsTypeChange = onFsTypeChange,
                onPimChange = onPimChange,
                onIndexChange = onIndexChange,
                onSizeChange = onSizeChange,
                onAddLayerClick = onAddLayerClick,
                onRemoveLastLayerClick = onRemoveLastLayerClick
            )
        }

        item {
            Button(
                onClick = onCreateClick,
                enabled = createEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать том")
            }
        }
    }
}