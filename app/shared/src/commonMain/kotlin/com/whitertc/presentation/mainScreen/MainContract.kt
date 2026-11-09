package com.whitertc.presentation.mainScreen

import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.speedtest.SpeedTestState
import org.jetbrains.compose.resources.StringResource

enum class ConnectionPhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR;

    val isConnected: Boolean get() = this == CONNECTED
    val isConnecting: Boolean get() = this == CONNECTING
    val isError: Boolean get() = this == ERROR
}

data class MainState(
    val status: VpnStatus = VpnStatus.Disconnected,
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val statusMessage: String = "",
    val isTelemetryVisible: Boolean = false,
    val sessionDurationText: String = "00:00:00",
    val stats: TunnelStats = TunnelStats(),
    val serverHost: String = "",
    val serverPort: Int = 3478,
    val providerType: TurnProviderType = TurnProviderType.DIRECT_WEBRTC,
    val meshLabel: String = "MAIN",
    val audioChannelsCount: Int = 0,
    val videoChannelsCount: Int = 0,
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfile: ConnectionProfile? = null,
    val totalChannels: Int = 8,
    val isProfileSheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val speedTestState: SpeedTestState = SpeedTestState.Idle,
    val connectedTimestampMs: Long? = null
)

sealed interface MainEvent {
    data object ConnectClicked : MainEvent
    data object DisconnectClicked : MainEvent
    data object TargetClicked : MainEvent
    data object DismissProfileSheet : MainEvent
    data class SelectProfile(val id: String) : MainEvent
    data class SaveNewProfile(val profile: ConnectionProfile) : MainEvent
    data class DeleteProfile(val id: String) : MainEvent
    data object OpenSettings : MainEvent
    data object RunSpeedTest : MainEvent
}

sealed interface MainEffect {
    data class ShowMessage(val message: String) : MainEffect
    data class ShowResourceMessage(val res: StringResource, val args: List<Any> = emptyList()) : MainEffect
    data object RequestVpnPermission : MainEffect
}
