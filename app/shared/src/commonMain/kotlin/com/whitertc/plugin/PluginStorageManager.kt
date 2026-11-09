package com.whitertc.plugin

/**
 * Common abstraction for deleting dynamic relay plugins across platforms.
 */
interface PluginStorageManager {
    fun deletePlugin(providerId: String): Boolean
}
