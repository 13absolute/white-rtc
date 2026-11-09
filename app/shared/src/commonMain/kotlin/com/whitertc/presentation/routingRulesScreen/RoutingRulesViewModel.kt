package com.whitertc.presentation.routingRulesScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.whitertc.domain.model.DEFAULT_EXAMPLE_TEMPLATE
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingPreset
import com.whitertc.domain.model.RoutingTemplate
import com.whitertc.domain.usecase.GetVpnConfigUseCase
import com.whitertc.domain.usecase.SaveVpnConfigUseCase
import com.whitertc.resources.Res
import com.whitertc.resources.routing_reset_default
import com.whitertc.resources.rule_template_deleted
import com.whitertc.resources.rule_template_saved_fmt
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class RoutingRulesViewModel(
    private val getVpnConfigUseCase: GetVpnConfigUseCase,
    private val saveVpnConfigUseCase: SaveVpnConfigUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RoutingRulesState())
    val state: StateFlow<RoutingRulesState> = _state.asStateFlow()

    private val _effect = Channel<RoutingRulesEffect>(Channel.BUFFERED)
    val effect: Flow<RoutingRulesEffect> = _effect.receiveAsFlow()

    private var saveDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            val config = getVpnConfigUseCase()
            _state.update {
                it.copy(
                    routingMode = config.routingMode,
                    selectedPreset = config.routingPreset,
                    activeTemplateId = config.activeTemplateId,
                    routingTemplates = config.routingTemplates,
                    fallbackOnAnyFailure = config.fallbackOnAnyFailure,
                    seamlessMigrationEnabled = config.seamlessMigrationEnabled,
                    customProbeTimeoutMs = config.customProbeTimeoutMs,
                    customProbeIntervalMs = config.customProbeIntervalMs,
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: RoutingRulesEvent) {
        when (event) {
            is RoutingRulesEvent.RoutingModeSelected -> {
                val preset = when (event.mode) {
                    RoutingMode.DIRECT_ONLY -> RoutingPreset.DIRECT_ONLY
                    RoutingMode.RELAY_ONLY -> RoutingPreset.RELAY_ONLY
                    RoutingMode.BY_TEMPLATE -> RoutingPreset.ADAPTIVE
                }
                _state.update { it.copy(routingMode = event.mode, selectedPreset = preset) }
                scheduleSave()
            }
            is RoutingRulesEvent.PresetSelected -> {
                val mode = when (event.preset) {
                    RoutingPreset.DIRECT_ONLY -> RoutingMode.DIRECT_ONLY
                    RoutingPreset.RELAY_ONLY -> RoutingMode.RELAY_ONLY
                    else -> RoutingMode.BY_TEMPLATE
                }
                _state.update { it.copy(selectedPreset = event.preset, routingMode = mode) }
                scheduleSave()
            }
            is RoutingRulesEvent.ActiveTemplateSelected -> {
                _state.update { it.copy(activeTemplateId = event.templateId) }
                scheduleSave()
            }
            is RoutingRulesEvent.StartEditingTemplate -> {
                _state.update { it.copy(editingTemplate = event.template, isCreatingTemplate = false) }
            }
            is RoutingRulesEvent.StartCreatingTemplate -> {
                val newId = "tpl_${kotlin.random.Random.nextInt(100000, 999999)}"
                val newTpl = RoutingTemplate(
                    id = newId,
                    name = "",
                    isDefault = false,
                    unreachableHosts = emptyList(),
                    reachableHosts = emptyList(),
                    probeTimeoutMs = 1500L,
                    probeIntervalMs = 15000L
                )
                _state.update { it.copy(editingTemplate = newTpl, isCreatingTemplate = true) }
            }
            is RoutingRulesEvent.CancelEditTemplate -> {
                _state.update { it.copy(editingTemplate = null, isCreatingTemplate = false) }
            }
            is RoutingRulesEvent.SaveTemplate -> {
                val tpl = event.template
                _state.update { state ->
                    val existingIndex = state.routingTemplates.indexOfFirst { it.id == tpl.id }
                    val updatedTemplates = if (existingIndex >= 0) {
                        state.routingTemplates.toMutableList().apply { set(existingIndex, tpl) }
                    } else {
                        state.routingTemplates + tpl
                    }
                    state.copy(
                        routingTemplates = updatedTemplates,
                        activeTemplateId = tpl.id,
                        editingTemplate = null,
                        isCreatingTemplate = false
                    )
                }
                scheduleSave()
                viewModelScope.launch {
                    _effect.send(RoutingRulesEffect.ShowResourceMessage(Res.string.rule_template_saved_fmt, listOf(event.template.name)))
                }
            }
            is RoutingRulesEvent.DeleteTemplate -> {
                _state.update { state ->
                    val updated = state.routingTemplates.filterNot { it.id == event.templateId }
                    val nextActive = if (state.activeTemplateId == event.templateId) {
                        updated.firstOrNull()?.id ?: DEFAULT_EXAMPLE_TEMPLATE.id
                    } else {
                        state.activeTemplateId
                    }
                    state.copy(
                        routingTemplates = if (updated.isNotEmpty()) updated else listOf(DEFAULT_EXAMPLE_TEMPLATE),
                        activeTemplateId = nextActive,
                        editingTemplate = null,
                        isCreatingTemplate = false
                    )
                }
                scheduleSave()
                viewModelScope.launch {
                    _effect.send(RoutingRulesEffect.ShowResourceMessage(Res.string.rule_template_deleted))
                }
            }
            is RoutingRulesEvent.FallbackOnAnyFailureToggled -> {
                _state.update { it.copy(fallbackOnAnyFailure = event.enabled) }
                scheduleSave()
            }
            is RoutingRulesEvent.SeamlessMigrationToggled -> {
                _state.update { it.copy(seamlessMigrationEnabled = event.enabled) }
                scheduleSave()
            }
            is RoutingRulesEvent.ProbeTimeoutChanged -> {
                _state.update { it.copy(customProbeTimeoutMs = event.timeoutMs) }
                scheduleSave()
            }
            is RoutingRulesEvent.ProbeIntervalChanged -> {
                _state.update { it.copy(customProbeIntervalMs = event.intervalMs) }
                scheduleSave()
            }
            is RoutingRulesEvent.ResetToDefaultClicked -> {
                _state.update {
                    it.copy(
                        routingMode = RoutingMode.BY_TEMPLATE,
                        selectedPreset = RoutingPreset.ADAPTIVE,
                        activeTemplateId = DEFAULT_EXAMPLE_TEMPLATE.id,
                        routingTemplates = listOf(DEFAULT_EXAMPLE_TEMPLATE),
                        editingTemplate = null,
                        isCreatingTemplate = false,
                        fallbackOnAnyFailure = false,
                        seamlessMigrationEnabled = true,
                        customProbeTimeoutMs = 1500L,
                        customProbeIntervalMs = 15000L
                    )
                }
                scheduleSave()
                viewModelScope.launch {
                    _effect.send(RoutingRulesEffect.ShowResourceMessage(Res.string.routing_reset_default))
                }
            }
        }
    }

    private fun scheduleSave() {
        saveDebounceJob?.cancel()
        saveDebounceJob = viewModelScope.launch {
            delay(150)
            val current = _state.value
            val config = getVpnConfigUseCase()
            saveVpnConfigUseCase(
                config.copy(
                    routingMode = current.routingMode,
                    routingPreset = current.selectedPreset,
                    activeTemplateId = current.activeTemplateId,
                    routingTemplates = current.routingTemplates,
                    fallbackOnAnyFailure = current.fallbackOnAnyFailure,
                    seamlessMigrationEnabled = current.seamlessMigrationEnabled,
                    customProbeTimeoutMs = current.customProbeTimeoutMs,
                    customProbeIntervalMs = current.customProbeIntervalMs
                )
            )
        }
    }
}
