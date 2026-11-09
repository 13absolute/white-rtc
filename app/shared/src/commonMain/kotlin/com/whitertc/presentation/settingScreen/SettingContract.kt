package com.whitertc.presentation.settingScreen

import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingPreset
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.plugin.PluginUiModel
import org.jetbrains.compose.resources.StringResource

data class SettingState(
    val isDesktop: Boolean = false,
    val hotspotEnabled: Boolean = false,
    val hotspotIp: String? = null,
    val hotspotPort: Int = 10808,
    val bypassLan: Boolean = false,
    val splitTunnelingMode: SplitTunnelingMode = SplitTunnelingMode.ALL,
    val splitTunnelingPackagesCount: Int = 0,
    val installedPlugins: List<PluginUiModel> = emptyList(),
    val debugLoggingEnabled: Boolean = false,
    val logFileSize: String = "0 B",
    val routingPreset: RoutingPreset = RoutingPreset.ADAPTIVE,
    val routingMode: RoutingMode = RoutingMode.BY_TEMPLATE,
    val activeTemplateName: String = "Example",
    val appVersion: String = "1.0.0",
    val buildNumber: String = "1",
    val platformInfo: String = "Android"
)

sealed interface SettingEvent {
    data class HotspotEnabledChanged(val value: Boolean) : SettingEvent
    data class BypassLanChanged(val value: Boolean) : SettingEvent
    data object OpenHotspotSettingsClicked : SettingEvent
    data class PluginInstalled(val pluginName: String) : SettingEvent
    data class PluginInstallFailed(val errorMessage: String) : SettingEvent
    data class DeletePlugin(val pluginId: String) : SettingEvent
    data class DebugLoggingChanged(val value: Boolean) : SettingEvent
    data object ClearLogsClicked : SettingEvent
    data object ShareLogsClicked : SettingEvent
}

sealed interface SettingEffect {
    data class ShowMessage(val message: String) : SettingEffect
    data class ShowResourceMessage(val res: StringResource, val args: List<Any> = emptyList()) : SettingEffect
    data object NavigateBack : SettingEffect
}
