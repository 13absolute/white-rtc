package com.whitertc.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whitertc.presentation.theme.OledTheme

@Composable
fun OledOutlinedCard(
    modifier: Modifier = Modifier,
    borderColor: Color = OledTheme.colors.borderDefault,
    backgroundColor: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .background(backgroundColor, shape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}
