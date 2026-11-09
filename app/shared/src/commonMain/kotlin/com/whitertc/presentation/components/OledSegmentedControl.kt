package com.whitertc.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.presentation.theme.OledTheme

/**
 * Reusable OLED Brutalist Segmented Control:
 * Monolithic segmented switch with sharp/subtle 8dp corners, 1px border.
 * Active segment highlighted with Dusty Rose border and elevated background.
 */
@Composable
fun <T> OledSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF080808))
            .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(8.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val activeBorder = if (isSelected) OledTheme.colors.rose else Color.Transparent
            val activeBg = if (isSelected) Color(0xFF141416) else Color.Transparent
            val textColor = if (isSelected) OledTheme.colors.textPrimary else OledTheme.colors.textMuted
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(activeBg)
                    .border(1.dp, activeBorder, RoundedCornerShape(6.dp))
                    .clickable { onItemSelected(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = itemLabel(item),
                    fontSize = 11.sp,
                    fontWeight = fontWeight,
                    fontFamily = OledTheme.typography.mono,
                    letterSpacing = 0.5.sp,
                    color = textColor
                )
            }
        }
    }
}
