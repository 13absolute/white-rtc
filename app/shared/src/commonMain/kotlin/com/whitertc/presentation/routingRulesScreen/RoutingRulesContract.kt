package com.whitertc.presentation.routingRulesScreen

import com.whitertc.domain.model.DEFAULT_EXAMPLE_TEMPLATE
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingPreset
import com.whitertc.domain.model.RoutingTemplate
import org.jetbrains.compose.resources.StringResource

data class RoutingRulesState(
    val routingMode: RoutingMode = RoutingMode.BY_TEMPLATE,
    val selectedPreset: RoutingPreset = RoutingPreset.ADAPTIVE,
    val activeTemplateId: String = "template_example",
    val routingTemplates: List<RoutingTemplate> = listOf(DEFAULT_EXAMPLE_TEMPLATE),
    val editingTemplate: RoutingTemplate? = null,
    val isCreatingTemplate: Boolean = false,
    val fallbackOnAnyFailure: Boolean = false,
    val seamlessMigrationEnabled: Boolean = true,
    val customProbeTimeoutMs: Long = 1500L,
    val customProbeIntervalMs: Long = 15000L,
    val isLoading: Boolean = true
) {
    val activeTemplate: RoutingTemplate
        get() = routingTemplates.firstOrNull { it.id == activeTemplateId } ?: routingTemplates.firstOrNull() ?: DEFAULT_EXAMPLE_TEMPLATE
}

sealed interface RoutingRulesEvent {
    data class RoutingModeSelected(val mode: RoutingMode) : RoutingRulesEvent
    data class PresetSelected(val preset: RoutingPreset) : RoutingRulesEvent
    data class ActiveTemplateSelected(val templateId: String) : RoutingRulesEvent
    data class StartEditingTemplate(val template: RoutingTemplate) : RoutingRulesEvent
    data object StartCreatingTemplate : RoutingRulesEvent
    data object CancelEditTemplate : RoutingRulesEvent
    data class SaveTemplate(val template: RoutingTemplate) : RoutingRulesEvent
    data class DeleteTemplate(val templateId: String) : RoutingRulesEvent
    data class FallbackOnAnyFailureToggled(val enabled: Boolean) : RoutingRulesEvent
    data class SeamlessMigrationToggled(val enabled: Boolean) : RoutingRulesEvent
    data class ProbeTimeoutChanged(val timeoutMs: Long) : RoutingRulesEvent
    data class ProbeIntervalChanged(val intervalMs: Long) : RoutingRulesEvent
    data object ResetToDefaultClicked : RoutingRulesEvent
}

sealed interface RoutingRulesEffect {
    data object NavigateBack : RoutingRulesEffect
    data class ShowMessage(val message: String) : RoutingRulesEffect
    data class ShowResourceMessage(val res: StringResource, val args: List<Any> = emptyList()) : RoutingRulesEffect
}
