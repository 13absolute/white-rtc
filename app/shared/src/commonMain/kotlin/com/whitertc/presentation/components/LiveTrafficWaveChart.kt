package com.whitertc.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

private const val MAX_HISTORY_POINTS = 30

data class TrafficSample(
    val downlinkMbps: Float,
    val uplinkMbps: Float
)

@Composable
fun LiveTrafficWaveChart(
    downlinkBps: Long,
    uplinkBps: Long,
    bytesIn: Long,
    bytesOut: Long,
    packetsIn: Long,
    packetsOut: Long,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val currentDownlinkBps by rememberUpdatedState(downlinkBps)
    val currentUplinkBps by rememberUpdatedState(uplinkBps)
    val currentBytesIn by rememberUpdatedState(bytesIn)
    val currentBytesOut by rememberUpdatedState(bytesOut)

    // Pre-populate with 30 zero points so the oscilloscope baseline is always present
    val history = remember {
        mutableStateListOf<TrafficSample>().apply {
            repeat(MAX_HISTORY_POINTS) { add(TrafficSample(0f, 0f)) }
        }
    }

    var liveDownMbps by remember { mutableStateOf(0f) }
    var liveUpMbps by remember { mutableStateOf(0f) }

    // 1-second continuous telemetry ticker that samples bitrate and updates the rolling history
    LaunchedEffect(Unit) {
        var prevBytesIn = currentBytesIn
        var prevBytesOut = currentBytesOut
        var prevMark = TimeSource.Monotonic.markNow()

        while (true) {
            delay(1000L)
            val elapsed = prevMark.elapsedNow()
            val dtSec = (elapsed.inWholeMilliseconds / 1000f).coerceAtLeast(0.001f)
            prevMark = TimeSource.Monotonic.markNow()

            val curBytesIn = currentBytesIn
            val curBytesOut = currentBytesOut
            val curDownBps = currentDownlinkBps
            val curUpBps = currentUplinkBps

            // Direct engine measurement preferred; bytes-delta fallback if daemon passes 0
            val speedDown: Float = if (curDownBps > 0L) {
                curDownBps.toFloat() / 1_000_000f
            } else if (curBytesIn > prevBytesIn) {
                ((curBytesIn - prevBytesIn) * 8f) / (dtSec * 1_000_000f)
            } else {
                0f
            }

            val speedUp: Float = if (curUpBps > 0L) {
                curUpBps.toFloat() / 1_000_000f
            } else if (curBytesOut > prevBytesOut) {
                ((curBytesOut - prevBytesOut) * 8f) / (dtSec * 1_000_000f)
            } else {
                0f
            }

            prevBytesIn = curBytesIn
            prevBytesOut = curBytesOut

            liveDownMbps = speedDown
            liveUpMbps = speedUp

            if (history.size >= MAX_HISTORY_POINTS) {
                history.removeAt(0)
            }
            history.add(TrafficSample(speedDown, speedUp))
        }
    }

    val displayDownMbps = if (downlinkBps > 0L) downlinkBps.toFloat() / 1_000_000f else liveDownMbps
    val displayUpMbps = if (uplinkBps > 0L) uplinkBps.toFloat() / 1_000_000f else liveUpMbps

    val chartBody = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(OledTheme.colors.rose)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(Res.string.telemetry_downlink),
                            fontSize = 9.sp,
                            color = OledTheme.colors.textMuted,
                            fontFamily = OledTheme.typography.sans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = formatMbps(displayDownMbps),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OledTheme.colors.textPrimary,
                            fontFamily = OledTheme.typography.mono
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(Res.string.telemetry_uplink),
                            fontSize = 9.sp,
                            color = OledTheme.colors.textMuted,
                            fontFamily = OledTheme.typography.sans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = formatMbps(displayUpMbps),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OledTheme.colors.textPrimary,
                            fontFamily = OledTheme.typography.mono
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8E8E93))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val downColor = OledTheme.colors.rose
            val upColor = Color(0xFF8E8E93)
            val gridColor = OledTheme.colors.borderSubtle

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (embedded) 80.dp else 96.dp)
            ) {
                val width = size.width
                val height = size.height

                // Draw 2 horizontal subtle guideline dashes
                for (i in 1..2) {
                    val y = height * (i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                val points = history.toList()
                if (points.size < 2) return@Canvas

                val peakSpeed = points.maxOfOrNull { max(it.downlinkMbps, it.uplinkMbps) } ?: 0f
                val maxSpeed = max(1f, peakSpeed * 1.15f)

                val stepX = width / (MAX_HISTORY_POINTS - 1).coerceAtLeast(1)
                val startIndex = (MAX_HISTORY_POINTS - points.size).coerceAtLeast(0)

                // 1. Draw Downlink Curve and Gradient (Accent Rose)
                drawTrafficSpline(
                    points = points.map { it.downlinkMbps },
                    maxVal = maxSpeed,
                    stepX = stepX,
                    startIndex = startIndex,
                    canvasWidth = width,
                    canvasHeight = height,
                    strokeColor = downColor,
                    gradientTopColor = downColor.copy(alpha = 0.15f)
                )

                // 2. Draw Uplink Curve and Gradient (Subtle Zinc/Silver)
                drawTrafficSpline(
                    points = points.map { it.uplinkMbps },
                    maxVal = maxSpeed,
                    stepX = stepX,
                    startIndex = startIndex,
                    canvasWidth = width,
                    canvasHeight = height,
                    strokeColor = upColor,
                    gradientTopColor = upColor.copy(alpha = 0.10f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hairline separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OledTheme.colors.borderHairline)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom rows: Session volume & packets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.telemetry_session_volume),
                    fontSize = 9.sp,
                    color = OledTheme.colors.textMuted,
                    fontFamily = OledTheme.typography.sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "↓ ${formatDataVolume(bytesIn)}   ↑ ${formatDataVolume(bytesOut)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTheme.colors.textSecondary,
                    fontFamily = OledTheme.typography.mono
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.telemetry_total_packets),
                    fontSize = 9.sp,
                    color = OledTheme.colors.textMuted,
                    fontFamily = OledTheme.typography.sans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "↓ ${formatPacketCount(packetsIn)}   ↑ ${formatPacketCount(packetsOut)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OledTheme.colors.textSecondary,
                    fontFamily = OledTheme.typography.mono
                )
            }
        }
    }

    if (embedded) {
        Box(modifier = modifier.fillMaxWidth()) {
            chartBody()
        }
    } else {
        OledOutlinedCard(
            modifier = modifier.fillMaxWidth(),
            borderColor = OledTheme.colors.borderHairline
        ) {
            chartBody()
        }
    }
}

private fun DrawScope.drawTrafficSpline(
    points: List<Float>,
    maxVal: Float,
    stepX: Float,
    startIndex: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    strokeColor: Color,
    gradientTopColor: Color
) {
    if (points.size < 2) return

    val strokePath = Path()
    val fillPath = Path()

    fun getY(value: Float): Float {
        val normalized = (value / maxVal).coerceIn(0f, 1f)
        return canvasHeight - (normalized * (canvasHeight - 16f)) - 8f
    }

    val firstX = startIndex * stepX
    val firstY = getY(points[0])

    strokePath.moveTo(firstX, firstY)
    fillPath.moveTo(firstX, canvasHeight)
    fillPath.lineTo(firstX, firstY)

    for (i in 0 until points.size - 1) {
        val p0X = (startIndex + i) * stepX
        val p0Y = getY(points[i])
        val p1X = (startIndex + i + 1) * stepX
        val p1Y = getY(points[i + 1])

        val controlX1 = p0X + (p1X - p0X) / 2f
        val controlY1 = p0Y
        val controlX2 = p0X + (p1X - p0X) / 2f
        val controlY2 = p1Y

        strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1X, p1Y)
        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1X, p1Y)
    }

    val lastX = (startIndex + points.size - 1) * stepX
    fillPath.lineTo(lastX, canvasHeight)
    fillPath.close()

    // Draw gradient fill
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(gradientTopColor, Color.Transparent),
            startY = 0f,
            endY = canvasHeight
        )
    )

    // Draw smooth line
    drawPath(
        path = strokePath,
        color = strokeColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun formatMbps(mbps: Float): String {
    return when {
        mbps >= 100f -> "${mbps.toInt()} Mbps"
        mbps >= 1f -> {
            val rounded = (mbps * 10).toInt() / 10f
            "$rounded Mbps"
        }
        mbps > 0.01f -> {
            val kbps = (mbps * 1000).toInt()
            "$kbps kbps"
        }
        else -> "0.0 Mbps"
    }
}

private fun formatDataVolume(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 6)
    val pre = "KMGTPE"[exp - 1]
    val value = bytes / 1024.0.pow(exp.toDouble())
    val rounded = (value * 10).toLong() / 10.0
    return "$rounded ${pre}B"
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
