package com.whitertc.presentation.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.domain.speedtest.SpeedTestState
import com.whitertc.domain.speedtest.SpeedTestStep
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@Composable
fun SpeedTestBenchmarkCard(
    speedTestState: SpeedTestState,
    onRunBenchmark: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false
) {
    val cardBody = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (speedTestState) {
                is SpeedTestState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(Res.string.speedtest_title),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = OledTheme.colors.textMuted,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(4.dp))
                                    .background(OledTheme.colors.surfaceElevated)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "20MB",
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OledTheme.colors.textSecondary
                                )
                            }
                        }

                        // Inverse Hardware Button (White or Rose fill, Black text)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (embedded) OledTheme.colors.rose else OledTheme.colors.textPrimary)
                                .clickable(onClick = onRunBenchmark)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.speedtest_btn_start),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OledTheme.typography.sans,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF000000)
                            )
                        }
                    }
                }

                is SpeedTestState.Running -> {
                    val stepTitle = when (speedTestState.step) {
                        SpeedTestStep.PREPARING_DIRECT -> stringResource(Res.string.speedtest_step_prep_direct)
                        SpeedTestStep.DIRECT_PROBE -> stringResource(Res.string.speedtest_step_direct_probe, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.DIRECT_DOWNLOAD -> stringResource(Res.string.speedtest_step_direct_down, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.DIRECT_UPLOAD -> stringResource(Res.string.speedtest_step_direct_up, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.DIRECT_DONE -> stringResource(Res.string.speedtest_step_direct_done, speedTestState.currentRun)
                        SpeedTestStep.PREPARING_VPN -> stringResource(Res.string.speedtest_step_prep_vpn)
                        SpeedTestStep.VPN_PROBE -> stringResource(Res.string.speedtest_step_vpn_probe, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.VPN_DOWNLOAD -> stringResource(Res.string.speedtest_step_vpn_down, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.VPN_UPLOAD -> stringResource(Res.string.speedtest_step_vpn_up, speedTestState.currentRun, speedTestState.totalRuns)
                        SpeedTestStep.VPN_DONE -> stringResource(Res.string.speedtest_step_vpn_done, speedTestState.currentRun)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stepTitle.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = OledTheme.colors.amber
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = OledTheme.colors.amber,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "${speedTestState.currentRun}/${speedTestState.totalRuns}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OledTheme.colors.amber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = {
                            val phaseBase = if (speedTestState.isVpnPhase) 0.5f else 0.0f
                            val runFraction = (speedTestState.currentRun.toFloat() / maxOf(1, speedTestState.totalRuns)) * 0.5f
                            (phaseBase + runFraction).coerceIn(0.05f, 0.95f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = OledTheme.colors.amber,
                        trackColor = OledTheme.colors.borderSubtle
                    )

                    val downStr = speedTestState.currentDownMbps?.let { "${formatDouble(it)}M" } ?: "..."
                    val upStr = speedTestState.currentUpMbps?.let { "${formatDouble(it)}M" } ?: "..."
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.speedtest_live_fmt, downStr, upStr),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OledTheme.colors.textMuted
                    )
                }

                is SpeedTestState.Completed -> {
                    val s = speedTestState.summary
                    val ratioColor = when {
                        s.downRatioPercent >= 80.0 -> OledTheme.colors.emerald
                        s.downRatioPercent >= 50.0 -> OledTheme.colors.amber
                        else -> OledTheme.colors.crimson
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(Res.string.speedtest_efficiency_fmt, formatDouble(s.medianDownRatioPercent)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = ratioColor
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(ratioColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.speedtest_status_pass),
                                        fontSize = 7.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = ratioColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(Res.string.speedtest_raw_vpn_fmt, formatDouble(s.directDownMedian), formatDouble(s.vpnDownMedian)),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OledTheme.colors.textSecondary
                            )
                        }

                        // Compact Retest Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(6.dp))
                                .clickable(onClick = onRunBenchmark)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.speedtest_btn_retest),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = OledTheme.colors.textPrimary
                            )
                        }
                    }
                }

                is SpeedTestState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.speedtest_error_fmt, speedTestState.message.take(24).uppercase()),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = OledTheme.colors.crimson
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, OledTheme.colors.crimson.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable(onClick = onRunBenchmark)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.speedtest_btn_retry),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OledTheme.colors.crimson
                            )
                        }
                    }
                }
            }
        }
    }

    if (embedded) {
        Box(modifier = modifier.fillMaxWidth()) {
            cardBody()
        }
    } else {
        OledOutlinedCard(
            modifier = modifier.fillMaxWidth(),
            borderColor = OledTheme.colors.borderHairline,
            contentPadding = 12.dp
        ) {
            cardBody()
        }
    }
}

private fun formatDouble(value: Double): String {
    val rounded = (value * 10.0).toLong() / 10.0
    return "$rounded"
}
