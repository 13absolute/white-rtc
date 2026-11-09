package com.whitertc.presentation.settingScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bitcoin
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Github
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Terminal
import com.composables.icons.lucide.Trash2
import com.whitertc.plugin.rememberPluginInstaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.presentation.components.OledOutlinedButton
import com.whitertc.presentation.components.OledOutlinedCard
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenContent(
    state: SettingState,
    onEvent: (SettingEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSplitTunneling: () -> Unit,
    onNavigateToRoutingRules: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedRecently by remember { mutableStateOf(false) }
    var btcCopiedRecently by remember { mutableStateOf(false) }
    val btcAddress = "bc1qyeykqujvtltmmjq6ea65tfrj98xhrmzv83hdt4"
    val btcAddressShort = "bc1qyeykqujv...rmzv83hdt4"
    val coroutineScope = rememberCoroutineScope()
    val btcCopiedMessage = stringResource(Res.string.settings_donate_btc_copied)
    var isPluginsExpanded by rememberSaveable { mutableStateOf(false) }

    val defaultInstallErrorMessage = stringResource(Res.string.settings_plugins_install_error)
    val installPlugin = rememberPluginInstaller { result ->
        result.onSuccess { name ->
            onEvent(SettingEvent.PluginInstalled(name))
        }.onFailure { err ->
            onEvent(SettingEvent.PluginInstallFailed(err.message ?: defaultInstallErrorMessage))
        }
    }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1500)
            copiedRecently = false
        }
    }

    LaunchedEffect(btcCopiedRecently) {
        if (btcCopiedRecently) {
            delay(2000)
            btcCopiedRecently = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF000000),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp,
                        color = OledTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(Res.string.common_back),
                            tint = OledTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp,)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            OledOutlinedCard(
                borderColor = if (btcCopiedRecently) OledTheme.colors.emerald.copy(alpha = 0.5f) else OledTheme.colors.borderActive,
                backgroundColor = Color(0xFF050505),
                contentPadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OledTheme.colors.amberDim)
                                .border(BorderStroke(1.dp, OledTheme.colors.amber.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Bitcoin,
                                contentDescription = null,
                                tint = OledTheme.colors.amber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(Res.string.settings_donate_btc_title),
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.5.sp,
                                color = OledTheme.colors.amber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(Res.string.settings_donate_btc_desc),
                                fontSize = 10.sp,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.textSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF000000))
                            .border(
                                BorderStroke(
                                    width = 1.dp,
                                    color = if (btcCopiedRecently) OledTheme.colors.emerald.copy(alpha = 0.7f) else OledTheme.colors.borderSubtle
                                ),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                clipboardManager.setText(AnnotatedString(btcAddress))
                                btcCopiedRecently = true
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(btcCopiedMessage)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "BITCOIN (BECH32)",
                                    fontSize = 8.5.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                    color = OledTheme.colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = btcAddressShort,
                                    fontSize = 11.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Medium,
                                    color = if (btcCopiedRecently) OledTheme.colors.emerald else OledTheme.colors.textPrimary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (btcCopiedRecently) OledTheme.colors.emeraldDim else OledTheme.colors.roseDim
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (btcCopiedRecently) OledTheme.colors.emerald.copy(alpha = 0.4f) else OledTheme.colors.rose.copy(alpha = 0.35f)
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (btcCopiedRecently) Lucide.Check else Lucide.Copy,
                                    contentDescription = null,
                                    tint = if (btcCopiedRecently) OledTheme.colors.emerald else OledTheme.colors.rose,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (btcCopiedRecently) "COPIED" else "COPY",
                                    fontSize = 9.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = if (btcCopiedRecently) OledTheme.colors.emerald else OledTheme.colors.rose
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isDesktop) {
                // СЕКЦИЯ 1: РАЗДАЧА ПРОКСИ (SOCKS5 / HTTP)
                SettingToggleCard(
                title = stringResource(Res.string.settings_hotspot_title),
                description = stringResource(Res.string.settings_hotspot_desc),
                icon = Lucide.Radio,
                checked = state.hotspotEnabled,
                onCheckedChange = { onEvent(SettingEvent.HotspotEnabledChanged(it)) }
            ) {
                AnimatedVisibility(
                    visible = state.hotspotEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            color = OledTheme.colors.borderSubtle,
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val currentIp = state.hotspotIp
                        if (currentIp != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(OledTheme.colors.emerald)
                                )
                                Text(
                                    text = stringResource(Res.string.settings_hotspot_active),
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = OledTheme.colors.emerald
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val connectionString = "socks5://$currentIp:${state.hotspotPort}"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF080808))
                                    .border(BorderStroke(1.dp, OledTheme.colors.borderSubtle), RoundedCornerShape(8.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(connectionString))
                                        copiedRecently = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.settings_hotspot_client_url),
                                            fontSize = 8.5.sp,
                                            fontFamily = OledTheme.typography.sans,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp,
                                            color = OledTheme.colors.textMuted
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (copiedRecently) stringResource(Res.string.common_copied) else stringResource(Res.string.common_copy),
                                                fontSize = 8.5.sp,
                                                fontFamily = OledTheme.typography.mono,
                                                fontWeight = FontWeight.Bold,
                                                color = if (copiedRecently) OledTheme.colors.emerald else OledTheme.colors.textSecondary
                                            )
                                            Icon(
                                                imageVector = if (copiedRecently) Lucide.Check else Lucide.Copy,
                                                contentDescription = stringResource(Res.string.common_copy),
                                                tint = if (copiedRecently) OledTheme.colors.emerald else OledTheme.colors.textSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = connectionString,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OledTheme.colors.rose,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            SettingSpecRow(label = stringResource(Res.string.settings_hotspot_ip_label), value = currentIp)
                            SettingSpecRow(label = stringResource(Res.string.settings_hotspot_port_label), value = state.hotspotPort.toString())
                            SettingSpecRow(label = stringResource(Res.string.settings_hotspot_mode_label), value = stringResource(Res.string.settings_hotspot_mode_val))

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(Res.string.settings_hotspot_hint),
                                fontSize = 10.5.sp,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.textMuted,
                                lineHeight = 15.sp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.Info,
                                    contentDescription = null,
                                    tint = OledTheme.colors.amber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.settings_hotspot_inactive),
                                        fontFamily = OledTheme.typography.mono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        color = OledTheme.colors.amber
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(Res.string.settings_hotspot_inactive_desc, state.hotspotPort),
                                        fontSize = 11.sp,
                                        fontFamily = OledTheme.typography.sans,
                                        color = OledTheme.colors.textSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OledOutlinedButton(
                                text = stringResource(Res.string.settings_hotspot_btn),
                                height = 42.dp,
                                onClick = { onEvent(SettingEvent.OpenHotspotSettingsClicked) }
                            )
                        }
                    }
                }
            }
            }

            OledOutlinedCard(
                borderColor = OledTheme.colors.borderHairline,
                contentPadding = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToRoutingRules() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.Route,
                                contentDescription = null,
                                tint = OledTheme.colors.rose,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(Res.string.settings_rules_title),
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.5.sp,
                                color = OledTheme.colors.textPrimary
                            )
                            val badgeText = when (state.routingMode) {
                                com.whitertc.domain.model.RoutingMode.BY_TEMPLATE -> state.activeTemplateName
                                com.whitertc.domain.model.RoutingMode.DIRECT_ONLY -> stringResource(Res.string.settings_mode_direct)
                                com.whitertc.domain.model.RoutingMode.RELAY_ONLY -> stringResource(Res.string.settings_mode_relay)
                            }
                            Text(
                                text = "·  $badgeText",
                                fontFamily = OledTheme.typography.mono,
                                fontSize = 11.sp,
                                color = OledTheme.colors.rose
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.settings_rules_desc),
                            fontSize = 10.5.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 26.dp)
                        )
                    }

                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        tint = OledTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!state.isDesktop) {
                SettingToggleCard(
                    title = stringResource(Res.string.settings_bypass_lan_title),
                    description = stringResource(Res.string.settings_bypass_lan_desc),
                    icon = Lucide.Shield,
                    checked = state.bypassLan,
                    onCheckedChange = { onEvent(SettingEvent.BypassLanChanged(it)) }
                )

                // СЕКЦИЯ 3: РАЗДЕЛЬНОЕ ТУННЕЛИРОВАНИЕ (ПРИЛОЖЕНИЯ)
                OledOutlinedCard(
                    borderColor = if (state.splitTunnelingMode != SplitTunnelingMode.ALL) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
                    contentPadding = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSplitTunneling() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.Layers,
                                    contentDescription = null,
                                    tint = if (state.splitTunnelingMode != SplitTunnelingMode.ALL) OledTheme.colors.rose else OledTheme.colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.settings_split_tunneling_title),
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.5.sp,
                                    color = if (state.splitTunnelingMode != SplitTunnelingMode.ALL) OledTheme.colors.rose else OledTheme.colors.textPrimary
                                )
                                if (state.splitTunnelingMode != SplitTunnelingMode.ALL) {
                                    Text(
                                        text = "·  ${state.splitTunnelingPackagesCount}",
                                        fontSize = 11.sp,
                                        fontFamily = OledTheme.typography.mono,
                                        fontWeight = FontWeight.Bold,
                                        color = OledTheme.colors.rose
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (state.splitTunnelingMode) {
                                    SplitTunnelingMode.ALL ->
                                        stringResource(Res.string.settings_split_all)
                                    SplitTunnelingMode.EXCLUDE ->
                                        stringResource(Res.string.settings_split_exclude_fmt, state.splitTunnelingPackagesCount)
                                    SplitTunnelingMode.INCLUDE ->
                                        stringResource(Res.string.settings_split_include_fmt, state.splitTunnelingPackagesCount)
                                },
                                fontSize = 10.5.sp,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.textSecondary,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }

                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = stringResource(Res.string.common_details),
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // СЕКЦИЯ: ПЛАГИНЫ РЕЛЕЕВ
            val pluginsInteractionSource = remember { MutableInteractionSource() }
            val pluginsCardModifier = if (!isPluginsExpanded) {
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = pluginsInteractionSource,
                        indication = null
                    ) { isPluginsExpanded = true }
            } else {
                Modifier.fillMaxWidth()
            }

            OledOutlinedCard(
                borderColor = if (isPluginsExpanded) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
                contentPadding = 16.dp,
                modifier = pluginsCardModifier
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = pluginsInteractionSource,
                                indication = null
                            ) { isPluginsExpanded = !isPluginsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Lucide.Boxes,
                                    contentDescription = null,
                                    tint = if (isPluginsExpanded) OledTheme.colors.rose else OledTheme.colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.settings_plugins_title),
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 0.5.sp,
                                    color = if (isPluginsExpanded) OledTheme.colors.rose else OledTheme.colors.textPrimary
                                )
                                if (state.installedPlugins.isNotEmpty()) {
                                    Text(
                                        text = "·  ${state.installedPlugins.size}",
                                        fontSize = 11.sp,
                                        fontFamily = OledTheme.typography.mono,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPluginsExpanded) OledTheme.colors.rose else OledTheme.colors.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.settings_plugins_desc),
                                fontSize = 10.5.sp,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.textSecondary,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 26.dp)
                            )
                        }

                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isPluginsExpanded) 180f else 0f,
                            animationSpec = tween(durationMillis = 250),
                            label = "plugins_arrow_rotation"
                        )
                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = stringResource(if (isPluginsExpanded) Res.string.common_collapse else Res.string.common_expand),
                            tint = if (isPluginsExpanded) OledTheme.colors.rose else OledTheme.colors.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(arrowRotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = isPluginsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(
                                color = OledTheme.colors.borderHairline,
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (state.installedPlugins.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.settings_plugins_empty),
                                    fontSize = 11.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    color = OledTheme.colors.textMuted,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                state.installedPlugins.forEachIndexed { index, plugin ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = OledTheme.colors.borderHairline
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = plugin.displayName,
                                                fontFamily = OledTheme.typography.mono,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = OledTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = if (plugin.isBuiltIn) stringResource(Res.string.settings_plugins_builtin) else stringResource(Res.string.settings_plugins_external),
                                                fontFamily = OledTheme.typography.sans,
                                                fontSize = 9.5.sp,
                                                color = OledTheme.colors.textMuted
                                            )
                                        }

                                        if (!plugin.isBuiltIn) {
                                            IconButton(
                                                onClick = { onEvent(SettingEvent.DeletePlugin(plugin.id)) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Lucide.Trash2,
                                                    contentDescription = stringResource(Res.string.settings_plugins_delete_desc),
                                                    tint = OledTheme.colors.crimson,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OledOutlinedButton(
                                text = stringResource(Res.string.settings_plugins_install_btn),
                                height = 42.dp,
                                onClick = installPlugin
                            )
                        }
                    }
                }
            }

            // СЕКЦИЯ: ОТЛАДКА И ДИАГНОСТИКА
            SettingToggleCard(
                title = stringResource(Res.string.settings_debug_title),
                description = stringResource(Res.string.settings_debug_desc),
                icon = Lucide.Terminal,
                checked = state.debugLoggingEnabled,
                onCheckedChange = { onEvent(SettingEvent.DebugLoggingChanged(it)) }
            ) {
                AnimatedVisibility(
                    visible = state.debugLoggingEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            color = OledTheme.colors.borderSubtle,
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        SettingSpecRow(
                            label = stringResource(Res.string.settings_debug_log_size),
                            value = state.logFileSize
                        )
                        SettingSpecRow(
                            label = stringResource(Res.string.settings_debug_mode),
                            value = stringResource(Res.string.settings_debug_mode_desc)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1.12f)) {
                                OledOutlinedButton(
                                    text = stringResource(Res.string.settings_debug_download),
                                    icon = {
                                        Icon(
                                            imageVector = Lucide.Save,
                                            contentDescription = null,
                                            tint = OledTheme.colors.textPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    fontSize = 11.sp,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    height = 38.dp,
                                    onClick = { onEvent(SettingEvent.ShareLogsClicked) }
                                )
                            }
                            Box(modifier = Modifier.weight(0.88f)) {
                                OledOutlinedButton(
                                    text = stringResource(Res.string.settings_debug_clear),
                                    icon = {
                                        Icon(
                                            imageVector = Lucide.Trash2,
                                            contentDescription = null,
                                            tint = OledTheme.colors.crimson,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    borderColor = OledTheme.colors.borderHairline,
                                    contentColor = OledTheme.colors.crimson,
                                    fontSize = 11.sp,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    height = 38.dp,
                                    onClick = { onEvent(SettingEvent.ClearLogsClicked) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(Res.string.settings_debug_hint),
                            fontSize = 10.5.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // СЕКЦИЯ 4: СВЕДЕНИЯ О ПРИЛОЖЕНИИ
            OledOutlinedCard(
                borderColor = OledTheme.colors.borderSubtle,
                contentPadding = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Info,
                            contentDescription = null,
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(Res.string.settings_about_title),
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingSpecRow(label = stringResource(Res.string.settings_about_version), value = "v${state.appVersion} (${state.buildNumber})")
                    SettingSpecRow(label = stringResource(Res.string.settings_about_protocol), value = stringResource(Res.string.protocol_name))
                    SettingSpecRow(label = stringResource(Res.string.settings_about_standards), value = stringResource(Res.string.protocol_standards))
                    SettingSpecRow(label = stringResource(Res.string.settings_about_platform), value = state.platformInfo)

                    HorizontalDivider(
                        color = OledTheme.colors.borderHairline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    val uriHandler = LocalUriHandler.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                uriHandler.openUri("https://github.com/13absolute/white-rtc")
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.Github,
                                contentDescription = null,
                                tint = OledTheme.colors.textPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "13absolute/white-rtc",
                                fontSize = 10.sp,
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = FontWeight.SemiBold,
                                color = OledTheme.colors.textPrimary
                            )
                        }
                        Icon(
                            imageVector = Lucide.ExternalLink,
                            contentDescription = null,
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = OledTheme.colors.rose,
    content: (@Composable () -> Unit)? = null
) {
    OledOutlinedCard(
        borderColor = if (checked) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
        contentPadding = 16.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (checked) activeColor else OledTheme.colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.5.sp,
                            color = if (checked) activeColor else OledTheme.colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 10.5.sp,
                        fontFamily = OledTheme.typography.sans,
                        color = OledTheme.colors.textSecondary,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 26.dp)
                    )
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF000000),
                        checkedTrackColor = activeColor,
                        uncheckedThumbColor = OledTheme.colors.textMuted,
                        uncheckedTrackColor = Color(0xFF141416),
                        uncheckedBorderColor = OledTheme.colors.borderHairline
                    )
                )
            }

            if (content != null) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = OledTheme.typography.mono,
            fontWeight = FontWeight.SemiBold,
            color = OledTheme.colors.textMuted
        )
        Text(
            text = value,
            fontSize = 9.5.sp,
            fontFamily = OledTheme.typography.mono,
            color = OledTheme.colors.textSecondary
        )
    }
}