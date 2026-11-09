package com.whitertc.domain.repository

/**
 * Manages runtime logging configuration, file sinks, and log export/sharing.
 */
interface AppLogManager {
    fun initialize()
    fun isDebugEnabled(): Boolean
    fun setDebugEnabled(enabled: Boolean)
    fun getLogFileSizeFormatted(): String
    fun clearLogs()
    fun shareLogs()
}
