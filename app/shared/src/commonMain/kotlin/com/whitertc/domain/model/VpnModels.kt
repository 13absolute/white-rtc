package com.whitertc.domain.model

import kotlinx.serialization.Serializable
import com.whitertc.core.protocol.WebRtcMimicFramer

@Serializable
data class RoutingTemplate(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val unreachableHosts: List<String> = emptyList(),
    val reachableHosts: List<String> = emptyList(),
    val probeTimeoutMs: Long = 1500L,
    val probeIntervalMs: Long = 15000L
)

val DEFAULT_EXAMPLE_TEMPLATE = RoutingTemplate(
    id = "template_example",
    name = "Default",
    isDefault = true,
    unreachableHosts = listOf("connectivitycheck.gstatic.com", "google.com", "captive.apple.com"),
    reachableHosts = listOf("vk.com", "yandex.ru", "gosuslugi.ru"),
    probeTimeoutMs = 1500L,
    probeIntervalMs = 15000L
)

enum class RoutingMode(
    val id: String,
    val displayName: String,
    val description: String,
    val connectionMode: String
) {
    DIRECT_ONLY("direct", "Direct Only", "Direct server connection on maximum speed", "direct"),
    RELAY_ONLY("relay", "Relay Only", "Traffic strictly encapsulated via trusted relay", "turn"),
    BY_TEMPLATE("template", "By Template", "Direct channel with failover to relay by template rules", "auto");

    companion object {
        fun fromId(id: String?): RoutingMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BY_TEMPLATE
        }
    }
}

enum class TurnProviderType(
    val id: String,
    val displayName: String,
    val placeholder: String,
    val meshLabel: String
) {
    VK_CALLS("vk_calls", "VK TURN", "https://vk.com/call/join/...", "BACKUP"),
    STANDARD_TURN("standard_turn", "TURN RFC 5766", "turn://user:pass@host:port", "BACKUP"),
    DIRECT_WEBRTC("direct_webrtc", "Main Server", "vps.example.com", "MAIN");

    companion object {
        fun fromId(id: String?): TurnProviderType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DIRECT_WEBRTC
        }
    }
}

data class ConnectionProfile(
    val id: String,
    val name: String,
    val providerType: TurnProviderType,
    val serverHost: String,
    val serverPort: Int,
    val serverPorts: List<Int>,
    val sharedSecret: String,
    val signalingSource: String,
    val isActive: Boolean,
    val pingMs: Long,
    val tunnelSubnet: String = "10.8.0.2/24",
    val parallelChannels: Int = 8
) {
    val displayName: String
        get() = ProfileLinkParser.cleanProfileDisplayName(name)
}

enum class RoutingPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val connectionMode: String
) {
    ADAPTIVE("adaptive", "Adaptive (Default)", "Direct connection with automated relay failover on external segment failures", "auto"),
    DIRECT_ONLY("direct", "Direct Only", "Strictly direct connection without intermediary relays", "direct"),
    RELAY_ONLY("relay", "Stealth Relay", "Continuous tunneling via trusted relays without direct traffic", "turn"),
    CUSTOM("custom", "Custom", "Manual configuration of thresholds, timeouts and canary hosts", "auto");

    companion object {
        fun fromId(id: String?): RoutingPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ADAPTIVE
        }
    }
}

data class VpnConfig(
    val serverHost: String,
    val serverPort: Int,
    val serverPorts: List<Int>,
    val sharedSecret: String,
    val providerType: TurnProviderType,
    val signalingSource: String,
    val vkToken: String,
    val callId: String,
    val turnHost: String,
    val turnPort: Int,
    val turnPassword: String,
    val tunnelSubnet: String,
    val mtu: Int,
    val hotspotEnabled: Boolean,
    val hotspotPort: Int,
    val parallelChannels: Int,
    val bypassLan: Boolean = false,
    val splitTunnelingMode: SplitTunnelingMode = SplitTunnelingMode.ALL,
    val splitTunnelingPackages: Set<String> = emptySet(),
    val debugLoggingEnabled: Boolean = false,
    val routingPreset: RoutingPreset = RoutingPreset.ADAPTIVE,
    val routingMode: RoutingMode = RoutingMode.BY_TEMPLATE,
    val activeTemplateId: String = "template_example",
    val routingTemplates: List<RoutingTemplate> = listOf(DEFAULT_EXAMPLE_TEMPLATE),
    val fallbackOnAnyFailure: Boolean = false,
    val seamlessMigrationEnabled: Boolean = true,
    val customProbeTimeoutMs: Long = 1500L,
    val customProbeIntervalMs: Long = 15000L
) {
    val activeTemplate: RoutingTemplate
        get() = routingTemplates.firstOrNull { it.id == activeTemplateId } ?: routingTemplates.firstOrNull() ?: DEFAULT_EXAMPLE_TEMPLATE

    companion object {
        fun initial(): VpnConfig = VpnConfig(
            serverHost = "",
            serverPort = 3478,
            serverPorts = emptyList(),
            sharedSecret = "",
            providerType = TurnProviderType.DIRECT_WEBRTC,
            signalingSource = "",
            vkToken = "",
            callId = "",
            turnHost = "",
            turnPort = 3478,
            turnPassword = "",
            tunnelSubnet = "10.8.0.2/24",
            mtu = WebRtcMimicFramer.RECOMMENDED_TUN_MTU,
            hotspotEnabled = false,
            hotspotPort = 1080,
            parallelChannels = 8,
            bypassLan = false,
            splitTunnelingMode = SplitTunnelingMode.ALL,
            splitTunnelingPackages = emptySet(),
            debugLoggingEnabled = false,
            routingPreset = RoutingPreset.ADAPTIVE,
            routingMode = RoutingMode.BY_TEMPLATE,
            activeTemplateId = "template_example",
            routingTemplates = listOf(DEFAULT_EXAMPLE_TEMPLATE),
            fallbackOnAnyFailure = false,
            seamlessMigrationEnabled = true,
            customProbeTimeoutMs = 1500L,
            customProbeIntervalMs = 15000L
        )
    }
}

enum class SplitTunnelingMode(val displayName: String) {
    ALL("All Applications"),
    EXCLUDE("Blacklist"),
    INCLUDE("Whitelist")
}


sealed interface VpnStatus {
    data object Disconnected : VpnStatus
    data class Connecting(val stage: String) : VpnStatus
    data class Connected(val stats: TunnelStats) : VpnStatus
    data class Error(val message: String) : VpnStatus
}

enum class ChannelMediaType(
    val codec: String,
    val rtpPayloadType: Int
) {
    AUDIO(codec = "OPUS", rtpPayloadType = 111),
    VIDEO(codec = "VP8", rtpPayloadType = 96)
}

data class ChannelTelemetry(
    val index: Int,
    val port: Int,
    val mediaType: ChannelMediaType,
    val downlinkBps: Long,
    val uplinkBps: Long,
    val rttMs: Long,
    val isActive: Boolean,
    val lostPackets: Int,
    val mtu: Int
)

data class TunnelStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val packetsIn: Long = 0L,
    val packetsOut: Long = 0L,
    val pingMs: Long = 0L,
    val activeChannels: Int = 0,
    val totalChannels: Int = 0,
    val downlinkBps: Long = 0L,
    val uplinkBps: Long = 0L,
    val channels: List<ChannelTelemetry> = emptyList()
)

data class HotspotConfig(
    val enabled: Boolean,
    val port: Int
)
