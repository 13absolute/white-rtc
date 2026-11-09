package com.whitertc.presentation.mainScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whitertc.presentation.components.ConnectionHero
import com.whitertc.presentation.components.ConnectionProfileSheet
import com.whitertc.presentation.components.TelemetryPagerCard
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    state: MainState,
    onEvent: (MainEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF000000),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.app_name),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = OledTheme.colors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, OledTheme.colors.borderHairline, CircleShape)
                        .background(Color.Transparent)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Settings,
                        contentDescription = stringResource(Res.string.main_settings_desc),
                        tint = OledTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val heroEstimatedHeight = 270.dp
                val idleTopPadding = maxOf(16.dp, (maxHeight - heroEstimatedHeight) / 2)
                val heroTopPadding by animateDpAsState(
                    targetValue = if (state.isTelemetryVisible) 26.dp else idleTopPadding,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    ),
                    label = "HeroTopPadding"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = heroTopPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Connection Hero Master Hub (Fixed size, smoothly floats vertically)
                    ConnectionHero(
                        phase = state.phase,
                        statusMessage = state.statusMessage,
                        sessionDurationText = state.sessionDurationText,
                        activeProfileName = state.activeProfile?.displayName ?: state.serverHost,
                        serverHost = state.serverHost,
                        serverPort = state.serverPort,
                        providerType = state.providerType,
                        pingMs = state.stats.pingMs,
                        totalChannels = state.totalChannels,
                        onConnect = { onEvent(MainEvent.ConnectClicked) },
                        onDisconnect = { onEvent(MainEvent.DisconnectClicked) },
                        onTargetClick = { onEvent(MainEvent.TargetClicked) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    AnimatedVisibility(
                        visible = state.isTelemetryVisible,
                        enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(
                            animationSpec = tween(400, delayMillis = 150),
                            initialOffsetY = { 60 }
                        ),
                        exit = fadeOut(tween(250)) + slideOutVertically(
                            animationSpec = tween(250),
                            targetOffsetY = { 60 }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (state.isTelemetryVisible) Modifier.weight(1f) else Modifier)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Spacer(modifier = Modifier.height(18.dp))

                            // Dual-Face OLED Telemetry Instrument Card
                            TelemetryPagerCard(
                                downlinkBps = state.stats.downlinkBps,
                                uplinkBps = state.stats.uplinkBps,
                                bytesIn = state.stats.bytesIn,
                                bytesOut = state.stats.bytesOut,
                                packetsIn = state.stats.packetsIn,
                                packetsOut = state.stats.packetsOut,
                                activeChannels = state.stats.activeChannels,
                                totalChannels = state.stats.totalChannels,
                                sfuLabel = state.meshLabel,
                                audioCount = state.audioChannelsCount,
                                videoCount = state.videoChannelsCount,
                                channels = state.stats.channels,
                                pingMs = state.stats.pingMs,
                                activeProfileName = state.activeProfile?.displayName ?: state.serverHost,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Connection Profile OLED Bottom Sheet (Full-height sheet with embedded Add step)
            if (state.isProfileSheetOpen) {
                ConnectionProfileSheet(
                    profiles = state.profiles,
                    activeProfileId = state.activeProfile?.id,
                    onSelectProfile = { onEvent(MainEvent.SelectProfile(it)) },
                    onDeleteProfile = { onEvent(MainEvent.DeleteProfile(it)) },
                    onSaveProfile = { onEvent(MainEvent.SaveNewProfile(it)) },
                    onDismissRequest = { onEvent(MainEvent.DismissProfileSheet) }
                )
            }
        }
    }
}
