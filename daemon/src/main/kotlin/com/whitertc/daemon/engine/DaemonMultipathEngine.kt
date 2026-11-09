package com.whitertc.daemon.engine

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.whitertc.core.client.WhiteRtcClient
import com.whitertc.core.config.WhiteRtcConfig
import com.whitertc.core.engine.ChannelMediaType
import com.whitertc.core.engine.TunnelStatus
import com.whitertc.core.ipc.IpcChannelTelemetry
import com.whitertc.core.ipc.IpcEvent
import com.whitertc.core.ipc.IpcTurnSession
import com.whitertc.core.ipc.IpcVpnConfig
import com.whitertc.core.protocol.WebRtcMimicFramer
import com.whitertc.core.tun.TunDevice
import com.whitertc.core.turn.DefaultSignalingResolver
import com.whitertc.core.turn.ResolvedTurnSession
import com.whitertc.daemon.tun.TunDeviceFactory
import org.slf4j.LoggerFactory

/**
 * Desktop Daemon engine orchestrator.
 * Delegates all multi-path WebRTC/TURN tunneling logic directly to [WhiteRtcClient] facade from :core.
 */
class DaemonMultipathEngine {

    private val logger = LoggerFactory.getLogger(DaemonMultipathEngine::class.java)

    val daemonStartTime: Long = System.currentTimeMillis()

    private fun statusEvent(state: String, message: String = ""): IpcEvent.StatusChanged =
        IpcEvent.StatusChanged(
            state = state,
            message = message,
            daemonStartTime = daemonStartTime
        )

    private val _status = MutableStateFlow(statusEvent("DISCONNECTED", ""))
    val status: StateFlow<IpcEvent.StatusChanged> = _status.asStateFlow()

    private val _stats = MutableStateFlow(IpcEvent.StatsUpdated.empty())
    val stats: StateFlow<IpcEvent.StatsUpdated> = _stats.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = AtomicBoolean(false)
    val isTunnelRunning: Boolean get() = isRunning.get()
    private val mutex = Mutex()

    private var tunDevice: TunDevice? = null
    private var proxyClient: WhiteRtcClient? = null
    private var clientStatusJob: Job? = null
    private var clientStatsJob: Job? = null
    private var routeMonitorJob: Job? = null

    suspend fun startTunnel(
        config: IpcVpnConfig,
        sessions: List<IpcTurnSession>
    ) = mutex.withLock {
        if (isRunning.get()) {
            if (_status.value.state == "ERROR" || tunDevice == null || proxyClient == null) {
                logger.warn("Cleaning up previous stuck tunnel before restarting...")
                cleanup()
            } else {
                logger.info("Tunnel is already running.")
                return@withLock
            }
        }
        isRunning.set(true)

        try {
            var activeSessions = sessions
            val isDirectWebRtc = config.isDirectWebRtc ||
                (activeSessions.isEmpty() && config.vkToken.isBlank() && config.turnPassword.isBlank() && config.signalingSource.isBlank() && !config.connectionMode.equals(WhiteRtcConfig.MODE_TURN, ignoreCase = true))

            if (!isDirectWebRtc && activeSessions.isEmpty() && config.signalingSource.isNotBlank() && config.providerType != WhiteRtcConfig.PROVIDER_DIRECT_WEBRTC) {
                logger.info("Resolving fast-start relay session for provider '{}' via '{}'...", config.providerType, config.signalingSource)
                _status.value = statusEvent("CONNECTING", "Resolving ${config.providerType} relay credentials...")
                try {
                    val resolved = DefaultSignalingResolver.resolve(
                        providerType = config.providerType,
                        source = config.signalingSource.trim(),
                        count = 1
                    )
                    activeSessions = resolved.map { s ->
                        IpcTurnSession(
                            username = s.username,
                            password = s.password,
                            turnHost = s.primaryTurnAddress?.first ?: s.turnAddresses.firstOrNull()?.first ?: config.serverHost,
                            turnPort = s.primaryTurnAddress?.second ?: s.turnAddresses.firstOrNull()?.second ?: 3478,
                            callId = s.callId
                        )
                    }
                    logger.info("Successfully resolved fast-start relay session (1/{}).", config.parallelChannels)
                } catch (e: Exception) {
                    logger.warn("Failed upfront signaling resolution: {}", e.message)
                }
            }

            _status.value = statusEvent("CONNECTING", "Creating virtual adapter...")
            val effectiveMtu = if (isDirectWebRtc) minOf(config.mtu, WebRtcMimicFramer.RECOMMENDED_TUN_MTU) else config.mtu

            val tun = TunDeviceFactory.create(
                interfaceName = "tun0",
                tunnelSubnet = config.tunnelSubnet,
                mtu = effectiveMtu
            )
            tunDevice = tun

            val coreConfig = WhiteRtcConfig(
                serverHost = config.serverHost,
                serverPorts = config.serverPorts,
                sharedSecret = config.sharedSecret,
                providerType = if (isDirectWebRtc) {
                    WhiteRtcConfig.PROVIDER_DIRECT_WEBRTC
                } else if (config.providerType.isNotBlank() && config.providerType != WhiteRtcConfig.PROVIDER_DIRECT_WEBRTC) {
                    config.providerType
                } else {
                    WhiteRtcConfig.PROVIDER_STANDARD_TURN
                },
                signalingSource = config.signalingSource,
                parallelChannels = config.parallelChannels,
                tunnelSubnet = config.tunnelSubnet,
                mtu = effectiveMtu,
                connectionMode = config.connectionMode,
                turnHost = config.turnHost.takeIf { it.isNotBlank() },
                turnPort = config.turnPort,
                turnPassword = config.turnPassword.takeIf { it.isNotBlank() },
                routingPreset = config.routingPreset,
                fallbackOnAnyFailure = config.fallbackOnAnyFailure,
                seamlessMigrationEnabled = config.seamlessMigrationEnabled,
                directProbeIntervalMs = config.directProbeIntervalMs,
                unreachableHosts = if (config.unreachableHosts.isNotEmpty()) config.unreachableHosts else WhiteRtcConfig.DEFAULT_UNREACHABLE_HOSTS,
                reachableHosts = if (config.reachableHosts.isNotEmpty()) config.reachableHosts else WhiteRtcConfig.DEFAULT_REACHABLE_HOSTS
            )

            val coreSessions = activeSessions.map { s ->
                ResolvedTurnSession(
                    callId = s.callId,
                    username = s.username,
                    password = s.password,
                    turnUrls = emptyList(),
                    stunUrls = emptyList(),
                    primaryTurnAddress = s.turnHost to s.turnPort
                )
            }

            val client = WhiteRtcClient.create(
                config = coreConfig,
                tunDevice = tun,
                sessions = coreSessions,
                externalScope = engineScope
            )
            proxyClient = client

            clientStatusJob?.cancel()
            clientStatusJob = engineScope.launch {
                client.status.collectLatest { st ->
                    when (st) {
                        is TunnelStatus.Disconnected -> _status.value = statusEvent("DISCONNECTED", "")
                        is TunnelStatus.Connecting -> _status.value = statusEvent("CONNECTING", st.stage)
                        is TunnelStatus.Connected -> {
                            _status.value = statusEvent("CONNECTED", "Tunnel active (${st.stats.activeChannels}/${st.stats.totalChannels} channels)")
                        }
                        is TunnelStatus.Error -> _status.value = statusEvent("ERROR", st.message)
                    }
                }
            }

            clientStatsJob?.cancel()
            clientStatsJob = engineScope.launch {
                client.stats.collectLatest { s ->
                    _stats.value = IpcEvent.StatsUpdated(
                        bytesIn = s.bytesIn,
                        bytesOut = s.bytesOut,
                        packetsIn = s.packetsIn,
                        packetsOut = s.packetsOut,
                        pingMs = s.pingMs,
                        activeChannels = s.activeChannels,
                        totalChannels = s.totalChannels,
                        downlinkBps = s.downlinkBps,
                        uplinkBps = s.uplinkBps,
                        channels = s.channels.map { ch ->
                            IpcChannelTelemetry(
                                index = ch.index,
                                port = ch.port,
                                isAudio = ch.mediaType == ChannelMediaType.AUDIO,
                                downlinkBps = ch.downlinkBps,
                                uplinkBps = ch.uplinkBps,
                                rttMs = ch.rttMs,
                                isActive = ch.isActive
                            )
                        }
                    )
                }
            }

            val result = client.start()
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                throw err ?: IllegalStateException("Failed to start WhiteRtcClient")
            }
            logger.info("WhiteRtcClient successfully started on desktop daemon.")
            startRouteMonitor()

        } catch (e: Throwable) {
            logger.error("Failed to start tunnel: ${e.message}", e)
            _status.value = statusEvent("ERROR", e.message ?: "Failed to start")
            cleanup()
        }
    }

    private fun startRouteMonitor() {
        routeMonitorJob?.cancel()
        routeMonitorJob = engineScope.launch(Dispatchers.IO) {
            var process: Process? = null
            try {
                process = ProcessBuilder("ip", "monitor", "route")
                    .redirectErrorStream(true)
                    .start()

                val reader = process.inputStream.bufferedReader()
                var lastTriggerTime = 0L

                while (isActive && isRunning.get()) {
                    val line = reader.readLine() ?: break
                    val isDefaultRoute = (line.contains("default via") || line.contains("default dev") || line.contains("Deleted default")) &&
                        !line.contains("table 51820") &&
                        !line.contains("dev tun0")

                    if (isDefaultRoute) {
                        val now = System.currentTimeMillis()
                        if (now - lastTriggerTime > 400L) {
                            lastTriggerTime = now
                            logger.info("Kernel default route change detected: {}. Triggering tunnel reconnect...", line.trim())
                            runCatching {
                                ProcessBuilder("resolvectl", "flush-caches").start().waitFor()
                            }
                            engineScope.launch {
                                delay(150L)
                                if (isRunning.get()) {
                                    val reconnected = proxyClient?.reconnect() ?: false
                                    logger.info("Underlay route roaming reconnect result: {}", reconnected)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logger.warn("Route monitor encountered error: {}", e.message)
                }
            } finally {
                runCatching { process?.destroyForcibly() }
            }
        }
    }

    suspend fun startStandaloneTunnel(
        config: WhiteRtcConfig,
        sessions: List<IpcTurnSession> = emptyList()
    ) {
        startTunnel(config.toIpcVpnConfig(), sessions)
    }

    suspend fun stopTunnel() = mutex.withLock {
        try {
            cleanup()
        } finally {
            isRunning.set(false)
        }
    }

    private fun cleanup() {
        isRunning.set(false)
        runCatching { routeMonitorJob?.cancel() }
        routeMonitorJob = null
        runCatching { clientStatusJob?.cancel() }
        clientStatusJob = null
        runCatching { clientStatsJob?.cancel() }
        clientStatsJob = null

        runCatching {
            proxyClient?.close()
        }
        proxyClient = null

        runCatching { tunDevice?.close() }
        tunDevice = null

        _status.value = statusEvent("DISCONNECTED", "")
    }
}
