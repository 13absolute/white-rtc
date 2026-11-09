package com.whitertc.desktop.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.whitertc.core.config.WhiteRtcConfig
import com.whitertc.core.ipc.IpcCommand
import com.whitertc.core.ipc.IpcEvent
import com.whitertc.core.ipc.IpcTurnSession
import com.whitertc.core.ipc.IpcVpnConfig
import com.whitertc.core.turn.ResolvedTurnSession
import com.whitertc.desktop.ipc.DesktopIpcClient
import com.whitertc.domain.model.ChannelMediaType
import com.whitertc.domain.model.ChannelTelemetry
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.repository.TurnSignalingRepository
import com.whitertc.domain.repository.VpnCommand
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.domain.repository.VpnStateRepository
import org.slf4j.LoggerFactory

class DesktopVpnController(
    private val vpnStateRepository: VpnStateRepository,
    private val vpnConfigRepository: VpnConfigRepository,
    private val turnSignalingRepository: TurnSignalingRepository,
    private val ipcClient: DesktopIpcClient = DesktopIpcClient()
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(DesktopVpnController::class.java)
    private val controllerScope = CoroutineScope(Dispatchers.IO + Job())
    private var lastRestartAttemptTime: Long = 0L

    fun start() {
        logger.info("Starting DesktopVpnController...")
        ipcClient.start()

        // 1. Listen for events from daemon
        controllerScope.launch {
            ipcClient.events.collectLatest { event ->
                when (event) {
                    is IpcEvent.StatusChanged -> {
                        logger.info("Daemon status changed: ${event.state} (${event.message})")
                        checkDaemonVersionAndRestartIfNeeded(event)
                        val domainStatus = when (event.state) {
                            "DISCONNECTED" -> VpnStatus.Disconnected
                            "CONNECTING" -> VpnStatus.Connecting(event.message)
                            "CONNECTED" -> VpnStatus.Connected(TunnelStats())
                            "RESTARTING" -> VpnStatus.Connecting("Restarting WhiteRTC service...")
                            "ERROR" -> VpnStatus.Error(event.message)
                            else -> VpnStatus.Disconnected
                        }
                        vpnStateRepository.updateStatus(domainStatus)
                    }
                    is IpcEvent.StatsUpdated -> {
                        val domainStats = TunnelStats(
                            bytesIn = event.bytesIn,
                            bytesOut = event.bytesOut,
                            packetsIn = event.packetsIn,
                            packetsOut = event.packetsOut,
                            pingMs = event.pingMs,
                            activeChannels = event.activeChannels,
                            totalChannels = event.totalChannels,
                            downlinkBps = event.downlinkBps,
                            uplinkBps = event.uplinkBps,
                            channels = event.channels.map { ch ->
                                ChannelTelemetry(
                                    index = ch.index,
                                    port = ch.port,
                                    mediaType = if (ch.isAudio) ChannelMediaType.AUDIO else ChannelMediaType.VIDEO,
                                    downlinkBps = ch.downlinkBps,
                                    uplinkBps = ch.uplinkBps,
                                    rttMs = ch.rttMs,
                                    isActive = ch.isActive,
                                    lostPackets = 0,
                                    mtu = 1280
                                )
                            }
                        )
                        vpnStateRepository.updateStats(domainStats)
                    }
                }
            }
        }

        // 2. Listen for UI commands (from MainScreenViewModel)
        controllerScope.launch {
            vpnStateRepository.observeCommands().collectLatest { command ->
                when (command) {
                    is VpnCommand.Start -> handleConnect(command.config)
                    is VpnCommand.Stop -> handleDisconnect()
                }
            }
        }
    }

    private suspend fun handleConnect(requestedConfig: com.whitertc.domain.model.VpnConfig) {
        if (!ipcClient.isConnectedToDaemon.value) {
            logger.info("WhiteRTC Daemon is not running. Initiating auto-elevation...")
            vpnStateRepository.updateStatus(
                VpnStatus.Connecting("Starting WhiteRTC system service (requesting root privileges)...")
            )
            val elevated = PrivilegeElevationLauncher.elevateAndStartDaemon { stage ->
                controllerScope.launch {
                    vpnStateRepository.updateStatus(VpnStatus.Connecting(stage))
                }
            }

            if (!elevated) {
                val errMsg = "Failed to start WhiteRTC system service.\nRoot privileges are required to create network TUN interface."
                logger.warn(errMsg)
                vpnStateRepository.updateStatus(VpnStatus.Error(errMsg))
                return
            }

            vpnStateRepository.updateStatus(VpnStatus.Connecting("Connecting to WhiteRTC daemon..."))
            val connected = ipcClient.awaitConnection(timeoutMs = 10000)
            if (!connected) {
                val errMsg = "Service started, but daemon socket is not responding."
                logger.warn(errMsg)
                vpnStateRepository.updateStatus(VpnStatus.Error(errMsg))
                return
            }
        }

        val config = requestedConfig
        val hasRelay = (config.providerType == TurnProviderType.VK_CALLS || config.providerType == TurnProviderType.STANDARD_TURN) && config.signalingSource.isNotBlank()
        val effectiveMode = when (config.routingMode) {
            RoutingMode.DIRECT_ONLY -> WhiteRtcConfig.MODE_DIRECT
            RoutingMode.RELAY_ONLY -> if (hasRelay) WhiteRtcConfig.MODE_TURN else WhiteRtcConfig.MODE_DIRECT
            RoutingMode.BY_TEMPLATE -> if (hasRelay) WhiteRtcConfig.MODE_AUTO else WhiteRtcConfig.MODE_DIRECT
        }
        val isDirect = effectiveMode == WhiteRtcConfig.MODE_DIRECT || !hasRelay
        var resolvedSessions = emptyList<ResolvedTurnSession>()

        if (!isDirect && config.signalingSource.isNotBlank()) {
            vpnStateRepository.updateStatus(
                VpnStatus.Connecting("Acquiring ${config.providerType.displayName} sessions...")
            )
            try {
                resolvedSessions = turnSignalingRepository.resolveMultiple(
                    providerType = config.providerType,
                    source = config.signalingSource.trim(),
                    count = config.parallelChannels,
                    onProgress = { resolved, total, stage ->
                        controllerScope.launch {
                            vpnStateRepository.updateStatus(
                                VpnStatus.Connecting("${config.providerType.displayName}: $resolved/$total ($stage)")
                            )
                        }
                    }
                )
            } catch (e: Throwable) {
                logger.error("Failed to resolve signaling sessions: ${e.message}", e)
                vpnStateRepository.updateStatus(VpnStatus.Error("Session error: ${e.message}"))
                return
            }
            if (resolvedSessions.isEmpty() && effectiveMode == WhiteRtcConfig.MODE_TURN) {
                val errMsg = "Failed to obtain TURN sessions for Relay Only mode"
                logger.error(errMsg)
                vpnStateRepository.updateStatus(VpnStatus.Error(errMsg))
                return
            }
        }

        val ipcSessions = if (isDirect) {
            emptyList()
        } else {
            resolvedSessions.map { s ->
                IpcTurnSession(
                    username = s.username,
                    password = s.password,
                    turnHost = s.primaryTurnAddress?.first ?: config.turnHost.ifBlank { "calls.okcdn.ru" },
                    turnPort = s.primaryTurnAddress?.second ?: config.turnPort,
                    callId = s.callId
                )
            }
        }

        val activeTpl = config.activeTemplate
        val ipcConfig = IpcVpnConfig(
            serverHost = config.serverHost,
            serverPort = config.serverPort,
            serverPorts = config.serverPorts,
            sharedSecret = config.sharedSecret,
            turnHost = config.turnHost,
            turnPort = config.turnPort,
            turnPassword = if (isDirect) "" else config.turnPassword,
            vkToken = if (isDirect) "" else config.vkToken,
            callId = if (isDirect) "direct" else config.callId,
            tunnelSubnet = config.tunnelSubnet,
            mtu = config.mtu,
            parallelChannels = config.parallelChannels,
            bypassLan = config.bypassLan,
            isDirectWebRtc = isDirect,
            connectionMode = effectiveMode,
            routingMode = config.routingMode.id,
            routingPreset = config.routingPreset.id,
            providerType = if (isDirect) WhiteRtcConfig.PROVIDER_DIRECT_WEBRTC else config.providerType.id,
            fallbackOnAnyFailure = config.fallbackOnAnyFailure,
            seamlessMigrationEnabled = config.seamlessMigrationEnabled,
            directProbeIntervalMs = activeTpl.probeIntervalMs,
            unreachableHosts = activeTpl.unreachableHosts,
            reachableHosts = activeTpl.reachableHosts
        )

        logger.info("Sending Connect command to daemon (${ipcSessions.size} TURN sessions, mode=$effectiveMode, isDirectWebRtc=$isDirect)...")
        ipcClient.sendCommand(IpcCommand.Connect(ipcConfig, ipcSessions))
    }

    private suspend fun handleDisconnect() {
        logger.info("Sending Disconnect command to daemon...")
        ipcClient.sendCommand(IpcCommand.Disconnect)
    }

    private suspend fun checkDaemonVersionAndRestartIfNeeded(event: IpcEvent.StatusChanged) {
        if (event.state == "RESTARTING" || event.daemonStartTime <= 0L) return

        val binaryTimestamp = PrivilegeElevationLauncher.getDaemonBinaryTimestamp() ?: return
        if (binaryTimestamp > event.daemonStartTime + 1000L) {
            val now = System.currentTimeMillis()
            if (now - lastRestartAttemptTime > 10_000L) {
                lastRestartAttemptTime = now
                logger.info(
                    "Detected newer daemon binary on disk (binary modified: {}, daemon started: {}). Requesting daemon restart...",
                    binaryTimestamp, event.daemonStartTime
                )
                vpnStateRepository.updateStatus(
                    VpnStatus.Connecting("Updating WhiteRTC daemon to new version...")
                )
                ipcClient.sendCommand(IpcCommand.Restart(reason = "binary_updated"))
            }
        }
    }

    suspend fun disconnectAndClose() {
        try {
            logger.info("Application closing: requesting daemon to disconnect tunnel...")
            ipcClient.sendCommand(IpcCommand.Disconnect)
            delay(120L)
        } catch (e: Throwable) {
            logger.warn("Error sending Disconnect command during shutdown: ${e.message}")
        } finally {
            close()
        }
    }

    override fun close() {
        ipcClient.close()
    }
}
