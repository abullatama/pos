package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

/**
 * Input teks standar A3.
 *
 * Label selalu di atas input (tidak placeholder-as-label). Helper dan error inline.
 */
@Composable
fun A3TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    textStyle: TextStyle = A3Theme.typography.body
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val hasError = error != null
    val borderColor = when {
        hasError -> colors.status.danger.text
        value.isNotEmpty() -> colors.ink
        else -> colors.border
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        label?.let {
            Text(
                text = it,
                color = colors.ink,
                style = A3Theme.typography.label,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shapes.inputShape)
                .background(colors.canvas)
                .border(
                    width = if (hasError) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = shapes.inputShape
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = textStyle.copy(color = colors.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        color = colors.inkMuted,
                        style = textStyle
                    )
                }
                innerTextField()
            }
        )

        when {
            error != null -> Text(
                text = error,
                color = colors.status.danger.text,
                style = A3Theme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            helper != null -> Text(
                text = helper,
                color = colors.inkMuted,
                style = A3Theme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
