package dev.sillycrypt.common.elements

import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import dev.sillycrypt.common.R

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OutlinedSecureIncognitoTextField(
    initialText: CharSequence,
    onTextChange: (CharSequence) -> Unit,
    modifier: Modifier = Modifier,
    isObfuscated: Boolean = true,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textObfuscationMode: TextObfuscationMode? = null,
    textObfuscationCharacter: Char = DefaultObfuscationCharacter,
    keyboardOptions: KeyboardOptions = SecureTextFieldKeyboardOptions,
    onKeyboardAction: KeyboardActionHandler? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    interactionSource: MutableInteractionSource? = null,
) {
    var isObfuscated by rememberSaveable { mutableStateOf(isObfuscated) }
    val field = rememberTextFieldState(initialText.toString())
    LaunchedEffect(field.text) {
        onTextChange(field.text)
    }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val modifiedRequest = PlatformTextInputMethodRequest { outAttributes ->
                val inputConnection = request.createInputConnection(outAttributes)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }

                inputConnection
            }

            nextHandler.startInputMethod(modifiedRequest)
        }, {
            OutlinedSecureTextField(
                state = field,
                modifier = modifier,
                label = label,
                enabled = enabled,
                textStyle = textStyle,
                placeholder = placeholder,
                labelPosition = labelPosition,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon?: {
                    IconButton(onClick = { isObfuscated = !isObfuscated }) {
                        Icon(
                            imageVector = if (isObfuscated) {
                                Icons.Default.VisibilityOff
                            } else Icons.Default.Visibility,
                            contentDescription = if (isObfuscated) {
                                stringResource(R.string.hide_value)
                            } else stringResource(R.string.show_value)
                        )
                    }
                },
                supportingText = supportingText,
                suffix = suffix,
                isError = isError,
                prefix = prefix,
                inputTransformation = inputTransformation,
                textObfuscationMode = textObfuscationMode ?: if (isObfuscated) {
                    TextObfuscationMode.Hidden
                } else TextObfuscationMode.Visible,
                keyboardOptions = keyboardOptions,
                textObfuscationCharacter = textObfuscationCharacter,
                onTextLayout = onTextLayout,
                onKeyboardAction = onKeyboardAction,
                contentPadding = contentPadding,
                shape = shape,
                colors = colors,
                interactionSource = interactionSource
            )
        }
    )
}

private val SecureTextFieldKeyboardOptions =
    KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)

private const val DefaultObfuscationCharacter: Char = '\u2022'