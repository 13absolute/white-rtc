package com.whitertc.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.domain.model.TunnelStats
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@Composable
fun TelemetryDetailCard(
    stats: TunnelStats,
    onOpenDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OledOutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onOpenDiagnostics != null) Modifier.clickable(onClick = onOpenDiagnostics) else Modifier),
        borderColor = OledTheme.colors.borderHairline,
        contentPadding = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.telemetry_network_title),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = OledTheme.colors.textMuted,
                    letterSpacing = 1.sp
                )
                if (onOpenDiagnostics != null) {
                    Text(
                        text = stringResource(Res.string.telemetry_details_btn),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OledTheme.colors.cyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = stringResource(Res.string.telemetry_in), value = formatPacketCount(stats.packetsIn))
                MetricItem(label = stringResource(Res.string.telemetry_out), value = formatPacketCount(stats.packetsOut))
                MetricItem(label = stringResource(Res.string.telemetry_framing), value = "VP8/OPUS")
                MetricItem(label = stringResource(Res.string.telemetry_cipher), value = "CHACHA20")
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
            color = OledTheme.colors.textMuted,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OledTheme.colors.textPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatPacketCount(count: Long): String {
    return when {
        count >= 1_000_000L -> {
            val tenths = (count / 100_000L).toFloat() / 10f
            "${tenths}M"
        }
        count >= 1_000L -> {
            val tenths = (count / 100L).toFloat() / 10f
            "${tenths}k"
        }
        else -> "$count"
    }
}
