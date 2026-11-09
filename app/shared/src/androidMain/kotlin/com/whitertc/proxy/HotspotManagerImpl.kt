package com.whitertc.proxy

import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.whitertc.domain.repository.HotspotManager
import org.koin.core.annotation.Single

@Single
class HotspotManagerImpl(
    private val context: Context
) : HotspotManager {

    private val detector by lazy { HotspotIpDetector(context) }

    override fun observeHotspotIp(): Flow<String?> {
        return detector.observeHotspotEndpoints().map { endpoints ->
            endpoints.firstOrNull()?.ipAddress
        }
    }

    override fun getHotspotIp(): String? {
        return detector.detectHotspotEndpoints().firstOrNull()?.ipAddress
    }

    override fun openHotspotSettings() {
        val appContext = context.applicationContext
        runCatching {
            appContext.startActivity(
                Intent("android.settings.TETHER_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            runCatching {
                appContext.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }
}
