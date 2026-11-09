package com.whitertc.desktop.ipc

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.whitertc.core.ipc.IpcCommand
import com.whitertc.core.ipc.IpcEvent
import com.whitertc.core.ipc.IpcJson
import org.slf4j.LoggerFactory

class DesktopIpcClient(
    customSocketPath: String? = null
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(DesktopIpcClient::class.java)
    private val clientScope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(true)
    private val reconnectTrigger = Channel<Unit>(Channel.CONFLATED)

    private val socketPath: Path = resolveSocketPath(customSocketPath)
    private var socketChannel: SocketChannel? = null
    private var writer: BufferedWriter? = null
    private val sendMutex = Mutex()

    private val _isConnectedToDaemon = MutableStateFlow(false)
    val isConnectedToDaemon: StateFlow<Boolean> = _isConnectedToDaemon.asStateFlow()

    private val _events = MutableSharedFlow<IpcEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    companion object {
        private const val DEFAULT_LINUX_PATH = "/run/whiteproxy/daemon.sock"
        private const val FALLBACK_PATH = "/tmp/whiteproxy_daemon.sock"

        fun resolveSocketPath(custom: String?): Path {
            if (!custom.isNullOrBlank()) return Path.of(custom)
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("win")) {
                val programData = System.getenv("ProgramData") ?: "C:\\ProgramData"
                val winFile = File(File(programData, "WhiteRTC"), "daemon.sock")
                if (winFile.exists()) return winFile.toPath()
                val tempFile = File(File(System.getProperty("java.io.tmpdir"), "WhiteRTC"), "daemon.sock")
                if (tempFile.exists()) return tempFile.toPath()
                return winFile.toPath()
            }
            if (File(DEFAULT_LINUX_PATH).exists()) {
                return Path.of(DEFAULT_LINUX_PATH)
            }
            if (File(FALLBACK_PATH).exists()) {
                return Path.of(FALLBACK_PATH)
            }
            return Path.of(DEFAULT_LINUX_PATH)
        }
    }

    fun start() {
        clientScope.launch {
            while (isActive && isRunning.get()) {
                try {
                    val os = System.getProperty("os.name", "").lowercase()
                    val candidatePaths = if (os.contains("win")) {
                        val programData = System.getenv("ProgramData") ?: "C:\\ProgramData"
                        listOf(
                            socketPath,
                            File(File(programData, "WhiteRTC"), "daemon.sock").toPath(),
                            File(File(System.getProperty("java.io.tmpdir"), "WhiteRTC"), "daemon.sock").toPath()
                        ).distinct()
                    } else {
                        listOf(
                            socketPath,
                            Path.of(FALLBACK_PATH),
                            Path.of(DEFAULT_LINUX_PATH)
                        ).distinct()
                    }

                    var connected = false
                    for (path in candidatePaths) {
                        if (File(path.toString()).exists()) {
                            try {
                                val address = UnixDomainSocketAddress.of(path)
                                val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                                channel.connect(address)
                                channel.configureBlocking(true)
                                socketChannel = channel

                                val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
                                writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(channel), Charsets.UTF_8))
                                _isConnectedToDaemon.value = true
                                connected = true
                                logger.info("Connected to daemon IPC socket at $path")

                                // Request current status
                                sendCommand(IpcCommand.GetStatus)

                                // Read loop
                                while (isActive && channel.isOpen) {
                                    val line = reader.readLine() ?: break
                                    if (line.isBlank()) continue
                                    try {
                                        val event = IpcJson.instance.decodeFromString<IpcEvent>(line)
                                        _events.emit(event)
                                    } catch (e: Throwable) {
                                        logger.warn("Failed to decode IPC event '$line': ${e.message}")
                                    }
                                }
                            } catch (e: Throwable) {
                                logger.debug("Attempt to connect to $path failed: ${e.message}")
                            } finally {
                                _isConnectedToDaemon.value = false
                                runCatching { socketChannel?.close() }
                                socketChannel = null
                                writer = null
                            }
                            break
                        }
                    }

                    if (!connected) {
                        _isConnectedToDaemon.value = false
                        withTimeoutOrNull(2000) { reconnectTrigger.receive() }
                    }
                } catch (e: Throwable) {
                    _isConnectedToDaemon.value = false
                    withTimeoutOrNull(2000) { reconnectTrigger.receive() }
                }
            }
        }
    }

    fun reconnectNow() {
        reconnectTrigger.trySend(Unit)
    }

    suspend fun awaitConnection(timeoutMs: Long = 8000): Boolean {
        reconnectNow()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (_isConnectedToDaemon.value) return true
            reconnectNow()
            delay(150)
        }
        return _isConnectedToDaemon.value
    }

    suspend fun sendCommand(command: IpcCommand): Boolean = sendMutex.withLock {
        val w = writer ?: return false
        return try {
            val json = IpcJson.instance.encodeToString(IpcCommand.serializer(), command)
            w.write(json)
            w.newLine()
            w.flush()
            true
        } catch (e: Throwable) {
            logger.warn("Failed to send command to daemon: ${e.message}")
            false
        }
    }

    override fun close() {
        isRunning.set(false)
        runCatching { socketChannel?.close() }
        socketChannel = null
        writer = null
    }
}
