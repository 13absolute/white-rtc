package com.whitertc.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.whitertc.domain.model.TurnProviderType

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val providerType: TurnProviderType,
    val serverHost: String,
    val serverPort: Int,
    val serverPorts: String,
    val sharedSecret: String,
    val signalingSource: String,
    val isActive: Boolean,
    val lastPingMs: Long,
    val tunnelSubnet: String = "10.8.0.2/24",
    val parallelChannels: Int = 8
)
