package com.whitertc.proxy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.whitertc.domain.repository.HotspotManager
import org.koin.core.annotation.Single

@Single
class DesktopHotspotManager : HotspotManager {

    override fun observeHotspotIp(): Flow<String?> = flowOf(getHotspotIp())

    override fun getHotspotIp(): String? = "127.0.0.1"

    override fun openHotspotSettings() {
        // No-op on desktop
    }
}
