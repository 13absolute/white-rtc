package com.whitertc.presentation.mainScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.whitertc.domain.model.ChannelMediaType
import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.speedtest.VpsSpeedTestRunner
import com.whitertc.domain.usecase.ConnectVpnUseCase
import com.whitertc.domain.usecase.DeleteConnectionProfileUseCase
import com.whitertc.domain.usecase.DisconnectVpnUseCase
import com.whitertc.domain.usecase.ObserveConnectionProfilesUseCase
import com.whitertc.domain.usecase.ObserveTunnelStatsUseCase
import com.whitertc.domain.usecase.ObserveVpnConfigUseCase
import com.whitertc.domain.usecase.ObserveVpnStateUseCase
import com.whitertc.domain.usecase.SaveConnectionProfileUseCase
import com.whitertc.domain.usecase.SelectConnectionProfileUseCase
import com.whitertc.resources.Res
import com.whitertc.resources.*
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MainScreenViewModel(
    private val observeVpnStateUseCase: ObserveVpnStateUseCase,
    private val observeTunnelStatsUseCase: ObserveTunnelStatsUseCase,
    private val observeVpnConfigUseCase: ObserveVpnConfigUseCase,
    private val observeConnectionProfilesUseCase: ObserveConnectionProfilesUseCase,
    private val selectConnectionProfileUseCase: SelectConnectionProfileUseCase,
    private val saveConnectionProfileUseCase: SaveConnectionProfileUseCase,
    private val deleteConnectionProfileUseCase: DeleteConnectionProfileUseCase,
    private val connectVpnUseCase: ConnectVpnUseCase,
    private val disconnectVpnUseCase: DisconnectVpnUseCase,
    private val vpsSpeedTestRunner: VpsSpeedTestRunner
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _effect = Channel<MainEffect>(Channel.BUFFERED)
    val effect: Flow<MainEffect> = _effect.receiveAsFlow()

    private var sessionTickerJob: Job? = null

    init {
        viewModelScope.launch {
            observeVpnStateUseCase().collectLatest { status ->
                val (phase, statusMessage, isTelemetry) = when (status) {
                    is VpnStatus.Disconnected -> Triple(
                        ConnectionPhase.DISCONNECTED,
                        "",
                        false
                    )
                    is VpnStatus.Connecting -> {
                        val channels = _state.value.totalChannels
                        val msg = if (_state.value.providerType == TurnProviderType.VK_CALLS) {
                            if (status.stage.contains("Authenticating", ignoreCase = true) ||
                                status.stage.contains("VK", ignoreCase = true) ||
                                status.stage.contains("Preparing", ignoreCase = true)
                            ) {
                                "RESOLVING RELAYS..."
                            } else {
                                "CONNECTING $channels CHANNELS..."
                            }
                        } else {
                            "SYNCING $channels CHANNELS..."
                        }
                        Triple(
                            ConnectionPhase.CONNECTING,
                            msg,
                            false
                        )
                    }
                    is VpnStatus.Connected -> Triple(
                        ConnectionPhase.CONNECTED,
                        "",
                        true
                    )
                    is VpnStatus.Error -> Triple(
                        ConnectionPhase.ERROR,
                        "✕ ${status.message.uppercase()}",
                        false
                    )
                }

                if (phase == ConnectionPhase.CONNECTED) {
                    val connectedTime = _state.value.connectedTimestampMs ?: Clock.System.now().toEpochMilliseconds()
                    if (sessionTickerJob == null || sessionTickerJob?.isActive != true) {
                        sessionTickerJob?.cancel()
                        sessionTickerJob = viewModelScope.launch {
                            while (true) {
                                val nowMs = Clock.System.now().toEpochMilliseconds()
                                val elapsedSec = maxOf(0L, (nowMs - connectedTime) / 1000L)
                                _state.update { it.copy(sessionDurationText = formatDuration(elapsedSec)) }
                                delay(1000L)
                            }
                        }
                    }
                    _state.update {
                        it.copy(
                            status = status,
                            phase = phase,
                            statusMessage = statusMessage,
                            isTelemetryVisible = isTelemetry,
                            isLoading = false,
                            connectedTimestampMs = connectedTime
                        )
                    }
                } else {
                    sessionTickerJob?.cancel()
                    sessionTickerJob = null
                    _state.update {
                        it.copy(
                            status = status,
                            phase = phase,
                            statusMessage = statusMessage,
                            isTelemetryVisible = isTelemetry,
                            isLoading = phase == ConnectionPhase.CONNECTING,
                            connectedTimestampMs = null,
                            sessionDurationText = "00:00:00"
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            observeTunnelStatsUseCase().collectLatest { stats ->
                val audio = stats.channels.count { it.mediaType == ChannelMediaType.AUDIO }
                val video = stats.channels.count { it.mediaType == ChannelMediaType.VIDEO }
                _state.update {
                    it.copy(
                        stats = stats,
                        audioChannelsCount = audio,
                        videoChannelsCount = video
                    )
                }
            }
        }
        viewModelScope.launch {
            observeVpnConfigUseCase().collectLatest { config ->
                val channels = (config.serverPorts.size.takeIf { it > 0 } ?: config.parallelChannels).coerceIn(1, 64)
                _state.update {
                    it.copy(
                        serverHost = config.serverHost,
                        serverPort = config.serverPort,
                        providerType = config.providerType,
                        meshLabel = config.providerType.meshLabel,
                        totalChannels = channels
                    )
                }
            }
        }
        viewModelScope.launch {
            observeConnectionProfilesUseCase().collectLatest { profiles ->
                val active = profiles.firstOrNull { it.isActive }
                if (profiles.isNotEmpty() && active == null) {
                    selectConnectionProfileUseCase(profiles.first().id)
                    return@collectLatest
                }
                val channels = (active?.serverPorts?.size?.takeIf { it > 0 } ?: _state.value.totalChannels).coerceIn(1, 64)
                _state.update {
                    it.copy(
                        profiles = profiles,
                        activeProfile = active,
                        meshLabel = active?.providerType?.meshLabel ?: it.meshLabel,
                        totalChannels = channels
                    )
                }
            }
        }
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.ConnectClicked -> handleConnect()
            is MainEvent.DisconnectClicked -> handleDisconnect()
            is MainEvent.TargetClicked -> _state.update { it.copy(isProfileSheetOpen = true) }
            is MainEvent.DismissProfileSheet -> _state.update { it.copy(isProfileSheetOpen = false) }
            is MainEvent.SelectProfile -> handleSelectProfile(event.id)
            is MainEvent.SaveNewProfile -> handleSaveProfile(event.profile)
            is MainEvent.DeleteProfile -> handleDeleteProfile(event.id)
            is MainEvent.OpenSettings -> { /* Handled at Route level via Navigator */ }
            is MainEvent.RunSpeedTest -> handleRunSpeedTest()
        }
    }

    private fun handleSelectProfile(id: String) {
        viewModelScope.launch {
            try {
                val wasConnected = _state.value.status is VpnStatus.Connected
                _state.update { it.copy(isProfileSheetOpen = false) }
                selectConnectionProfileUseCase(id)
                if (wasConnected) {
                    disconnectVpnUseCase()
                    delay(400)
                    _effect.send(MainEffect.RequestVpnPermission)
                    connectVpnUseCase()
                }
            } catch (t: Throwable) {
                _effect.send(t.message?.let { MainEffect.ShowMessage(it) } ?: MainEffect.ShowResourceMessage(Res.string.profile_select_error))
            }
        }
    }

    private fun handleSaveProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            try {
                saveConnectionProfileUseCase(profile)
            } catch (t: Throwable) {
                _effect.send(t.message?.let { MainEffect.ShowMessage(it) } ?: MainEffect.ShowResourceMessage(Res.string.profile_save_error))
            }
        }
    }

    private fun handleDeleteProfile(id: String) {
        viewModelScope.launch {
            try {
                if (_state.value.activeProfile?.id == id && _state.value.status !is VpnStatus.Disconnected) {
                    disconnectVpnUseCase()
                }
                deleteConnectionProfileUseCase(id)
            } catch (t: Throwable) {
                _effect.send(t.message?.let { MainEffect.ShowMessage(it) } ?: MainEffect.ShowResourceMessage(Res.string.profile_delete_error))
            }
        }
    }

    private fun handleRunSpeedTest() {
        // Disabled in release build: synthetic benchmark ports (8089 HTTP, 8625 TWAMP) are closed
    }

    private fun handleConnect() {
        if (_state.value.profiles.isEmpty()) {
            _state.update { it.copy(isProfileSheetOpen = true) }
            return
        }
        val active = _state.value.activeProfile
        if (active == null || _state.value.serverHost.isBlank()) {
            _state.update { it.copy(isProfileSheetOpen = true) }
            return
        }
        val channels = _state.value.totalChannels
        val initialMsg = if (_state.value.providerType == TurnProviderType.VK_CALLS) {
            "RESOLVING RELAYS..."
        } else {
            "SYNCING $channels CHANNELS..."
        }
        _state.update {
            it.copy(
                phase = ConnectionPhase.CONNECTING,
                statusMessage = initialMsg,
                isLoading = true
            )
        }
        viewModelScope.launch {
            try {
                _effect.send(MainEffect.RequestVpnPermission)
                connectVpnUseCase()
            } catch (t: Throwable) {
                _effect.send(MainEffect.ShowMessage(t.message ?: "Tunnel connection error"))
            }
        }
    }

    private fun handleDisconnect() {
        viewModelScope.launch {
            try {
                disconnectVpnUseCase()
            } catch (t: Throwable) {
                _effect.send(MainEffect.ShowMessage(t.message ?: "Disconnect error"))
            }
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val h = if (hours < 10) "0$hours" else "$hours"
        val m = if (minutes < 10) "0$minutes" else "$minutes"
        val s = if (seconds < 10) "0$seconds" else "$seconds"
        return "$h:$m:$s"
    }

    override fun onCleared() {
        super.onCleared()
        sessionTickerJob?.cancel()
        sessionTickerJob = null
    }
}