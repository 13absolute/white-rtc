package com.whitertc.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.presentation.mainScreen.ConnectionPhase
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@Composable
fun ConnectionHero(
    phase: ConnectionPhase,
    statusMessage: String,
    sessionDurationText: String,
    activeProfileName: String,
    serverHost: String,
    serverPort: Int,
    providerType: TurnProviderType,
    pingMs: Long,
    totalChannels: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTargetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = when (phase) {
        ConnectionPhase.CONNECTING -> OledTheme.colors.rose
        ConnectionPhase.ERROR -> OledTheme.colors.crimson
        else -> OledTheme.colors.borderHairline
    }

    val statusColor = when (phase) {
        ConnectionPhase.CONNECTED -> OledTheme.colors.emerald
        ConnectionPhase.CONNECTING -> OledTheme.colors.rose
        ConnectionPhase.ERROR -> OledTheme.colors.crimson
        ConnectionPhase.DISCONNECTED -> OledTheme.colors.textMuted
    }

    val buttonSize = 170.dp
    val roseColor = OledTheme.colors.rose
    val roseDim = OledTheme.colors.roseDim

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dedicated fixed-height status slot above button (zero vertical jump)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    fontSize = 10.5.sp,
                    fontFamily = OledTheme.typography.mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Central Unified Master Sensor Hub (170.dp fixed solid hardware size)
        val buttonBorderModifier = if (!phase.isConnecting) {
            Modifier.border(
                BorderStroke(
                    width = 1.dp,
                    color = ringColor
                ),
                CircleShape
            )
        } else {
            Modifier
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .then(buttonBorderModifier)
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        when {
                            phase.isConnected -> onDisconnect()
                            phase.isConnecting -> onDisconnect()
                            else -> onConnect()
                        }
                    }
                )
        ) {
            if (phase.isConnecting) {
                val channels = totalChannels.coerceIn(1, 32)
                Canvas(modifier = Modifier.size(buttonSize)) {
                    val strokePx = 1.5.dp.toPx()
                    val inset = strokePx / 2f + 1.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val arcOffset = Offset(inset, inset)

                    if (channels == 1) {
                        drawArc(
                            color = roseDim,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = arcOffset,
                            size = arcSize,
                            style = Stroke(width = strokePx)
                        )
                        drawArc(
                            color = roseColor,
                            startAngle = radarAngle,
                            sweepAngle = 80f,
                            useCenter = false,
                            topLeft = arcOffset,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    } else {
                        val gap = if (channels <= 8) 4f else if (channels <= 16) 3f else 2f
                        val sweepAngle = (360f - channels * gap) / channels
                        val beamAngle = (-90f + radarAngle).mod(360f)
                        val tailDeg = 180f

                        for (i in 0 until channels) {
                            val startAngle = -90f + i * (sweepAngle + gap)
                            val centerAngle = (startAngle + sweepAngle / 2f).mod(360f)
                            val diff = (beamAngle - centerAngle + 360f).mod(360f)
                            val intensity = if (diff <= tailDeg) {
                                val t = 1f - (diff / tailDeg)
                                0.2f + 0.8f * (t * t)
                            } else {
                                0.2f
                            }

                            drawArc(
                                color = roseColor.copy(alpha = intensity),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = arcOffset,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                when (phase) {
                    ConnectionPhase.CONNECTED -> {
                        // Micro Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OledTheme.colors.emerald.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(Res.string.hero_secured),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OledTheme.typography.sans,
                                letterSpacing = 1.sp,
                                color = OledTheme.colors.emerald
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Large Session Timer from ViewModel State
                        Text(
                            text = sessionDurationText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.mono,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Action hint
                        Text(
                            text = stringResource(Res.string.hero_disconnect),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textMuted
                        )
                    }
                    ConnectionPhase.CONNECTING -> {
                        StandbyIcon(
                            color = roseColor.copy(alpha = pulseAlpha),
                            modifier = Modifier.size(38.dp),
                            strokeWidthDp = 2.6f
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(Res.string.hero_syncing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 2.sp,
                            color = roseColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.hero_channels_fmt, totalChannels),
                            fontSize = 9.sp,
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color = roseColor.copy(alpha = 0.7f)
                        )
                    }
                    else -> {
                        StandbyIcon(
                            color = if (phase.isError) OledTheme.colors.crimson else OledTheme.colors.rose,
                            modifier = Modifier.size(38.dp),
                            strokeWidthDp = 2.6f
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (phase.isError) stringResource(Res.string.hero_retry) else stringResource(Res.string.hero_connect),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 2.sp,
                            color = OledTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (phase.isError) stringResource(Res.string.hero_tap_to_reconnect) else stringResource(Res.string.hero_tap_to_start),
                            fontSize = 9.sp,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metadata Row: Click-to-Select Target Node + Live Ping
        val targetHost = if (serverHost.isNotBlank()) "$serverHost:$serverPort" else "—"
        val displayName = activeProfileName.ifBlank { targetHost }
        val pingColor = when {
            pingMs <= 0 -> OledTheme.colors.textMuted
            pingMs < 60 -> OledTheme.colors.emerald
            pingMs < 120 -> OledTheme.colors.amber
            else -> OledTheme.colors.crimson
        }
        val pingText = if (pingMs <= 0) "— ms" else "$pingMs ms"
        val targetAccent = OledTheme.colors.rose

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            contentAlignment = Alignment.Center
        ) {
            if (phase.isConnecting) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(6.dp))
                        .background(Color.Transparent)
                        .clickable(onClick = onDisconnect)
                        .padding(horizontal = 20.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.common_cancel),
                        fontSize = 10.sp,
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = OledTheme.colors.textMuted
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(8.dp))
                            .background(Color.Transparent)
                            .clickable(onClick = onTargetClick)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${stringResource(Res.string.hero_target_label)} ",
                                fontSize = 10.sp,
                                fontFamily = OledTheme.typography.sans,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = OledTheme.colors.textMuted
                            )
                            Text(
                                text = displayName.uppercase(),
                                fontSize = 10.sp,
                                fontFamily = OledTheme.typography.sans,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = targetAccent
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Hairline vertical divider
                            Box(
                                modifier = Modifier
                                    .height(10.dp)
                                    .width(1.dp)
                                    .background(OledTheme.colors.borderHairline)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Micro-LED for ping
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(pingColor)
                            )

                            Spacer(modifier = Modifier.width(5.dp))

                            Text(
                                text = pingText,
                                fontSize = 10.sp,
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = FontWeight.Bold,
                                color = pingColor
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "▾",
                                fontSize = 11.sp,
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = FontWeight.Bold,
                                color = OledTheme.colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.hero_tap_to_select_server),
                        fontSize = 8.sp,
                        fontFamily = OledTheme.typography.sans,
                        letterSpacing = 0.5.sp,
                        color = OledTheme.colors.textMuted.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun StandbyIcon(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 2.5f
) {
    Canvas(modifier = modifier) {
        val strokePx = strokeWidthDp.dp.toPx()
        val w = size.width
        val h = size.height
        val radius = (minOf(w, h) - strokePx * 2f) / 2f
        val center = Offset(w / 2f, h / 2f)

        // Arc starts at -60 degrees, sweeps 300 degrees clockwise (leaving 60 deg opening at top / 270 deg)
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Vertical line passing through opening at 12 o'clock
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius * 0.15f),
            end = Offset(center.x, center.y - radius * 1.05f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}
