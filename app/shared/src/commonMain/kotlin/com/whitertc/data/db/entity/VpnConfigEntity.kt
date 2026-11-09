package com.whitertc.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.whitertc.core.protocol.WebRtcMimicFramer
import com.whitertc.domain.model.TurnProviderType

@Entity(tableName = "vpn_config")
data class VpnConfigEntity(
    @PrimaryKey
    val id: String,
    val serverHost: String,
    val serverPort: Int,
    val serverPorts: String,
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
    val bypassLan: Boolean,
    val splitTunnelingMode: String = "ALL",
    val splitTunnelingPackages: String = "",
    val debugLoggingEnabled: Boolean = false,
    val routingPreset: String = "adaptive",
    val routingMode: String = "template",
    val activeTemplateId: String = "template_example",
    val routingTemplatesJson: String = "",
    val fallbackOnAnyFailure: Boolean = false,
    val seamlessMigrationEnabled: Boolean = true,
    val customProbeTimeoutMs: Long = 1500L,
    val customProbeIntervalMs: Long = 15000L
)
