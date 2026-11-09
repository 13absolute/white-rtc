package com.whitertc.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.presentation.theme.OledTheme

/**
 * Reusable OLED Brutalist Input Field:
 * Clean 1px border, focus border (Dusty Rose), monospace font, 8dp corners.
 */
@Composable
fun OledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    headerRightContent: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null || headerRightContent != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OledTheme.typography.sans,
                        letterSpacing = 1.sp,
                        color = OledTheme.colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                headerRightContent?.invoke()
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = OledTheme.colors.textPrimary
            ),
            placeholder = if (placeholder != null) {
                {
                    Text(
                        text = placeholder,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = OledTheme.colors.textMuted
                    )
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF080808),
                unfocusedContainerColor = Color(0xFF040404),
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = OledTheme.colors.rose,
                unfocusedBorderColor = OledTheme.colors.borderHairline,
                cursorColor = OledTheme.colors.rose,
                errorBorderColor = OledTheme.colors.crimson
            )
        )

        if (supportingText != null) {
            Spacer(modifier = Modifier.height(5.dp))
            supportingText()
        }
    }
}
