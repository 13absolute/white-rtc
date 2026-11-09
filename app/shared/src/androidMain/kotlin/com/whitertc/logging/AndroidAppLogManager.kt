package com.whitertc.logging

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import com.whitertc.core.logging.LogLevel
import com.whitertc.core.logging.LogSink
import com.whitertc.core.logging.WhiteLogger
import com.whitertc.domain.repository.AppLogManager
import com.whitertc.app.shared.R
import org.koin.core.annotation.Single

@Single
class AndroidAppLogManager(
    private val context: Context
) : AppLogManager {

    private val isInitialized = AtomicBoolean(false)
    private val logDirectory by lazy {
        File(context.filesDir, "logs").apply { mkdirs() }
    }
    private val logFile by lazy {
        File(logDirectory, "debug_log.txt")
    }

    private val writeCounter = AtomicInteger(0)
    private val fileLock = Any()

    companion object {
        private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB
        private const val TRUNCATE_TARGET_BYTES = 1024 * 1024L   // 1 MB
    }

    private val logcatSink = LogSink { level, tag, message, throwable ->
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }

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
                if (writeCounter.incrementAndGet() % 100 == 0) {
                    checkAndRotateLogFile()
                }
            } catch (_: Throwable) {
                // Ignore file write errors to prevent disrupting tunnel operations
            }
        }
    }

    override fun initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            WhiteLogger.addSink(logcatSink)
            WhiteLogger.addSink(fileLogSink)
            WhiteLogger.i("AppLogManager", "AndroidAppLogManager initialized")
        }
    }

    override fun isDebugEnabled(): Boolean = WhiteLogger.isDebugEnabled

    override fun setDebugEnabled(enabled: Boolean) {
        initialize()
        WhiteLogger.isDebugEnabled = enabled
        if (enabled) {
            WhiteLogger.i("AppLogManager", "=== Debug logging enabled (${Clock.System.now()}) ===")
        }
    }

    override fun getLogFileSizeFormatted(): String {
        val sizeBytes = if (logFile.exists()) logFile.length() else 0L
        return formatBytes(sizeBytes)
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
        val appContext = context.applicationContext
        synchronized(fileLock) {
            if (!logFile.exists() || logFile.length() == 0L) {
                FileOutputStream(logFile, false).use {
                    it.write("=== WhiteRTC Debug Log (No logs recorded yet) ===\n".toByteArray(Charsets.UTF_8))
                }
            }
        }

        try {
            val authority = "${appContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(appContext, authority, logFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "WhiteRTC Debug Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.logs_share_chooser_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Throwable) {
            WhiteLogger.e("AppLogManager", "Failed to share log file", e)
        }
    }

    private fun checkAndRotateLogFile() {
        if (!logFile.exists() || logFile.length() < MAX_LOG_SIZE_BYTES) return
        try {
            RandomAccessFile(logFile, "r").use { raf ->
                val length = raf.length()
                if (length > MAX_LOG_SIZE_BYTES) {
                    val startOffset = length - TRUNCATE_TARGET_BYTES
                    raf.seek(startOffset)
                    val buffer = ByteArray(TRUNCATE_TARGET_BYTES.toInt())
                    val read = raf.read(buffer)
                    if (read > 0) {
                        FileOutputStream(logFile, false).use { fos ->
                            fos.write("... [logs truncated] ...\n".toByteArray(Charsets.UTF_8))
                            fos.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                val rounded = (mb * 10).toLong() / 10.0
                "$rounded MB"
            }
        }
    }
}
