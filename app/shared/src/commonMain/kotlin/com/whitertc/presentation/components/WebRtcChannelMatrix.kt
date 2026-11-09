package com.whitertc.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.domain.model.ChannelMediaType
import com.whitertc.domain.model.ChannelTelemetry
import com.whitertc.presentation.theme.OledTheme

@Composable
fun WebRtcChannelMatrix(
    activeChannels: Int,
    totalChannels: Int,
    sfuLabel: String,
    audioCount: Int,
    videoCount: Int,
    channels: List<ChannelTelemetry>,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    val matrixBody = @Composable {
        Column(modifier = Modifier.fillMaxSize()) {
            // System Summary Bar (Modular Rack Header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$sfuLabel: $totalChannels SUBFLOWS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = OledTheme.colors.textPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$audioCount OPUS (AUDIO) • $videoCount VP8 (VIDEO)",
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OledTheme.colors.textMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeChannels > 0) OledTheme.colors.emerald else OledTheme.colors.textMuted
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$activeChannels / $totalChannels ALIVE",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (activeChannels > 0) OledTheme.colors.emerald else OledTheme.colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Modular Rack List (Scalable from 1 to 64+ subflows with internal vertical scrolling)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(
                    items = channels,
                    key = { it.index }
                ) { ch ->
                    val isExpanded = expandedIndex == ch.index
                    val chNum = ch.index + 1
                    val chLabel = if (chNum < 10) "CH 0$chNum" else "CH $chNum"
                    val isAudio = ch.mediaType == ChannelMediaType.AUDIO
                    val totalSpeed = ch.downlinkBps + ch.uplinkBps

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                1.dp,
                                if (isExpanded) OledTheme.colors.rose else OledTheme.colors.borderHairline,
                                RoundedCornerShape(6.dp)
                            )
                            .background(
                                if (isExpanded) OledTheme.colors.roseDim else OledTheme.colors.surfaceElevated
                            )
                            .clickable {
                                expandedIndex = if (isExpanded) null else ch.index
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel ID + Codec + Status LED
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = chLabel,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ch.isActive) OledTheme.colors.textPrimary else OledTheme.colors.textMuted
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Monochrome codec badge
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
                                            text = ch.mediaType.codec,
                                            fontSize = 7.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAudio) OledTheme.colors.textSecondary else OledTheme.colors.rose
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // LED status indicator
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (ch.isActive) OledTheme.colors.emerald else Color(0xFF333339)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (ch.isActive) "ALIVE" else "IDLE",
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (ch.isActive) OledTheme.colors.emerald else OledTheme.colors.textMuted
                                    )
                                }

                                // Port + Real-time throughput + Latency
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ":${ch.port}/UDP",
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OledTheme.colors.textMuted
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = formatBps(totalSpeed),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalSpeed > 0L) OledTheme.colors.textPrimary else OledTheme.colors.textMuted
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    val rttText = if (ch.rttMs > 0L) "${ch.rttMs} ms" else "-- ms"
                                    Text(
                                        text = rttText,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (ch.rttMs > 0L) OledTheme.colors.rose else OledTheme.colors.textMuted
                                    )
                                }
                            }

                            // Interactive Micro-Inspection Panel (Expanded on tap)
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(OledTheme.colors.borderHairline)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            val ssrcHex = ((0x57485000L + ch.index) and 0xFFFFFFFFL).toString(16).uppercase()
                                            Text(
                                                text = "RTP SSRC: 0x$ssrcHex",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = OledTheme.colors.textMuted
                                            )
                                            Text(
                                                text = "MTU: ${ch.mtu} B",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = OledTheme.colors.textMuted
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "↓ ${formatBps(ch.downlinkBps)}   ↑ ${formatBps(ch.uplinkBps)}",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = OledTheme.colors.textSecondary
                                            )
                                            Text(
                                                text = if (ch.lostPackets > 0) "LOSS: ${ch.lostPackets} PKT" else "LOSS: 0 PKT",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (ch.lostPackets > 0) OledTheme.colors.amber else OledTheme.colors.textMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (embedded) {
        Box(modifier = modifier.fillMaxSize()) {
            matrixBody()
        }
    } else {
        OledOutlinedCard(
            modifier = modifier.fillMaxWidth(),
            borderColor = OledTheme.colors.borderHairline,
            contentPadding = 12.dp
        ) {
            matrixBody()
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
