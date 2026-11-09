package com.whitertc.logging

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import com.whitertc.core.logging.ConsoleLogSink
import com.whitertc.core.logging.LogLevel
import com.whitertc.core.logging.LogSink
import com.whitertc.core.logging.WhiteLogger
import com.whitertc.domain.repository.AppLogManager
import org.koin.core.annotation.Single

@Single
class DesktopAppLogManager : AppLogManager {

    private val isInitialized = AtomicBoolean(false)
    private val logDirectory by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val newDir = File(userHome, ".whitertc")
        val oldDir = File(userHome, ".whiteproxy")
        if (!newDir.exists() && oldDir.exists()) oldDir else newDir.apply { mkdirs() }
    }
    private val logFile by lazy {
        File(logDirectory, "debug_log.txt")
    }

    private val fileLock = Any()

    private val fileLogSink = LogSink { level, tag, message, throwable ->
        if (!WhiteLogger.isDebugEnabled) return@LogSink

        val timestamp = Clock.System.now().toString()
        val levelChar = when (level) {
            LogLevel.VERBOSE -> 'V'
            LogLevel.DEBUG -> 'D'
            LogLevel.INFO -> 'I'
            LogLevel.WARN -> 'W'
            LogLevel.ERROR -> 'E'
        }
        val line = "[$timestamp] [$levelChar] [$tag] $message\n" +
                (throwable?.let { "${it.stackTraceToString()}\n" } ?: "")

        synchronized(fileLock) {
            try {
                FileOutputStream(logFile, true).use { fos ->
                    fos.write(line.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Throwable) {}
        }
    }

    override fun initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            WhiteLogger.addSink(ConsoleLogSink())
            WhiteLogger.addSink(fileLogSink)
            WhiteLogger.i("DesktopAppLogManager", "DesktopAppLogManager initialized")
        }
    }

    override fun isDebugEnabled(): Boolean = WhiteLogger.isDebugEnabled

    override fun setDebugEnabled(enabled: Boolean) {
        initialize()
        WhiteLogger.isDebugEnabled = enabled
        if (enabled) {
            WhiteLogger.i("DesktopAppLogManager", "=== Debug logging enabled (${Clock.System.now()}) ===")
        }
    }

    override fun getLogFileSizeFormatted(): String {
        val sizeBytes = if (logFile.exists()) logFile.length() else 0L
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> {
                val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
                val rounded = (mb * 10).toLong() / 10.0
                "$rounded MB"
            }
        }
    }

    override fun clearLogs() {
        synchronized(fileLock) {
            try {
                if (logFile.exists()) {
                    FileOutputStream(logFile, false).use {
                        it.write("=== Logs cleared ===\n".toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    override fun shareLogs() {
        WhiteLogger.i("DesktopAppLogManager", "Debug logs located at: ${logFile.absolutePath}")
    }
}
