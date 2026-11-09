package com.whitertc.daemon.ipc

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.whitertc.core.ipc.IpcCommand
import com.whitertc.core.ipc.IpcEvent
import com.whitertc.core.ipc.IpcJson
import com.whitertc.daemon.engine.DaemonMultipathEngine
import org.slf4j.LoggerFactory

class DaemonIpcServer(
    private val engine: DaemonMultipathEngine,
    customSocketPath: String? = null
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(DaemonIpcServer::class.java)
    private val serverScope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(false)

    val socketPath: Path = resolveSocketPath(customSocketPath)
    private var serverChannel: ServerSocketChannel? = null
    private val connectedClients = CopyOnWriteArrayList<ConnectedClient>()
    private var deadManJob: Job? = null
    private val deadManMutex = Mutex()

    companion object {
        const val DEFAULT_LINUX_PATH = "/run/whiteproxy/daemon.sock"
        const val FALLBACK_PATH = "/tmp/whiteproxy_daemon.sock"

        fun resolveSocketPath(custom: String?): Path {
            if (!custom.isNullOrBlank()) return Path.of(custom)
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("win")) {
                val programData = System.getenv("ProgramData") ?: "C:\\ProgramData"
                val winDir = File(programData, "WhiteRTC")
                if (winDir.exists() || winDir.mkdirs()) {
                    return File(winDir, "daemon.sock").toPath()
                }
                val tempDir = File(System.getProperty("java.io.tmpdir"), "WhiteRTC")
                tempDir.mkdirs()
                return File(tempDir, "daemon.sock").toPath()
            }
            val runDir = File("/run/whiteproxy")
            if (runDir.exists() || runDir.mkdirs()) {
                return Path.of(DEFAULT_LINUX_PATH)
            }
            return Path.of(FALLBACK_PATH)
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        Files.deleteIfExists(socketPath)
        socketPath.parent?.let { Files.createDirectories(it) }

        val address = UnixDomainSocketAddress.of(socketPath)
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(address)
        serverChannel = server

        // Allow unprivileged desktop clients to read/write socket
        runCatching {
            Files.setPosixFilePermissions(socketPath, PosixFilePermissions.fromString("rw-rw-rw-"))
        }

        logger.info("Daemon IPC Server listening on AF_UNIX: $socketPath")

        serverScope.launch {
            while (isRunning.get() && server.isOpen) {
                try {
                    val client = server.accept()
                    logger.info("GUI client connected from $client")
                    handleClient(client)
                } catch (e: Throwable) {
                    if (isRunning.get()) {
                        logger.error("Error accepting IPC client: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClient(channel: SocketChannel) {
        val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(channel), Charsets.UTF_8))
        val client = ConnectedClient(channel, writer)
        connectedClients.add(client)

        serverScope.launch {
            deadManMutex.withLock {
                if (deadManJob?.isActive == true) {
                    logger.info("GUI reconnected within grace period. Cancelling Dead-Man Switch.")
                    deadManJob?.cancel()
                    deadManJob = null
                }
            }
        }

        val clientJob = serverScope.launch {
            // Forward status updates to this client
            launch {
                engine.status.collectLatest { status ->
                    client.sendEvent(status)
                }
            }
            // Forward stats updates to this client
            launch {
                engine.stats.collectLatest { stats ->
                    client.sendEvent(stats)
                }
            }
            // Read incoming commands
            launch {
                try {
                    while (isActive && channel.isOpen) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        handleCommand(line, client)
                    }
                } catch (e: Throwable) {
                    logger.debug("Client read loop terminated: ${e.message}")
                } finally {
                    connectedClients.remove(client)
                    runCatching { channel.close() }
                    logger.info("GUI client disconnected (remaining clients: ${connectedClients.size})")
                    checkDeadManSwitch()
                }
            }
        }
    }

    private fun checkDeadManSwitch() {
        serverScope.launch {
            deadManMutex.withLock {
                if (connectedClients.isEmpty() && engine.isTunnelRunning) {
                    logger.warn("All GUI clients disconnected while VPN is running! Starting 1.5-second Dead-Man Switch watchdog...")
                    deadManJob?.cancel()
                    deadManJob = launch {
                        delay(1500L)
                        if (connectedClients.isEmpty() && engine.isTunnelRunning) {
                            logger.error("Dead-Man Switch expired! GUI did not reconnect within 1.5 seconds. Automatically stopping tunnel to restore host networking...")
                            engine.stopTunnel()
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleCommand(jsonLine: String, client: ConnectedClient) {
        try {
            val command = IpcJson.instance.decodeFromString<IpcCommand>(jsonLine)
            logger.info("Received IPC command: ${command::class.simpleName}")
            when (command) {
                is IpcCommand.GetStatus -> {
                    client.sendEvent(engine.status.value)
                    client.sendEvent(engine.stats.value)
                }
                is IpcCommand.Connect -> {
                    engine.startTunnel(command.config, command.sessions)
                }
                is IpcCommand.Disconnect -> {
                    try {
                        engine.stopTunnel()
                    } catch (e: Throwable) {
                        logger.error("Error executing Disconnect command: ${e.message}", e)
                    }
                    client.sendEvent(IpcEvent.StatusChanged("DISCONNECTED", "", daemonStartTime = engine.daemonStartTime))
                }
                is IpcCommand.Restart -> {
                    logger.info("Received Restart command (reason: ${command.reason}). Stopping tunnel and exiting for systemd restart...")
                    client.sendEvent(IpcEvent.StatusChanged("RESTARTING", "Daemon restarting...", daemonStartTime = engine.daemonStartTime))
                    Thread({
                        try {
                            runBlocking { engine.stopTunnel() }
                        } catch (e: Throwable) {
                            logger.warn("Error stopping tunnel during restart: ${e.message}")
                        }
                        close()
                        Thread.sleep(150L)
                        logger.info("Exiting daemon process with code 1 for systemd auto-restart...")
                        kotlin.system.exitProcess(1)
                    }, "whiteproxy-restart").start()
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to parse command '$jsonLine': ${e.message}", e)
            if (e is LinkageError || e is NoClassDefFoundError || e is ClassNotFoundException) {
                logger.error("Classloader / LinkageError detected (daemon binary was replaced on disk). Exiting for systemd restart...")
                kotlin.system.exitProcess(1)
            }
            client.sendEvent(IpcEvent.StatusChanged("ERROR", "Invalid command: ${e.message}", daemonStartTime = engine.daemonStartTime))
        }
    }

    override fun close() {
        if (isRunning.compareAndSet(true, false)) {
            connectedClients.forEach { runCatching { it.channel.close() } }
            connectedClients.clear()
            runCatching { serverChannel?.close() }
            Files.deleteIfExists(socketPath)
            logger.info("Daemon IPC Server stopped.")
        }
    }

    private class ConnectedClient(
        val channel: SocketChannel,
        val writer: BufferedWriter
    ) {
        private val lock = Any()

        fun sendEvent(event: IpcEvent) {
            synchronized(lock) {
                runCatching {
                    val json = IpcJson.instance.encodeToString(IpcEvent.serializer(), event)
                    writer.write(json)
                    writer.newLine()
                    writer.flush()
                }
            }
        }
    }
}
