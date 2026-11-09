package com.whitertc.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import com.whitertc.core.provider.RelayChannelConfig
import com.whitertc.core.provider.RelayProvider
import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.ProfileLinkParser
import com.whitertc.domain.model.SubscriptionResolver
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.getLocalDeviceId
import com.whitertc.domain.model.getLocalDeviceName
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

enum class ProfileSheetStep {
    LIST,
    ADD,
    EDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionProfileSheet(
    profiles: List<ConnectionProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveProfile: (ConnectionProfile) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var currentStep by remember { mutableStateOf(ProfileSheetStep.LIST) }
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF000000),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OledTheme.colors.borderHairline)
            )
        },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ProfileSheetTransition"
            ) { step ->
                when (step) {
                    ProfileSheetStep.LIST -> {
                        ProfileListView(
                            profiles = profiles,
                            activeProfileId = activeProfileId,
                            onSelectProfile = onSelectProfile,
                            onDeleteProfile = onDeleteProfile,
                            onEditProfile = { profile ->
                                editingProfile = profile
                                currentStep = ProfileSheetStep.EDIT
                            },
                            onGoToAddStep = { currentStep = ProfileSheetStep.ADD }
                        )
                    }
                    ProfileSheetStep.ADD -> {
                        ProfileAddView(
                            onBack = { currentStep = ProfileSheetStep.LIST },
                            onSave = { newProfile ->
                                onSaveProfile(newProfile)
                                currentStep = ProfileSheetStep.LIST
                            }
                        )
                    }
                    ProfileSheetStep.EDIT -> {
                        val profileToEdit = editingProfile
                        if (profileToEdit != null) {
                            ProfileEditView(
                                profile = profileToEdit,
                                onBack = { currentStep = ProfileSheetStep.LIST },
                                onSave = { updatedProfile ->
                                    onSaveProfile(updatedProfile)
                                    currentStep = ProfileSheetStep.LIST
                                }
                            )
                        } else {
                            currentStep = ProfileSheetStep.LIST
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileListView(
    profiles: List<ConnectionProfile>,
    activeProfileId: String?,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onEditProfile: (ConnectionProfile) -> Unit,
    onGoToAddStep: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.profile_sheet_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.sans,
                letterSpacing = 1.sp,
                color = OledTheme.colors.textPrimary
            )

            Text(
                text = "${profiles.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OledTheme.typography.mono,
                color = OledTheme.colors.textMuted
            )
        }

        Text(
            text = stringResource(Res.string.profile_sheet_subtitle),
            fontSize = 8.5.sp,
            fontFamily = OledTheme.typography.sans,
            color = OledTheme.colors.textMuted,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Profiles List
        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.profile_sheet_empty),
                    fontSize = 10.sp,
                    fontFamily = OledTheme.typography.mono,
                    letterSpacing = 0.5.sp,
                    color = OledTheme.colors.textMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == activeProfileId || profile.isActive
                    ProfileCardItem(
                        profile = profile,
                        isSelected = isSelected,
                        onSelect = { onSelectProfile(profile.id) },
                        onDelete = { onDeleteProfile(profile.id) },
                        onEdit = { onEditProfile(profile) },
                        canDelete = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Primary Action Button (Unified Design Token: Solid Dusty Rose, bold black text)
        OledPrimaryButton(
            text = stringResource(Res.string.profile_sheet_btn_add),
            onClick = onGoToAddStep,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileAddView(
    onBack: () -> Unit,
    onSave: (ConnectionProfile) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var serverInput by remember { mutableStateOf("") }
    var isBackupRouteEnabled by remember { mutableStateOf(false) }
    var backupRouteInput by remember { mutableStateOf("") }
    var selectedChannels by remember { mutableStateOf(8) }
    var isResolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }

    val isSubLink = remember(serverInput) {
        SubscriptionResolver.isSubscriptionUrl(serverInput)
    }

    val parsedProfile = remember(serverInput, isSubLink) {
        if (isSubLink) null else ProfileLinkParser.parseProfileFromLink(serverInput)
    }

    LaunchedEffect(serverInput) {
        val trimmed = serverInput.trim()
        val match = RelayProviderRegistry.matchAndValidate(trimmed)
        if (match != null) {
            isBackupRouteEnabled = true
            backupRouteInput = trimmed
        } else if (parsedProfile != null) {
            if (parsedProfile.providerType != TurnProviderType.DIRECT_WEBRTC || parsedProfile.signalingSource.isNotBlank()) {
                isBackupRouteEnabled = true
                if (parsedProfile.signalingSource.isNotBlank()) {
                    backupRouteInput = parsedProfile.signalingSource
                }
            }
            if (parsedProfile.parallelChannels in 8..32) {
                selectedChannels = parsedProfile.parallelChannels
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.profile_sheet_new_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.sans,
                letterSpacing = 1.sp,
                color = OledTheme.colors.textPrimary
            )
        }

        Text(
            text = stringResource(Res.string.profile_sheet_new_subtitle),
            fontSize = 8.5.sp,
            fontFamily = OledTheme.typography.sans,
            color = OledTheme.colors.textMuted,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Single Link Input
        OledTextField(
            value = serverInput,
            onValueChange = {
                serverInput = it
                resolveError = null
            },
            label = stringResource(Res.string.profile_sheet_input_label),
            placeholder = "white://... / http://host:8085/sub/...",
            headerRightContent = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(4.dp))
                        .clickable {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                serverInput = clip.trim()
                                resolveError = null
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.profile_sheet_btn_paste),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OledTheme.typography.mono,
                        color = OledTheme.colors.rose
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        val hasRelayProviders = remember { RelayProviderRegistry.hasRelayProviders() }
        val trimmedBackup = backupRouteInput.trim()
        val backupValidation = remember(trimmedBackup) {
            if (trimmedBackup.isNotBlank()) RelayProviderRegistry.matchAndValidate(trimmedBackup) else null
        }
        val isAddLinkError = hasRelayProviders && isBackupRouteEnabled && trimmedBackup.isNotBlank() && (
            backupValidation == null || backupValidation.errorMessage != null
        )
        val backupErrorMessage = if (hasRelayProviders && isBackupRouteEnabled && trimmedBackup.isNotBlank()) {
            if (backupValidation == null) stringResource(Res.string.profile_sheet_err_relay_format) else backupValidation.errorMessage
        } else null

        if (hasRelayProviders) {
            // Backup Route Toggle Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(8.dp))
                    .background(Color(0xFF080808))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_sheet_relay_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 0.5.sp,
                            color = OledTheme.colors.textPrimary
                        )
                        Text(
                            text = stringResource(Res.string.profile_sheet_relay_desc),
                            fontSize = 9.5.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textMuted,
                            lineHeight = 13.sp
                        )
                    }

                    Switch(
                        checked = isBackupRouteEnabled,
                        onCheckedChange = { isBackupRouteEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF000000),
                            checkedTrackColor = OledTheme.colors.rose,
                            uncheckedThumbColor = OledTheme.colors.textMuted,
                            uncheckedTrackColor = Color(0xFF141416),
                            uncheckedBorderColor = OledTheme.colors.borderHairline
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = isBackupRouteEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val badgeText = backupValidation?.badgeLabel
                    val fieldLabel = if (!badgeText.isNullOrBlank()) {
                        stringResource(Res.string.profile_sheet_relay_server_fmt, badgeText)
                    } else {
                        stringResource(Res.string.profile_sheet_relay_server)
                    }
                    OledTextField(
                        value = backupRouteInput,
                        onValueChange = { backupRouteInput = it },
                        label = fieldLabel,
                        placeholder = RelayProviderRegistry.getPlaceholderHint(),
                        isError = isAddLinkError,
                        supportingText = if (backupErrorMessage != null) {
                            {
                                Text(
                                    text = backupErrorMessage,
                                    fontSize = 9.5.sp,
                                    fontFamily = OledTheme.typography.sans,
                                    color = OledTheme.colors.crimson
                                )
                            }
                        } else null,
                        headerRightContent = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            backupRouteInput = clip.trim()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.profile_sheet_btn_paste),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = OledTheme.typography.mono,
                                    color = OledTheme.colors.rose
                                )
                            }
                        }
                    )

                    val matchedProvider = if (trimmedBackup.isNotBlank()) {
                        backupValidation?.provider ?: RelayProviderRegistry.findProvider(trimmedBackup)
                    } else null
                    val channelConfig = matchedProvider?.channelConfig

                    if (channelConfig != null) {
                        val channelOptions = remember(channelConfig) {
                            (channelConfig.range.first..channelConfig.range.last step channelConfig.step).toList()
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        RelayChannelsSelector(
                            selectedChannels = selectedChannels,
                            onChannelsSelected = { selectedChannels = it },
                            channelOptions = channelOptions
                        )
                    }
                }
            }
        }

        // Live Preview Card or Error Hint
        if (isSubLink) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(8.dp))
                    .background(Color(0xFF080808))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_sheet_sub_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textPrimary,
                            modifier = Modifier.alignByBaseline()
                        )

                        val activePreviewBadge = if (isBackupRouteEnabled) {
                            backupValidation?.badgeLabel ?: RelayProviderRegistry.getBadge(trimmedBackup) ?: "+RELAY"
                        } else null

                        if (activePreviewBadge != null) {
                            Text(
                                text = activePreviewBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.rose,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.profile_sheet_sub_desc),
                        fontSize = 9.5.sp,
                        fontFamily = OledTheme.typography.sans,
                        color = OledTheme.colors.textMuted
                    )
                }
            }
        } else if (parsedProfile != null) {
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(8.dp))
                    .background(Color(0xFF080808))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = parsedProfile.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textPrimary,
                            modifier = Modifier.alignByBaseline()
                        )

                        val parsedActiveBadge = if (isBackupRouteEnabled) {
                            backupValidation?.badgeLabel ?: RelayProviderRegistry.getBadge(trimmedBackup) ?: "+RELAY"
                        } else null

                        if (parsedActiveBadge != null) {
                            Text(
                                text = parsedActiveBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OledTheme.typography.sans,
                                color = OledTheme.colors.rose,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }

                    val portsSummary = if (parsedProfile.serverPorts.size > 1) {
                        stringResource(Res.string.profile_sheet_ports_fmt, parsedProfile.serverPorts.size)
                    } else ""
                    val channelsSummary = if (isBackupRouteEnabled && backupValidation?.provider?.channelConfig != null) {
                        stringResource(Res.string.profile_sheet_streams_fmt, selectedChannels)
                    } else ""

                    Text(
                        text = "${parsedProfile.serverHost}:${parsedProfile.serverPort}$portsSummary$channelsSummary",
                        fontSize = 9.5.sp,
                        fontFamily = OledTheme.typography.mono,
                        color = OledTheme.colors.textMuted
                    )
                }
            }
        } else if (serverInput.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.profile_sheet_err_format),
                fontSize = 9.sp,
                fontFamily = OledTheme.typography.mono,
                color = OledTheme.colors.crimson,
                letterSpacing = 0.5.sp
            )
        }

        if (resolveError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "// $resolveError",
                fontSize = 9.sp,
                fontFamily = OledTheme.typography.mono,
                color = OledTheme.colors.crimson,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OledSecondaryButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )

            val isBackupInputValid = !isBackupRouteEnabled || trimmedBackup.isBlank() || (backupValidation != null && backupValidation.errorMessage == null)
            val errResolveText = stringResource(Res.string.profile_sheet_err_resolve)
            OledPrimaryButton(
                text = if (isResolving) stringResource(Res.string.profile_sheet_resolving) else stringResource(Res.string.common_add),
                enabled = (parsedProfile != null || isSubLink) && !isResolving && isBackupInputValid,
                onClick = {
                    val effectiveBackupLink = if (isBackupRouteEnabled) trimmedBackup else ""

                    if (isSubLink) {
                        isResolving = true
                        resolveError = null
                        scope.launch {
                            val profile = SubscriptionResolver.resolveSubscription(
                                rawUrl = serverInput,
                                deviceId = getLocalDeviceId(),
                                deviceName = getLocalDeviceName()
                            )
                            isResolving = false
                            if (profile != null) {
                                val linkToUse = if (isBackupRouteEnabled) {
                                    if (effectiveBackupLink.isNotBlank()) effectiveBackupLink else profile.signalingSource
                                } else ""
                                val matched = if (linkToUse.isNotBlank()) RelayProviderRegistry.matchAndValidate(linkToUse) else null
                                val effectiveType = if (linkToUse.isNotBlank()) {
                                    TurnProviderType.fromId(matched?.provider?.providerId)
                                } else TurnProviderType.DIRECT_WEBRTC

                                onSave(
                                    profile.copy(
                                        id = "profile_${Clock.System.now().toEpochMilliseconds()}",
                                        providerType = effectiveType,
                                        signalingSource = linkToUse,
                                        parallelChannels = if (isBackupRouteEnabled) selectedChannels else 8,
                                        isActive = true
                                    )
                                )
                            } else {
                                resolveError = errResolveText
                            }
                        }
                    } else {
                        val profileToSave = parsedProfile ?: return@OledPrimaryButton
                        val linkToUse = if (isBackupRouteEnabled) {
                            if (effectiveBackupLink.isNotBlank()) effectiveBackupLink else profileToSave.signalingSource
                        } else ""
                        val matched = if (linkToUse.isNotBlank()) RelayProviderRegistry.matchAndValidate(linkToUse) else null
                        val effectiveType = if (linkToUse.isNotBlank()) {
                            TurnProviderType.fromId(matched?.provider?.providerId)
                        } else TurnProviderType.DIRECT_WEBRTC

                        onSave(
                            profileToSave.copy(
                                id = "profile_${Clock.System.now().toEpochMilliseconds()}",
                                providerType = effectiveType,
                                signalingSource = linkToUse,
                                parallelChannels = if (isBackupRouteEnabled) selectedChannels else 8,
                                isActive = true
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProfileCardItem(
    profile: ConnectionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) OledTheme.colors.rose else OledTheme.colors.borderHairline
    val badge = remember(profile.signalingSource, profile.providerType) {
        RelayProviderRegistry.getBadge(profile.signalingSource, profile.providerType.id)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF080808) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = profile.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = OledTheme.typography.sans,
                        color = OledTheme.colors.textPrimary,
                        modifier = Modifier.alignByBaseline()
                    )

                    if (!badge.isNullOrBlank()) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.rose,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                val channelsSummary = if (profile.providerType != TurnProviderType.DIRECT_WEBRTC || profile.signalingSource.isNotBlank()) {
                    val provider = RelayProviderRegistry.findProvider(profile.signalingSource) ?: RelayProviderRegistry.get(profile.providerType.id)
                    if (provider?.channelConfig != null) stringResource(Res.string.profile_sheet_streams_fmt, profile.parallelChannels) else ""
                } else ""

                val hostText = if (profile.serverHost.isNotBlank()) "${profile.serverHost}:${profile.serverPort}" else stringResource(Res.string.profile_sheet_no_address)
                Text(
                    text = "$hostText$channelsSummary",
                    fontSize = 9.5.sp,
                    fontFamily = OledTheme.typography.mono,
                    color = OledTheme.colors.textMuted
                )
            }

            // Right Actions: Pencil for edit, Trash for delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Pencil,
                        contentDescription = stringResource(Res.string.profile_sheet_edit_desc),
                        tint = OledTheme.colors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                if (canDelete) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Trash2,
                            contentDescription = stringResource(Res.string.profile_sheet_delete_desc),
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditView(
    profile: ConnectionProfile,
    onBack: () -> Unit,
    onSave: (ConnectionProfile) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isBackupRouteEnabled by remember(profile.id) {
        mutableStateOf(profile.providerType != TurnProviderType.DIRECT_WEBRTC || profile.signalingSource.isNotBlank())
    }
    var backupRouteInput by remember(profile.id) { mutableStateOf(profile.signalingSource) }
    var selectedChannels by remember(profile.id) { mutableStateOf(profile.parallelChannels) }

    val hasRelayProviders = remember { RelayProviderRegistry.hasRelayProviders() }
    val trimmedLink = backupRouteInput.trim()
    val validationResult = remember(trimmedLink) {
        if (trimmedLink.isNotBlank()) RelayProviderRegistry.matchAndValidate(trimmedLink) else null
    }
    val isLinkError = hasRelayProviders && isBackupRouteEnabled && trimmedLink.isNotBlank() && (
        validationResult == null || validationResult.errorMessage != null
    )
    val errorMessage = if (hasRelayProviders && isBackupRouteEnabled && trimmedLink.isNotBlank()) {
        if (validationResult == null) stringResource(Res.string.profile_sheet_err_relay_format) else validationResult.errorMessage
    } else null

    Column(modifier = Modifier.fillMaxWidth()) {
        // Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.profile_sheet_backup_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.sans,
                letterSpacing = 1.sp,
                color = OledTheme.colors.textPrimary
            )
        }

        Text(
            text = stringResource(Res.string.profile_sheet_backup_subtitle),
            fontSize = 8.5.sp,
            fontFamily = OledTheme.typography.sans,
            color = OledTheme.colors.textMuted,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Target profile info preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(8.dp))
                .background(Color(0xFF080808))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = profile.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OledTheme.typography.sans,
                    color = OledTheme.colors.textPrimary
                )
                Text(
                    text = "${profile.serverHost}:${profile.serverPort}",
                    fontSize = 9.5.sp,
                    fontFamily = OledTheme.typography.mono,
                    color = OledTheme.colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (hasRelayProviders) {
            // Backup Route Toggle Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(8.dp))
                    .background(Color(0xFF080808))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_sheet_relay_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OledTheme.typography.sans,
                            letterSpacing = 0.5.sp,
                            color = OledTheme.colors.textPrimary
                        )
                        Text(
                            text = stringResource(Res.string.profile_sheet_relay_desc),
                            fontSize = 9.5.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textMuted,
                            lineHeight = 13.sp
                        )
                    }

                    Switch(
                        checked = isBackupRouteEnabled,
                        onCheckedChange = { isBackupRouteEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF000000),
                            checkedTrackColor = OledTheme.colors.rose,
                            uncheckedThumbColor = OledTheme.colors.textMuted,
                            uncheckedTrackColor = Color(0xFF141416),
                            uncheckedBorderColor = OledTheme.colors.borderHairline
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = isBackupRouteEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val badgeText = validationResult?.badgeLabel ?: RelayProviderRegistry.getBadge(trimmedLink, profile.providerType.id)
                    val fieldLabel = if (!badgeText.isNullOrBlank()) {
                        stringResource(Res.string.profile_sheet_relay_server_fmt, badgeText)
                    } else {
                        stringResource(Res.string.profile_sheet_relay_server)
                    }

                    OledTextField(
                        value = backupRouteInput,
                        onValueChange = { backupRouteInput = it },
                        label = fieldLabel,
                        placeholder = RelayProviderRegistry.getPlaceholderHint(),
                        isError = isLinkError,
                        supportingText = if (errorMessage != null) {
                            {
                                Text(
                                    text = errorMessage,
                                    fontSize = 9.5.sp,
                                    fontFamily = OledTheme.typography.sans,
                                    color = OledTheme.colors.crimson
                                )
                            }
                        } else null,
                        headerRightContent = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            backupRouteInput = clip.trim()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.profile_sheet_btn_paste),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = OledTheme.typography.mono,
                                    color = OledTheme.colors.rose
                                )
                            }
                        }
                    )

                    val matchedProvider = if (trimmedLink.isNotBlank()) {
                        validationResult?.provider ?: RelayProviderRegistry.findProvider(trimmedLink)
                    } else {
                        RelayProviderRegistry.get(profile.providerType.id)
                    }
                    val channelConfig = matchedProvider?.channelConfig

                    if (channelConfig != null) {
                        val channelOptions = remember(channelConfig) {
                            (channelConfig.range.first..channelConfig.range.last step channelConfig.step).toList()
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        RelayChannelsSelector(
                            selectedChannels = selectedChannels,
                            onChannelsSelected = { selectedChannels = it },
                            channelOptions = channelOptions
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OledSecondaryButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )

            val canSave = !isBackupRouteEnabled || (trimmedLink.isNotBlank() && validationResult != null && validationResult.errorMessage == null) || trimmedLink.isBlank()

            OledPrimaryButton(
                text = stringResource(Res.string.common_save),
                enabled = canSave,
                onClick = {
                    val effectiveSource = if (isBackupRouteEnabled) trimmedLink else ""
                    val matched = if (effectiveSource.isNotBlank()) RelayProviderRegistry.matchAndValidate(effectiveSource) else null
                    val effectiveType = if (isBackupRouteEnabled && effectiveSource.isNotBlank()) {
                        TurnProviderType.fromId(matched?.provider?.providerId ?: profile.providerType.id)
                    } else {
                        TurnProviderType.DIRECT_WEBRTC
                    }
                    val baseName = profile.displayName
                    val updatedName = if (effectiveType == TurnProviderType.VK_CALLS && (
                            baseName == "VK Calls" ||
                            baseName.startsWith("VK Calls (") ||
                            baseName.startsWith("VK Relay")
                        )) {
                        val joinHash = effectiveSource.substringAfterLast('/').substringBefore('?').substringBefore('#')
                        if (joinHash.isNotBlank()) "VK Calls ($joinHash)" else baseName
                    } else if (effectiveType == TurnProviderType.STANDARD_TURN && (
                            baseName == "Standard TURN" ||
                            baseName.startsWith("Standard TURN (") ||
                            baseName.startsWith("TURN Relay")
                        )) {
                        val host = effectiveSource.removePrefix("turn:").removePrefix("turns:").removePrefix("//")
                            .split("@").lastOrNull()?.substringBefore('/')?.substringBefore(':') ?: ""
                        if (host.isNotBlank()) "TURN ($host)" else baseName
                    } else {
                        baseName
                    }
                    val updatedProfile = profile.copy(
                        providerType = effectiveType,
                        signalingSource = effectiveSource,
                        parallelChannels = if (isBackupRouteEnabled) selectedChannels else 8,
                        name = updatedName
                    )
                    onSave(updatedProfile)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RelayChannelsSelector(
    selectedChannels: Int,
    onChannelsSelected: (Int) -> Unit,
    channelOptions: List<Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.profile_sheet_channels_title),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.sans,
                letterSpacing = 0.5.sp,
                color = OledTheme.colors.textSecondary
            )

            Text(
                text = stringResource(Res.string.hero_channels_fmt, selectedChannels),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OledTheme.typography.mono,
                color = OledTheme.colors.rose
            )
        }

        OledSegmentedControl(
            items = channelOptions,
            selectedItem = selectedChannels,
            onItemSelected = onChannelsSelected,
            itemLabel = { "$it" }
        )

        Text(
            text = stringResource(Res.string.profile_sheet_channels_desc),
            fontSize = 9.sp,
            fontFamily = OledTheme.typography.sans,
            color = OledTheme.colors.textMuted,
            lineHeight = 13.sp
        )
    }
}
