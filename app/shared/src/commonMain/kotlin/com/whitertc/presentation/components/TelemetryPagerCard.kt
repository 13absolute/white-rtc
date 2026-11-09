package com.whitertc.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.domain.model.ChannelMediaType
import com.whitertc.domain.model.ChannelTelemetry
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

enum class TelemetryFace {
    TRAFFIC,
    CHANNELS
}

@Composable
fun TelemetryPagerCard(
    downlinkBps: Long,
    uplinkBps: Long,
    bytesIn: Long,
    bytesOut: Long,
    packetsIn: Long,
    packetsOut: Long,
    activeChannels: Int,
    totalChannels: Int,
    sfuLabel: String,
    audioCount: Int,
    videoCount: Int,
    channels: List<ChannelTelemetry>,
    pingMs: Long = 0L,
    activeProfileName: String = "",
    modifier: Modifier = Modifier
) {
    var currentFace by remember { mutableStateOf(TelemetryFace.TRAFFIC) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Interactive Header (Subtle, borderless, hardware telemetry aesthetics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Clean title
            Text(
                text = if (currentFace == TelemetryFace.TRAFFIC) stringResource(Res.string.telemetry_tab_traffic) else stringResource(Res.string.telemetry_tab_channels),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.mono,
                color = OledTheme.colors.textSecondary,
                letterSpacing = 1.sp
            )

            // Right: Outlined button with channel count indicating click affordance
            val channelCount = if (totalChannels > 0) totalChannels else 8
            val buttonText = if (currentFace == TelemetryFace.TRAFFIC) {
                stringResource(Res.string.telemetry_btn_streams_fmt, channelCount)
            } else {
                stringResource(Res.string.telemetry_btn_traffic)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141418))
                    .border(1.dp, OledTheme.colors.borderActive, RoundedCornerShape(6.dp))
                    .clickable {
                        currentFace = if (currentFace == TelemetryFace.TRAFFIC) TelemetryFace.CHANNELS else TelemetryFace.TRAFFIC
                    }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OledTheme.typography.mono,
                    color = OledTheme.colors.rose,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hairline separator under header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OledTheme.colors.borderHairline)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Animated Dual-Face Body (Jitter-free: both faces calibrated to ~200dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
        ) {
            AnimatedContent(
                targetState = currentFace,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "TelemetryFaceTransition"
            ) { face ->
                when (face) {
                    TelemetryFace.TRAFFIC -> {
                        LiveTrafficWaveChart(
                            downlinkBps = downlinkBps,
                            uplinkBps = uplinkBps,
                            bytesIn = bytesIn,
                            bytesOut = bytesOut,
                            packetsIn = packetsIn,
                            packetsOut = packetsOut,
                            embedded = true
                        )
                    }
                    TelemetryFace.CHANNELS -> {
                        CompactChannelsFace(
                            channels = channels,
                            activeChannels = activeChannels,
                            totalChannels = totalChannels,
                            pingMs = pingMs
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactChannelsFace(
    channels: List<ChannelTelemetry>,
    activeChannels: Int,
    totalChannels: Int,
    pingMs: Long
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val displayCount = if (totalChannels > 0) totalChannels else 8
        for (i in 0 until displayCount) {
            val ch = channels.getOrNull(i)
            val chNum = i + 1
            val chLabel = if (chNum < 10) "CH 0$chNum" else "CH $chNum"
            val isAudio = ch?.mediaType == ChannelMediaType.AUDIO || (ch == null && i < 4)
            val codec = ch?.mediaType?.codec ?: if (isAudio) "OPUS" else "VP8"
            val isActive = ch?.isActive ?: (i < activeChannels)
            val port = ch?.port ?: (44300 + chNum)
            val speed = (ch?.downlinkBps ?: 0L) + (ch?.uplinkBps ?: 0L)
            val rtt = if (ch != null && ch.rttMs > 0L) ch.rttMs else if (isActive && pingMs > 0L) pingMs else 0L

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: CH ID + Codec + Status dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chLabel,
                        fontSize = 9.sp,
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) OledTheme.colors.textPrimary else OledTheme.colors.textMuted
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isAudio) OledTheme.colors.borderHairline else OledTheme.colors.borderSubtle,
                                RoundedCornerShape(3.dp)
                            )
                            .background(Color(0xFF141416))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = codec,
                            fontSize = 7.5.sp,
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAudio) OledTheme.colors.textSecondary else OledTheme.colors.rose
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) OledTheme.colors.emerald else Color(0xFF333339)
                            )
                    )
                }

                // Right: Port + Live Throughput + RTT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ":$port/UDP",
                        fontSize = 8.5.sp,
                        fontFamily = OledTheme.typography.mono,
                        color = OledTheme.colors.textMuted
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formatBps(speed),
                        fontSize = 9.sp,
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        color = if (speed > 0L) OledTheme.colors.textPrimary else OledTheme.colors.textMuted
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    val rttText = if (rtt > 0L) "$rtt ms" else "-- ms"
                    Text(
                        text = rttText,
                        fontSize = 8.5.sp,
                        fontFamily = OledTheme.typography.mono,
                        color = if (rtt > 0L) OledTheme.colors.rose else OledTheme.colors.textMuted
                    )
                }
            }

            if (i < displayCount - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Hairline separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(OledTheme.colors.borderHairline)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom technical summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.telemetry_subflows_fmt, displayCount),
                fontSize = 8.5.sp,
                fontFamily = OledTheme.typography.mono,
                fontWeight = FontWeight.Bold,
                color = OledTheme.colors.textMuted,
                letterSpacing = 0.5.sp
            )
            val mtu = channels.firstOrNull()?.mtu ?: 1120
            Text(
                text = stringResource(Res.string.telemetry_summary_fmt, activeChannels, mtu),
                fontSize = 8.5.sp,
                fontFamily = OledTheme.typography.mono,
                fontWeight = FontWeight.SemiBold,
                color = if (activeChannels > 0) OledTheme.colors.emerald else OledTheme.colors.textMuted
            )
        }
    }
}

private fun formatBps(bps: Long): String {
    return when {
        bps >= 1_000_000L -> {
            val mbpsX10 = (bps * 10L) / 1_000_000L
            val integerPart = mbpsX10 / 10
            val fractionPart = mbpsX10 % 10
            "$integerPart.$fractionPart Mbps"
        }
        bps >= 1_000L -> "${bps / 1_000L} kbps"
        bps > 0L -> "$bps bps"
        else -> "0 kbps"
    }
}
