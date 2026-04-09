package com.dev.sillycrypt.presentation.validation

import com.dev.sillycrypt.presentation.states.VolumeCreationForm

data class VolumeCreationFormValidation(
    val sizeError: String? = null,
    val pimError: String? = null,
    val indexError: String? = null
) {
    val isValid: Boolean
        get() = sizeError == null && pimError == null && indexError == null
}

fun validateVolumeCreationForm(form: VolumeCreationForm): VolumeCreationFormValidation {
    val sizeValue = form.size.toLongOrNull()
    val pimValue = form.pim.toIntOrNull()
    val indexValue = form.index.toIntOrNull()

    val sizeError = when {
        form.size.isBlank() -> "Введите size"
        sizeValue == null -> "Size должен быть числом"
        sizeValue <= 0 -> "Size должен быть > 0"
        else -> null
    }

    val pimError = when {
        form.pim.isBlank() -> "Введите PIM"
        pimValue == null -> "PIM должен быть числом"
        pimValue < 0 -> "PIM должен быть >= 0"
        else -> null
    }

    val indexError = when {
        form.index.isBlank() -> "Введите index"
        indexValue == null -> "Index должен быть числом"
        indexValue < 0 -> "Index должен быть >= 0"
        indexValue > 256 -> "Index должен быть <= 256"
        else -> null
    }

    return VolumeCreationFormValidation(
        sizeError = sizeError,
        pimError = pimError,
        indexError = indexError
    )
}