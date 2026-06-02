package dev.sillycrypt.common.text

import android.content.Context
import androidx.annotation.StringRes

/**
 * Class for operations with usual and resource text
 */
sealed class UIText {
    data class UsualString(val value: String) : UIText()
    class StringResource(@StringRes val id: Int, vararg val arguments: Any) : UIText()

    fun asString(context: Context?): String {
        return when (this) {
            is UsualString -> value
            is StringResource -> context?.getString(id, *arguments)
                ?: throw RuntimeException("context is not provided")
        }
    }
}
