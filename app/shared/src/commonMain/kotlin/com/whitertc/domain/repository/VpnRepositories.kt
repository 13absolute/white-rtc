package com.whitertc.domain.repository

import kotlinx.coroutines.flow.Flow
import com.whitertc.core.turn.ResolvedTurnSession
import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnConfig
import com.whitertc.domain.model.VpnStatus

interface VpnConfigRepository {
    fun observeConfig(): Flow<VpnConfig>
    suspend fun getConfig(): VpnConfig
    suspend fun saveConfig(config: VpnConfig)
    suspend fun updateHotspot(enabled: Boolean, port: Int)

    fun observeProfiles(): Flow<List<ConnectionProfile>>
    suspend fun getProfiles(): List<ConnectionProfile>
    suspend fun selectProfile(id: String)
    suspend fun saveProfile(profile: ConnectionProfile)
    suspend fun deleteProfile(id: String)
}

sealed interface VpnCommand {
    data class Start(val config: VpnConfig) : VpnCommand
    data object Stop : VpnCommand
}

interface VpnStateRepository {
    fun observeStatus(): Flow<VpnStatus>
    suspend fun updateStatus(status: VpnStatus)
    fun observeStats(): Flow<TunnelStats>
    suspend fun updateStats(stats: TunnelStats)
    suspend fun sendCommand(command: VpnCommand)
    fun observeCommands(): Flow<VpnCommand>
}

interface TurnSignalingRepository {
    suspend fun resolve(
        providerType: TurnProviderType,
        source: String,
        onProgress: ((step: Int, name: String, details: String) -> Unit)?
    ): ResolvedTurnSession

    suspend fun resolveMultiple(
        providerType: TurnProviderType,
        source: String,
        count: Int,
        onProgress: ((resolvedCount: Int, total: Int, stage: String) -> Unit)?
    ): List<ResolvedTurnSession>
}



