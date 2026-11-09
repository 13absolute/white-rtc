package com.whitertc.domain.usecase

import kotlinx.coroutines.flow.Flow
import com.whitertc.core.turn.ResolvedTurnSession
import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnConfig
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.repository.TurnSignalingRepository
import com.whitertc.domain.repository.VpnCommand
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.domain.repository.VpnStateRepository
import org.koin.core.annotation.Factory

@Factory
class ObserveVpnStateUseCase(
    private val vpnStateRepository: VpnStateRepository
) {
    operator fun invoke(): Flow<VpnStatus> = vpnStateRepository.observeStatus()
}

@Factory
class ObserveTunnelStatsUseCase(
    private val vpnStateRepository: VpnStateRepository
) {
    operator fun invoke(): Flow<TunnelStats> = vpnStateRepository.observeStats()
}

@Factory
class ObserveVpnConfigUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    operator fun invoke(): Flow<VpnConfig> = vpnConfigRepository.observeConfig()
}

@Factory
class GetVpnConfigUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(): VpnConfig = vpnConfigRepository.getConfig()
}

@Factory
class SaveVpnConfigUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(config: VpnConfig) {
        vpnConfigRepository.saveConfig(config)
    }
}

@Factory
class ConnectVpnUseCase(
    private val vpnConfigRepository: VpnConfigRepository,
    private val vpnStateRepository: VpnStateRepository
) {
    suspend operator fun invoke() {
        val config = vpnConfigRepository.getConfig()
        val stageMsg = when (config.providerType) {
            TurnProviderType.DIRECT_WEBRTC -> "Initializing direct WebRTC tunnel"
            TurnProviderType.VK_CALLS -> "Initializing VK Calls tunnel"
            TurnProviderType.STANDARD_TURN -> "Initializing TURN tunnel"
        }
        vpnStateRepository.updateStatus(VpnStatus.Connecting(stageMsg))
        vpnStateRepository.sendCommand(VpnCommand.Start(config))
    }
}

@Factory
class DisconnectVpnUseCase(
    private val vpnStateRepository: VpnStateRepository
) {
    suspend operator fun invoke() {
        vpnStateRepository.updateStatus(VpnStatus.Connecting("Disconnecting"))
        vpnStateRepository.sendCommand(VpnCommand.Stop)
        vpnStateRepository.updateStatus(VpnStatus.Disconnected)
    }
}

@Factory
class ToggleHotspotUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(enabled: Boolean, port: Int) {
        vpnConfigRepository.updateHotspot(enabled, port)
    }
}

@Factory
class ResolveTurnCredentialsUseCase(
    private val turnSignalingRepository: TurnSignalingRepository
) {
    suspend operator fun invoke(
        providerType: TurnProviderType,
        source: String,
        onProgress: ((step: Int, name: String, details: String) -> Unit)?
    ): ResolvedTurnSession {
        return turnSignalingRepository.resolve(providerType, source, onProgress)
    }
}

@Factory
class ObserveConnectionProfilesUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    operator fun invoke(): Flow<List<ConnectionProfile>> = vpnConfigRepository.observeProfiles()
}

@Factory
class SelectConnectionProfileUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(id: String) {
        vpnConfigRepository.selectProfile(id)
    }
}

@Factory
class SaveConnectionProfileUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(profile: ConnectionProfile) {
        vpnConfigRepository.saveProfile(profile)
    }
}

@Factory
class DeleteConnectionProfileUseCase(
    private val vpnConfigRepository: VpnConfigRepository
) {
    suspend operator fun invoke(id: String) {
        vpnConfigRepository.deleteProfile(id)
    }
}



