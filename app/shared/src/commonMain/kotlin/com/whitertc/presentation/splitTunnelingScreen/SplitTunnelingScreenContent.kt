package com.whitertc.presentation.splitTunnelingScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Shuffle
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.X
import com.whitertc.domain.model.InstalledApp
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.presentation.components.OledOutlinedCard
import com.whitertc.presentation.components.OledSegmentedControl
import com.whitertc.presentation.theme.OledTheme
import org.jetbrains.compose.resources.stringResource
import com.whitertc.resources.Res
import com.whitertc.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingScreenContent(
    state: SplitTunnelingState,
    onEvent: (SplitTunnelingEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF000000),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.split_title),
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            letterSpacing = 1.sp,
                            color = OledTheme.colors.textPrimary
                        )
                        if (state.mode != SplitTunnelingMode.ALL && state.selectedCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(OledTheme.colors.rose.copy(alpha = 0.2f))
                                    .border(1.dp, OledTheme.colors.rose.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${state.selectedCount}",
                                    fontSize = 10.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    fontWeight = FontWeight.Bold,
                                    color = OledTheme.colors.rose
                                )
                            }
                        }
                    }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // СЕЛЕКТОР РЕЖИМА
            val modeAllLabel = stringResource(Res.string.split_mode_all)
            val modeExcludeLabel = stringResource(Res.string.split_mode_exclude)
            val modeIncludeLabel = stringResource(Res.string.split_mode_include)

            OledSegmentedControl(
                items = listOf(
                    SplitTunnelingMode.ALL,
                    SplitTunnelingMode.EXCLUDE,
                    SplitTunnelingMode.INCLUDE
                ),
                selectedItem = state.mode,
                onItemSelected = { onEvent(SplitTunnelingEvent.ModeChanged(it)) },
                itemLabel = { mode ->
                    when (mode) {
                        SplitTunnelingMode.ALL -> modeAllLabel
                        SplitTunnelingMode.EXCLUDE -> modeExcludeLabel
                        SplitTunnelingMode.INCLUDE -> modeIncludeLabel
                    }
                },
                height = 42.dp
            )

            // ИНФОРМАЦИОННАЯ ПАНЕЛЬ РЕЖИМА
            OledOutlinedCard(
                borderColor = if (state.mode != SplitTunnelingMode.ALL) OledTheme.colors.borderActive else OledTheme.colors.borderHairline,
                contentPadding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = if (state.mode != SplitTunnelingMode.ALL) OledTheme.colors.rose else OledTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (state.mode) {
                            SplitTunnelingMode.ALL -> stringResource(Res.string.split_desc_all)
                            SplitTunnelingMode.EXCLUDE -> stringResource(Res.string.split_desc_exclude)
                            SplitTunnelingMode.INCLUDE -> stringResource(Res.string.split_desc_include)
                        },
                        fontSize = 10.5.sp,
                        fontFamily = OledTheme.typography.sans,
                        color = OledTheme.colors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            if (state.mode != SplitTunnelingMode.ALL) {
                // ПОИСК
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onEvent(SplitTunnelingEvent.SearchQueryChanged(it)) },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.split_search_hint),
                            fontSize = 12.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Lucide.Search,
                            contentDescription = null,
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onEvent(SplitTunnelingEvent.SearchQueryChanged("")) }) {
                                Icon(
                                    imageVector = Lucide.X,
                                    contentDescription = stringResource(Res.string.common_clear),
                                    tint = OledTheme.colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF080808),
                        unfocusedContainerColor = Color(0xFF080808),
                        focusedBorderColor = OledTheme.colors.rose,
                        unfocusedBorderColor = OledTheme.colors.borderHairline,
                        focusedTextColor = OledTheme.colors.textPrimary,
                        unfocusedTextColor = OledTheme.colors.textPrimary,
                        cursorColor = OledTheme.colors.rose
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // ПАНЕЛЬ БЫСТРЫХ ДЕЙСТВИЙ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Переключатель системных приложений
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.showSystemApps) Color(0xFF141416) else Color.Transparent)
                            .border(
                                1.dp,
                                if (state.showSystemApps) OledTheme.colors.rose else OledTheme.colors.borderHairline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onEvent(SplitTunnelingEvent.ShowSystemAppsToggled(!state.showSystemApps)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.Cpu,
                                contentDescription = null,
                                tint = if (state.showSystemApps) OledTheme.colors.rose else OledTheme.colors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(Res.string.split_filter_system),
                                fontSize = 10.5.sp,
                                fontFamily = OledTheme.typography.mono,
                                fontWeight = if (state.showSystemApps) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.showSystemApps) OledTheme.colors.rose else OledTheme.colors.textSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterActionButton(
                            text = stringResource(Res.string.split_filter_all),
                            onClick = { onEvent(SplitTunnelingEvent.SelectAllVisibleClicked) }
                        )
                        FilterActionButton(
                            text = stringResource(Res.string.split_filter_clear),
                            onClick = { onEvent(SplitTunnelingEvent.DeselectAllClicked) }
                        )
                        FilterActionButton(
                            text = stringResource(Res.string.split_filter_invert),
                            onClick = { onEvent(SplitTunnelingEvent.InvertSelectionClicked) }
                        )
                    }
                }
            }

            // СПИСОК ПРИЛОЖЕНИЙ
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = OledTheme.colors.rose,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = stringResource(Res.string.split_loading),
                            fontSize = 11.sp,
                            fontFamily = OledTheme.typography.mono,
                            color = OledTheme.colors.textMuted
                        )
                    }
                }
            } else if (state.mode == SplitTunnelingMode.ALL) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.split_disabled_title),
                            fontFamily = OledTheme.typography.mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = OledTheme.colors.textMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = stringResource(Res.string.split_disabled_desc),
                            fontSize = 11.sp,
                            fontFamily = OledTheme.typography.sans,
                            color = OledTheme.colors.textSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                val apps = state.filteredApps
                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.split_empty),
                            fontSize = 12.sp,
                            fontFamily = OledTheme.typography.mono,
                            color = OledTheme.colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = apps,
                            key = { it.packageName }
                        ) { app ->
                            val isSelected = app.packageName in state.selectedPackages
                            AppRowItem(
                                app = app,
                                isSelected = isSelected,
                                onToggle = { onEvent(SplitTunnelingEvent.AppToggled(app.packageName)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(
    app: InstalledApp,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF0C0A0B) else Color(0xFF080808))
            .border(
                1.dp,
                if (isSelected) OledTheme.colors.rose.copy(alpha = 0.5f) else OledTheme.colors.borderHairline,
                RoundedCornerShape(10.dp)
            )
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Иконка приложения
                val icon = app.icon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141416)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Layers,
                            contentDescription = null,
                            tint = OledTheme.colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Название и package name
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) OledTheme.colors.textPrimary else OledTheme.colors.textPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isSystem) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF18181B))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = stringResource(Res.string.split_system_badge),
                                    fontSize = 8.sp,
                                    fontFamily = OledTheme.typography.mono,
                                    color = OledTheme.colors.textMuted
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.packageName,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = OledTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // OLED Чекбокс
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) OledTheme.colors.rose else Color(0xFF141416))
                    .border(
                        1.dp,
                        if (isSelected) OledTheme.colors.rose else OledTheme.colors.borderDefault,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Lucide.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0C0C0E))
            .border(1.dp, OledTheme.colors.borderHairline, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = OledTheme.typography.mono,
            fontWeight = FontWeight.SemiBold,
            color = OledTheme.colors.textSecondary
        )
    }
}
