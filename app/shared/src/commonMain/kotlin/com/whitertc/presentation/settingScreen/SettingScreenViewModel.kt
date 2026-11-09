package com.whitertc.presentation.settingScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.repository.AppInfoProvider
import com.whitertc.domain.repository.AppLogManager
import com.whitertc.domain.repository.HotspotManager
import com.whitertc.domain.usecase.DisconnectVpnUseCase
import com.whitertc.domain.usecase.GetVpnConfigUseCase
import com.whitertc.domain.usecase.ObserveConnectionProfilesUseCase
import com.whitertc.domain.usecase.ObserveVpnConfigUseCase
import com.whitertc.domain.usecase.SaveConnectionProfileUseCase
import com.whitertc.domain.usecase.DeleteConnectionProfileUseCase
import com.whitertc.domain.usecase.SaveVpnConfigUseCase
import com.whitertc.plugin.PluginRegistryHelper
import com.whitertc.plugin.PluginStorageManager
import com.whitertc.resources.Res
import com.whitertc.resources.logs_cleared
import com.whitertc.resources.plugin_deleted
import com.whitertc.resources.plugin_install_error_fmt
import com.whitertc.resources.plugin_installed_fmt
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SettingScreenViewModel(
    private val getVpnConfigUseCase: GetVpnConfigUseCase,
    private val observeVpnConfigUseCase: ObserveVpnConfigUseCase,
    private val saveVpnConfigUseCase: SaveVpnConfigUseCase,
    private val hotspotManager: HotspotManager,
    private val disconnectVpnUseCase: DisconnectVpnUseCase,
    private val observeConnectionProfilesUseCase: ObserveConnectionProfilesUseCase,
    private val saveConnectionProfileUseCase: SaveConnectionProfileUseCase,
    private val deleteConnectionProfileUseCase: DeleteConnectionProfileUseCase,
    private val pluginStorageManager: PluginStorageManager,
    private val appLogManager: AppLogManager,
    private val appInfoProvider: AppInfoProvider
) : ViewModel() {

    companion object {
        const val DEFAULT_HOTSPOT_PORT = 10808
    }

    private val _state = MutableStateFlow(
        SettingState(
            isDesktop = appInfoProvider.isDesktop,
            appVersion = appInfoProvider.appVersion,
            buildNumber = appInfoProvider.buildNumber,
            platformInfo = appInfoProvider.platformName
        )
    )
    val state: StateFlow<SettingState> = _state.asStateFlow()

    private val _effect = Channel<SettingEffect>(Channel.BUFFERED)
    val effect: Flow<SettingEffect> = _effect.receiveAsFlow()

    private var ipObservationJob: Job? = null

    init {
        appLogManager.initialize()
        viewModelScope.launch {
            observeVpnConfigUseCase().collectLatest { config ->
                val port = if (config.hotspotPort > 0) config.hotspotPort else DEFAULT_HOTSPOT_PORT
                appLogManager.setDebugEnabled(config.debugLoggingEnabled)
                val activeTemplate = config.routingTemplates.firstOrNull { it.id == config.activeTemplateId }
                    ?: com.whitertc.domain.model.DEFAULT_EXAMPLE_TEMPLATE
                _state.update {
                    it.copy(
                        hotspotEnabled = config.hotspotEnabled,
                        hotspotPort = port,
                        bypassLan = config.bypassLan,
                        splitTunnelingMode = config.splitTunnelingMode,
                        splitTunnelingPackagesCount = config.splitTunnelingPackages.size,
                        installedPlugins = PluginRegistryHelper.getInstalledPlugins(),
                        debugLoggingEnabled = config.debugLoggingEnabled,
                        logFileSize = appLogManager.getLogFileSizeFormatted(),
                        routingPreset = config.routingPreset,
                        routingMode = config.routingMode,
                        activeTemplateName = activeTemplate.name,
                        appVersion = appInfoProvider.appVersion,
                        buildNumber = appInfoProvider.buildNumber,
                        platformInfo = appInfoProvider.platformName
                    )
                }
                if (config.hotspotEnabled && ipObservationJob == null) {
                    startObservingHotspotIp()
                } else if (!config.hotspotEnabled && ipObservationJob != null) {
                    stopObservingHotspotIp()
                }
            }
        }
    }

    fun onEvent(event: SettingEvent) {
        when (event) {
            is SettingEvent.HotspotEnabledChanged -> {
                _state.update { it.copy(hotspotEnabled = event.value) }
                if (event.value) {
                    startObservingHotspotIp()
                } else {
                    stopObservingHotspotIp()
                }
                viewModelScope.launch {
                    val current = getVpnConfigUseCase()
                    saveVpnConfigUseCase(
                        current.copy(
                            hotspotEnabled = event.value,
                            hotspotPort = _state.value.hotspotPort
                        )
                    )
                }
            }
            is SettingEvent.BypassLanChanged -> {
                _state.update { it.copy(bypassLan = event.value) }
                viewModelScope.launch {
                    val current = getVpnConfigUseCase()
                    saveVpnConfigUseCase(current.copy(bypassLan = event.value))
                }
            }
            is SettingEvent.OpenHotspotSettingsClicked -> {
                hotspotManager.openHotspotSettings()
            }
            is SettingEvent.PluginInstalled -> {
                _state.update { it.copy(installedPlugins = PluginRegistryHelper.getInstalledPlugins()) }
                viewModelScope.launch {
                    _effect.send(SettingEffect.ShowResourceMessage(Res.string.plugin_installed_fmt, listOf(event.pluginName)))
                }
            }
            is SettingEvent.PluginInstallFailed -> {
                viewModelScope.launch {
                    _effect.send(SettingEffect.ShowResourceMessage(Res.string.plugin_install_error_fmt, listOf(event.errorMessage)))
                }
            }
            is SettingEvent.DeletePlugin -> {
                viewModelScope.launch {
                    val current = getVpnConfigUseCase()
                    val provider = RelayProviderRegistry.get(event.pluginId)
                    val isCurrentlyActive = current.providerType.id == event.pluginId ||
                        (provider != null && current.signalingSource.isNotBlank() && provider.canHandle(current.signalingSource))
                    if (isCurrentlyActive) {
                        disconnectVpnUseCase()
                    }

                    val profiles = observeConnectionProfilesUseCase().first()
                    for (profile in profiles) {
                        val matches = profile.providerType.id == event.pluginId ||
                            (provider != null && profile.signalingSource.isNotBlank() && provider.canHandle(profile.signalingSource))
                        if (matches) {
                            if (profile.serverHost.isBlank() || profile.serverHost == "calls.okcdn.ru") {
                                deleteConnectionProfileUseCase(profile.id)
                            } else {
                                val updated = profile.copy(
                                    providerType = if (profile.providerType.id == event.pluginId) TurnProviderType.DIRECT_WEBRTC else profile.providerType,
                                    signalingSource = if (provider != null && provider.canHandle(profile.signalingSource)) "" else profile.signalingSource
                                )
                                saveConnectionProfileUseCase(updated)
                            }
                        }
                    }

                    pluginStorageManager.deletePlugin(event.pluginId)
                    _state.update { it.copy(installedPlugins = PluginRegistryHelper.getInstalledPlugins()) }
                    _effect.send(SettingEffect.ShowResourceMessage(Res.string.plugin_deleted))
                }
            }
            is SettingEvent.DebugLoggingChanged -> {
                appLogManager.setDebugEnabled(event.value)
                _state.update { it.copy(debugLoggingEnabled = event.value, logFileSize = appLogManager.getLogFileSizeFormatted()) }
                viewModelScope.launch {
                    val current = getVpnConfigUseCase()
                    saveVpnConfigUseCase(current.copy(debugLoggingEnabled = event.value))
                }
            }
            is SettingEvent.ClearLogsClicked -> {
                appLogManager.clearLogs()
                _state.update { it.copy(logFileSize = appLogManager.getLogFileSizeFormatted()) }
                viewModelScope.launch {
                    _effect.send(SettingEffect.ShowResourceMessage(Res.string.logs_cleared))
                }
            }
            is SettingEvent.ShareLogsClicked -> {
                appLogManager.shareLogs()
            }
        }
    }

    private fun startObservingHotspotIp() {
        ipObservationJob?.cancel()
        ipObservationJob = viewModelScope.launch {
            hotspotManager.observeHotspotIp().collectLatest { ip ->
                _state.update { it.copy(hotspotIp = ip) }
            }
        }
    }

    private fun stopObservingHotspotIp() {
        ipObservationJob?.cancel()
        ipObservationJob = null
        _state.update { it.copy(hotspotIp = null) }
    }
}