package com.whitertc.presentation.routingRulesScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingTemplate
import com.whitertc.presentation.components.OledOutlinedButton
import com.whitertc.presentation.components.OledOutlinedCard
import com.whitertc.presentation.components.OledTextField
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingRulesScreenContent(
    state: RoutingRulesState,
    onEvent: (RoutingRulesEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF000000),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.routing_title),
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OledOutlinedCard(
                borderColor = OledTheme.colors.borderHairline,
                contentPadding = 14.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = OledTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.routing_desc),
                        fontFamily = OledTheme.typography.sans,
                        fontSize = 10.5.sp,
                        color = OledTheme.colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            // Заголовок секции режимов
            Text(
                text = stringResource(Res.string.routing_mode_title),
                fontFamily = OledTheme.typography.mono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = OledTheme.colors.textMuted,
                modifier = Modifier.padding(start = 2.dp)
            )

            // Режим 1: По шаблону (Auto)
            ModeSelectCard(
                title = stringResource(Res.string.routing_mode_template_title),
                description = stringResource(Res.string.routing_mode_template_desc),
                icon = Lucide.Zap,
                isSelected = state.routingMode == RoutingMode.BY_TEMPLATE,
                onClick = { onEvent(RoutingRulesEvent.RoutingModeSelected(RoutingMode.BY_TEMPLATE)) }
            )

            // Режим 2: Только прямой канал
            ModeSelectCard(
                title = stringResource(Res.string.routing_mode_direct_title),
                description = stringResource(Res.string.routing_mode_direct_desc),
                icon = Lucide.Radio,
                isSelected = state.routingMode == RoutingMode.DIRECT_ONLY,
                onClick = { onEvent(RoutingRulesEvent.RoutingModeSelected(RoutingMode.DIRECT_ONLY)) }
            )

            // Режим 3: Только релей (промежуточный сервер)
            ModeSelectCard(
                title = stringResource(Res.string.routing_mode_relay_title),
                description = stringResource(Res.string.routing_mode_relay_desc),
                icon = Lucide.Server,
                isSelected = state.routingMode == RoutingMode.RELAY_ONLY,
                onClick = { onEvent(RoutingRulesEvent.RoutingModeSelected(RoutingMode.RELAY_ONLY)) }
            )

            // Секция шаблонов (отображается исключительно в режиме BY_TEMPLATE)
            AnimatedVisibility(
                visible = state.routingMode == RoutingMode.BY_TEMPLATE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.routing_templates_title),
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textMuted,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        Text(
                            text = "${state.routingTemplates.size}",
                            fontFamily = OledTheme.typography.mono,
                            fontSize = 10.sp,
                            color = OledTheme.colors.textMuted
                        )
                    }

                    // Список шаблонов с наглядным условием в стиле SettingSpecRow
                    state.routingTemplates.forEach { template ->
                        RoutingRuleCard(
                            template = template,
                            isActive = template.id == state.activeTemplateId,
                            onSelect = { onEvent(RoutingRulesEvent.ActiveTemplateSelected(template.id)) },
                            onEdit = { onEvent(RoutingRulesEvent.StartEditingTemplate(template)) },
                            onDelete = { onEvent(RoutingRulesEvent.DeleteTemplate(template.id)) }
                        )
                    }

                    // Кнопка создания нового правила (стандартная кнопка Settings без кислотного свечения)
                    OledOutlinedButton(
                        onClick = { onEvent(RoutingRulesEvent.StartCreatingTemplate) },
                        text = stringResource(Res.string.routing_btn_add),
                        height = 42.dp,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // BottomSheet редактирования / создания шаблона
    if (state.editingTemplate != null) {
        TemplateEditSheet(
            template = state.editingTemplate,
            isNew = state.isCreatingTemplate,
            onSave = { onEvent(RoutingRulesEvent.SaveTemplate(it)) },
            onDismiss = { onEvent(RoutingRulesEvent.CancelEditTemplate) }
        )
    }
}

@Composable
private fun ModeSelectCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OledOutlinedCard(
        borderColor = if (isSelected) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
        contentPadding = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) OledTheme.colors.rose else OledTheme.colors.textMuted,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(17.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) OledTheme.colors.rose else OledTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = description,
                        fontSize = 10.5.sp,
                        fontFamily = OledTheme.typography.sans,
                        color = OledTheme.colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            // Минималистичный радио-индикатор режима (четкий визуальный якорь выбора)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        if (isSelected) OledTheme.colors.rose else OledTheme.colors.borderDefault,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OledTheme.colors.rose)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutingRuleCard(
    template: RoutingTemplate,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OledOutlinedCard(
        borderColor = if (isActive) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
        contentPadding = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Верхняя строка: название правила, признак по умолчанию, кнопки действий (без лишней точки слева)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = template.name,
                        fontFamily = OledTheme.typography.mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isActive) OledTheme.colors.rose else OledTheme.colors.textPrimary
                    )
                    if (template.isDefault) {
                        Text(
                            text = stringResource(Res.string.routing_default_tag),
                            fontFamily = OledTheme.typography.mono,
                            fontSize = 10.sp,
                            color = OledTheme.colors.textMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Lucide.Pencil,
                            contentDescription = stringResource(Res.string.common_edit),
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (!template.isDefault) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = stringResource(Res.string.common_delete),
                                tint = OledTheme.colors.crimson,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = OledTheme.colors.borderHairline,
                thickness = 1.dp
            )

            // Блок условий в строгом системном стиле Settings (без вложенных рамок и кислотных чипов)
            val notSetText = stringResource(Res.string.common_not_set)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RuleSpecRow(
                    label = stringResource(Res.string.routing_spec_if_unreachable),
                    value = template.unreachableHosts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: notSetText
                )
                RuleSpecRow(
                    label = stringResource(Res.string.routing_spec_if_reachable),
                    value = template.reachableHosts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: notSetText
                )
                RuleSpecRow(
                    label = stringResource(Res.string.routing_spec_action),
                    value = stringResource(Res.string.routing_spec_action_val, (template.probeIntervalMs / 1000).toInt()),
                    valueColor = if (isActive) OledTheme.colors.rose else OledTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun RuleSpecRow(
    label: String,
    value: String,
    valueColor: Color = OledTheme.colors.textSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = OledTheme.typography.mono,
            fontWeight = FontWeight.SemiBold,
            color = OledTheme.colors.textMuted,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            fontSize = 9.5.sp,
            fontFamily = OledTheme.typography.mono,
            color = valueColor,
            maxLines = 2
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditSheet(
    template: RoutingTemplate,
    isNew: Boolean,
    onSave: (RoutingTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(template.name) }
    var unreachableHosts by remember { mutableStateOf(template.unreachableHosts) }
    var reachableHosts by remember { mutableStateOf(template.reachableHosts) }
    var probeTimeoutMs by remember { mutableStateOf(template.probeTimeoutMs) }
    var probeIntervalMs by remember { mutableStateOf(template.probeIntervalMs) }

    var newUnreachableText by remember { mutableStateOf("") }
    var newReachableText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isNew) stringResource(Res.string.routing_new_rule_title) else stringResource(Res.string.routing_edit_rule_title),
                fontFamily = OledTheme.typography.mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = OledTheme.colors.textPrimary
            )

            // Название шаблона
            OledTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.routing_template_name_label),
                placeholder = stringResource(Res.string.routing_template_name_hint)
            )

            // Секция 1: Внешние ресурсы
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.routing_unreachable_title),
                    fontFamily = OledTheme.typography.mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = OledTheme.colors.textPrimary
                )
                Text(
                    text = stringResource(Res.string.routing_unreachable_desc),
                    fontFamily = OledTheme.typography.sans,
                    fontSize = 10.sp,
                    color = OledTheme.colors.textSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    unreachableHosts.forEach { host ->
                        SimpleHostChip(
                            host = host,
                            onDelete = { unreachableHosts = unreachableHosts.filterNot { it == host } }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OledTextField(
                        value = newUnreachableText,
                        onValueChange = { newUnreachableText = it },
                        placeholder = stringResource(Res.string.routing_host_hint),
                        modifier = Modifier.weight(1f)
                    )
                    OledOutlinedButton(
                        onClick = {
                            val clean = newUnreachableText.trim().lowercase()
                            if (clean.isNotBlank() && !unreachableHosts.contains(clean)) {
                                unreachableHosts = unreachableHosts + clean
                                newUnreachableText = ""
                            }
                        },
                        text = "+",
                        height = 42.dp,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }

            // Секция 2: Локальные ресурсы
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.routing_reachable_title),
                    fontFamily = OledTheme.typography.mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = OledTheme.colors.textPrimary
                )
                Text(
                    text = stringResource(Res.string.routing_reachable_desc),
                    fontFamily = OledTheme.typography.sans,
                    fontSize = 10.sp,
                    color = OledTheme.colors.textSecondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reachableHosts.forEach { host ->
                        SimpleHostChip(
                            host = host,
                            onDelete = { reachableHosts = reachableHosts.filterNot { it == host } }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OledTextField(
                        value = newReachableText,
                        onValueChange = { newReachableText = it },
                        placeholder = stringResource(Res.string.routing_host_hint),
                        modifier = Modifier.weight(1f)
                    )
                    OledOutlinedButton(
                        onClick = {
                            val clean = newReachableText.trim().lowercase()
                            if (clean.isNotBlank() && !reachableHosts.contains(clean)) {
                                reachableHosts = reachableHosts + clean
                                newReachableText = ""
                            }
                        },
                        text = "+",
                        height = 42.dp,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }

            // Секция 3: Тайминги
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.routing_probe_timeout),
                        fontFamily = OledTheme.typography.mono,
                        fontSize = 10.sp,
                        color = OledTheme.colors.textPrimary
                    )
                    Text(
                        text = "${probeTimeoutMs} ms",
                        fontFamily = OledTheme.typography.mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OledTheme.colors.rose
                    )
                }
                Slider(
                    value = probeTimeoutMs.toFloat(),
                    onValueChange = { probeTimeoutMs = it.toLong() },
                    valueRange = 500f..4000f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = OledTheme.colors.rose,
                        activeTrackColor = OledTheme.colors.rose,
                        inactiveTrackColor = Color(0xFF1E1E22)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.routing_recovery_interval),
                        fontFamily = OledTheme.typography.mono,
                        fontSize = 10.sp,
                        color = OledTheme.colors.textPrimary
                    )
                    Text(
                        text = "${probeIntervalMs / 1000} s",
                        fontFamily = OledTheme.typography.mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OledTheme.colors.rose
                    )
                }
                Slider(
                    value = (probeIntervalMs / 1000).toFloat(),
                    onValueChange = { probeIntervalMs = (it * 1000).toLong() },
                    valueRange = 5f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = OledTheme.colors.rose,
                        activeTrackColor = OledTheme.colors.rose,
                        inactiveTrackColor = Color(0xFF1E1E22)
                    )
                )
            }

            // Кнопки Сохранить / Отмена
            val defaultRuleName = stringResource(Res.string.routing_default_name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OledOutlinedButton(
                    onClick = onDismiss,
                    text = stringResource(Res.string.common_cancel),
                    modifier = Modifier.weight(1f),
                    borderColor = OledTheme.colors.borderSubtle,
                    contentColor = OledTheme.colors.textMuted,
                    height = 44.dp
                )
                OledOutlinedButton(
                    onClick = {
                        val validName = if (name.isBlank()) defaultRuleName else name.trim()
                        val updated = template.copy(
                            name = validName,
                            unreachableHosts = if (unreachableHosts.isEmpty()) listOf("google.com") else unreachableHosts,
                            reachableHosts = if (reachableHosts.isEmpty()) listOf("vk.com") else reachableHosts,
                            probeTimeoutMs = probeTimeoutMs,
                            probeIntervalMs = probeIntervalMs
                        )
                        onSave(updated)
                    },
                    text = stringResource(Res.string.common_save),
                    modifier = Modifier.weight(1f),
                    height = 44.dp
                )
            }
        }
    }
}

@Composable
private fun SimpleHostChip(
    host: String,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF121214))
            .border(BorderStroke(1.dp, OledTheme.colors.borderHairline), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = host,
                fontFamily = OledTheme.typography.mono,
                fontSize = 10.sp,
                color = OledTheme.colors.textPrimary
            )
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(Res.string.common_delete),
                tint = OledTheme.colors.textMuted,
                modifier = Modifier
                    .size(12.dp)
                    .clickable(onClick = onDelete)
            )
        }
    }
}
