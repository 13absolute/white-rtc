package com.whitertc.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.whitertc.core.protocol.WebRtcMimicFramer
import com.whitertc.data.db.dao.ConnectionProfileDao
import com.whitertc.data.db.dao.VpnConfigDao
import com.whitertc.data.db.entity.ConnectionProfileEntity
import com.whitertc.data.db.entity.VpnConfigEntity
import com.whitertc.domain.model.ConnectionProfile
import com.whitertc.domain.model.DEFAULT_EXAMPLE_TEMPLATE
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingPreset
import com.whitertc.domain.model.RoutingTemplate
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnConfig
import com.whitertc.domain.repository.VpnConfigRepository
import org.koin.core.annotation.Single

@Single
class VpnConfigRepositoryImpl(
    private val vpnConfigDao: VpnConfigDao,
    private val connectionProfileDao: ConnectionProfileDao
) : VpnConfigRepository {

    override fun observeConfig(): Flow<VpnConfig> {
        return vpnConfigDao.observeById("default")
            .onStart { ensureActiveProfile() }
            .map { entity ->
                entity?.toDomain() ?: VpnConfig.initial()
            }
    }

    override suspend fun getConfig(): VpnConfig {
        ensureActiveProfile()
        return vpnConfigDao.getById("default")?.toDomain() ?: VpnConfig.initial()
    }

    override suspend fun saveConfig(config: VpnConfig) {
        vpnConfigDao.insertOrUpdate(config.toEntity())
    }

    override suspend fun updateHotspot(enabled: Boolean, port: Int) {
        val current = getConfig()
        saveConfig(current.copy(hotspotEnabled = enabled, hotspotPort = port))
    }

    override fun observeProfiles(): Flow<List<ConnectionProfile>> {
        return connectionProfileDao.observeAll()
            .onStart { ensureActiveProfile() }
            .map { list ->
                list.map { it.toDomain() }
            }
    }

    override suspend fun getProfiles(): List<ConnectionProfile> {
        ensureActiveProfile()
        return connectionProfileDao.getAll().map { it.toDomain() }
    }

    override suspend fun selectProfile(id: String) {
        connectionProfileDao.setActiveProfile(id)
        val selected = connectionProfileDao.getById(id) ?: return
        val current = vpnConfigDao.getById("default")?.toDomain() ?: VpnConfig.initial()
        val ports = selected.serverPorts.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..65535 }
        val updated = current.copy(
            serverHost = selected.serverHost,
            serverPort = selected.serverPort,
            providerType = selected.providerType,
            signalingSource = selected.signalingSource,
            sharedSecret = selected.sharedSecret,
            serverPorts = if (ports.isNotEmpty()) ports else current.serverPorts,
            tunnelSubnet = if (selected.tunnelSubnet.isNotBlank()) selected.tunnelSubnet else current.tunnelSubnet,
            parallelChannels = selected.parallelChannels
        )
        saveConfig(updated)
    }

    override suspend fun saveProfile(profile: ConnectionProfile) {
        val active = connectionProfileDao.getActive()
        val shouldActivate = profile.isActive || active == null
        val toSave = if (shouldActivate) profile.copy(isActive = true) else profile
        connectionProfileDao.insertOrUpdate(toSave.toEntity())
        if (shouldActivate) {
            selectProfile(toSave.id)
        }
    }

    override suspend fun deleteProfile(id: String) {
        connectionProfileDao.deleteById(id)
        val remaining = connectionProfileDao.getAll()
        val active = connectionProfileDao.getActive()
        if (active == null) {
            if (remaining.isNotEmpty()) {
                selectProfile(remaining.first().id)
            } else {
                val current = vpnConfigDao.getById("default")?.toDomain() ?: VpnConfig.initial()
                saveConfig(
                    current.copy(
                        serverHost = "",
                        serverPorts = emptyList(),
                        signalingSource = ""
                    )
                )
            }
        }
    }

    private suspend fun ensureActiveProfile() {
        seedDefaultProfilesIfEmpty()
        val all = connectionProfileDao.getAll()
        if (all.isNotEmpty()) {
            val active = connectionProfileDao.getActive()
            if (active == null) {
                selectProfile(all.first().id)
            } else {
                val currentConfig = vpnConfigDao.getById("default")
                if (currentConfig == null || currentConfig.serverHost.isBlank()) {
                    selectProfile(active.id)
                }
            }
        }
    }

    private suspend fun seedDefaultProfilesIfEmpty() {
        // Clean initial state: no hardcoded server IPs
    }

    private fun ConnectionProfileEntity.toDomain(): ConnectionProfile {
        val ports = serverPorts.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..65535 }
        return ConnectionProfile(
            id = id,
            name = name,
            providerType = providerType,
            serverHost = serverHost,
            serverPort = serverPort,
            serverPorts = ports,
            sharedSecret = sharedSecret,
            signalingSource = signalingSource,
            isActive = isActive,
            pingMs = lastPingMs,
            tunnelSubnet = tunnelSubnet,
            parallelChannels = parallelChannels
        )
    }

    private fun ConnectionProfile.toEntity(): ConnectionProfileEntity {
        return ConnectionProfileEntity(
            id = id,
            name = name,
            providerType = providerType,
            serverHost = serverHost,
            serverPort = serverPort,
            serverPorts = serverPorts.joinToString(","),
            sharedSecret = sharedSecret,
            signalingSource = signalingSource,
            isActive = isActive,
            lastPingMs = pingMs,
            tunnelSubnet = tunnelSubnet,
            parallelChannels = parallelChannels
        )
    }

    private fun VpnConfigEntity.toDomain(): VpnConfig {
        val effectiveMtu = if (providerType == TurnProviderType.DIRECT_WEBRTC) minOf(mtu, WebRtcMimicFramer.RECOMMENDED_TUN_MTU) else mtu
        val effectiveChannels = if (providerType == TurnProviderType.DIRECT_WEBRTC) minOf(parallelChannels, 8) else parallelChannels
        val ports = serverPorts.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..65535 }
        val mode = runCatching { SplitTunnelingMode.valueOf(splitTunnelingMode) }.getOrDefault(SplitTunnelingMode.ALL)
        val packages = splitTunnelingPackages.split(',').filter { it.isNotBlank() }.toSet()
        val templates: List<RoutingTemplate> = if (routingTemplatesJson.isNotBlank()) {
            runCatching {
                Json.decodeFromString<List<RoutingTemplate>>(routingTemplatesJson)
            }.getOrDefault(listOf(DEFAULT_EXAMPLE_TEMPLATE))
        } else {
            listOf(DEFAULT_EXAMPLE_TEMPLATE)
        }
        return VpnConfig(
            serverHost = serverHost,
            serverPort = serverPort,
            serverPorts = ports,
            sharedSecret = sharedSecret,
            providerType = providerType,
            signalingSource = signalingSource,
            vkToken = vkToken,
            callId = callId,
            turnHost = turnHost,
            turnPort = turnPort,
            turnPassword = turnPassword,
            tunnelSubnet = tunnelSubnet,
            mtu = effectiveMtu,
            hotspotEnabled = hotspotEnabled,
            hotspotPort = hotspotPort,
            parallelChannels = effectiveChannels,
            bypassLan = bypassLan,
            splitTunnelingMode = mode,
            splitTunnelingPackages = packages,
            debugLoggingEnabled = debugLoggingEnabled,
            routingPreset = RoutingPreset.fromId(routingPreset),
            routingMode = RoutingMode.fromId(routingMode),
            activeTemplateId = if (activeTemplateId.isNotBlank()) activeTemplateId else "template_example",
            routingTemplates = if (templates.isNotEmpty()) templates else listOf(DEFAULT_EXAMPLE_TEMPLATE),
            fallbackOnAnyFailure = fallbackOnAnyFailure,
            seamlessMigrationEnabled = seamlessMigrationEnabled,
            customProbeTimeoutMs = customProbeTimeoutMs,
            customProbeIntervalMs = customProbeIntervalMs
        )
    }

    private fun VpnConfig.toEntity(): VpnConfigEntity = VpnConfigEntity(
        id = "default",
        serverHost = serverHost,
        serverPort = serverPort,
        serverPorts = serverPorts.joinToString(","),
        sharedSecret = sharedSecret,
        providerType = providerType,
        signalingSource = signalingSource,
        vkToken = vkToken,
        callId = callId,
        turnHost = turnHost,
        turnPort = turnPort,
        turnPassword = turnPassword,
        tunnelSubnet = tunnelSubnet,
        mtu = mtu,
        hotspotEnabled = hotspotEnabled,
        hotspotPort = hotspotPort,
        parallelChannels = parallelChannels,
        bypassLan = bypassLan,
        splitTunnelingMode = splitTunnelingMode.name,
        splitTunnelingPackages = splitTunnelingPackages.joinToString(","),
        debugLoggingEnabled = debugLoggingEnabled,
        routingPreset = routingPreset.id,
        routingMode = routingMode.id,
        activeTemplateId = activeTemplateId,
        routingTemplatesJson = runCatching { Json.encodeToString(routingTemplates) }.getOrDefault(""),
        fallbackOnAnyFailure = fallbackOnAnyFailure,
        seamlessMigrationEnabled = seamlessMigrationEnabled,
        customProbeTimeoutMs = customProbeTimeoutMs,
        customProbeIntervalMs = customProbeIntervalMs
    )
}

