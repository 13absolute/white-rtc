package com.whitertc.plugin

import com.whitertc.core.plugin.JvmPluginLoader
import org.koin.core.annotation.Single

@Single
class JvmPluginStorageManager : PluginStorageManager {
    override fun deletePlugin(providerId: String): Boolean {
        return JvmPluginLoader.deletePlugin(providerId)
    }
}
