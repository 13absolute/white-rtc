package com.whitertc.plugin

import android.content.Context
import org.koin.core.annotation.Single

@Single
class AndroidPluginStorageManager(
    private val context: Context
) : PluginStorageManager {
    override fun deletePlugin(providerId: String): Boolean {
        return AndroidPluginLoader(context).deletePlugin(providerId)
    }
}
