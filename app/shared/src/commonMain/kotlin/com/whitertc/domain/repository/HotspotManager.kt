package com.whitertc.domain.repository

import kotlinx.coroutines.flow.Flow

interface HotspotManager {
    fun observeHotspotIp(): Flow<String?>
    fun getHotspotIp(): String?
    fun openHotspotSettings()
}
