package com.whitertc.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.Settings
import com.whitertc.core.logging.WhiteLogger
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.whitertc.core.buffer.BufferView
import com.whitertc.core.client.WhiteRtcClient
import com.whitertc.core.config.WhiteRtcConfig
import com.whitertc.core.engine.TunnelStatus
import com.whitertc.core.transport.JvmNioUdpSocketChannel
import com.whitertc.core.transport.UdpSocketChannel
import com.whitertc.core.tun.TunDevice
import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.plugin.AndroidPluginLoader
import com.whitertc.domain.model.RoutingMode
import com.whitertc.domain.model.RoutingPreset
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.VpnConfig
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.repository.VpnCommand
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.domain.repository.VpnStateRepository
import com.whitertc.proxy.AndroidUdpSocketFactory
import com.whitertc.proxy.HotspotIpDetector
import com.whitertc.app.shared.R
import com.whitertc.proxy.LocalVpnProxyServer
import com.whitertc.proxy.VpnNetworkManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android VpnService implementation.
 * Thin platform wrapper that configures VpnService.Builder, handles notifications,
 * and delegates all multi-path WebRTC/TURN tunneling logic directly to [WhiteProxyClient].
 */
class WhiteVpnService : VpnService(), KoinComponent {

    private val vpnStateRepository: VpnStateRepository by inject()
    private val vpnConfigRepository: VpnConfigRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyClient: WhiteRtcClient? = null
    private val isRunning = AtomicBoolean(false)

    private val vpnNetworkManager by lazy { VpnNetworkManager(this) }
    private val hotspotIpDetector by lazy { HotspotIpDetector(this) }
    private var proxyServer: LocalVpnProxyServer? = null
    private var currentHotspotPort: Int = -1

    private val physicalNetworks = PhysicalNetworkTracker<Network>()
    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private var wakeLock: PowerManager.WakeLock? = null
    private var physicalNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var systemStateReceiver: BroadcastReceiver? = null
    private var engineStatusJob: Job? = null
    private var engineStatsJob: Job? = null
    private var tunnelJob: Job? = null
    private var activeProfileName: String? = null

    companion object {
        private const val TAG = "WhiteVpnService"
        const val ACTION_START = "com.whitertc.vpn.ACTION_START"
        const val ACTION_STOP = "com.whitertc.vpn.ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "white_proxy_vpn_channel"
        private const val NOTIFICATION_ID = 4041

        fun start(context: Context) {
            val intent = Intent(context, WhiteVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WhiteVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidPluginLoader(this).loadAllInstalledPlugins()
        createNotificationChannel()

        serviceScope.launch {
            vpnConfigRepository.observeProfiles().collectLatest { profiles ->
                activeProfileName = profiles.firstOrNull { it.isActive }?.name
            }
        }

        serviceScope.launch {
            vpnStateRepository.observeCommands().collectLatest { command ->
                when (command) {
                    is VpnCommand.Start -> {
                        if (!isRunning.get()) {
                            tunnelJob?.cancel()
                            tunnelJob = serviceScope.launch(Dispatchers.IO) {
                                startTunnel(command.config)
                            }
                        }
                    }
                    is VpnCommand.Stop -> stopTunnel()
                }
            }
        }

        serviceScope.launch {
            vpnConfigRepository.observeConfig().collectLatest { config ->
                if (isRunning.get()) {
                    if (config.hotspotEnabled) {
                        if (proxyServer == null || currentHotspotPort != config.hotspotPort) {
                            startHotspotProxy(config.hotspotPort)
                        }
                    } else if (proxyServer != null) {
                        stopHotspotProxy()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android OS restarted the service after killing it (START_STICKY)
            startForegroundServiceCompat(getString(R.string.vpn_notification_recovering))
            if (!isRunning.get()) {
                tunnelJob?.cancel()
                tunnelJob = serviceScope.launch(Dispatchers.IO) {
                    val config = vpnConfigRepository.getConfig()
                    startTunnel(config)
                }
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                startForegroundServiceCompat(getString(R.string.vpn_notification_connecting))
                if (!isRunning.get()) {
                    tunnelJob?.cancel()
                    tunnelJob = serviceScope.launch(Dispatchers.IO) {
                        val config = vpnConfigRepository.getConfig()
                        startTunnel(config)
                    }
                }
            }
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceCompat(contentText: String) {
        val notification = createNotification(contentText = contentText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onRevoke() {
        WhiteLogger.i(TAG) { "VPN permission revoked by system (onRevoke). Shutting down tunnel..." }
        stopTunnel()
        stopSelf()
    }

    private fun configureResolverPhysicalNetwork() {
        RelayProviderRegistry.customHttpRequester = { urlStr ->
            withContext(Dispatchers.IO) {
                val url = java.net.URI(urlStr).toURL()
                val network = physicalNetworks.getActiveNetworks().firstOrNull()
                val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "WhiteRTC/1.0 (Android; SDK 34; arm64-v8a)")
                conn.setRequestProperty("Accept", "application/json")
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (conn.responseCode !in 200..299) {
                    throw com.whitertc.core.turn.RelayResolverException("HTTP error ${conn.responseCode}: $body")
                }
                body
            }
        }
    }

    private suspend fun startTunnel(config: VpnConfig) {
        if (isRunning.getAndSet(true)) {
            return
        }
        startForegroundServiceCompat("Connecting to tunnel...")
        registerPhysicalNetworkMonitoring()
        configureResolverPhysicalNetwork()

        try {
            vpnStateRepository.updateStatus(VpnStatus.Connecting("Configuring TUN interface"))

            val rawIp = config.tunnelSubnet.substringBefore("/")
            val assignedIp = if (rawIp.endsWith(".0")) {
                rawIp.substringBeforeLast(".") + ".2"
            } else if (rawIp.isNotBlank()) {
                rawIp
            } else {
                "10.8.0.2"
            }
            val prefix = config.tunnelSubnet.substringAfter("/", "24").toIntOrNull() ?: 24
            WhiteLogger.i(TAG) { "[TUN] Configuring TUN interface: assignedIp=$assignedIp/$prefix, rawSubnet=${config.tunnelSubnet}, bypassLan=${config.bypassLan}" }

            val builder = Builder()
                .setSession("WhiteRTC")
                .addAddress(assignedIp, prefix)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(config.mtu)

            if (!config.bypassLan) {
                builder.addRoute("0.0.0.0", 0)
            } else {
                BypassLanPolicy.PUBLIC_IPV4_SUBNETS.forEach { (address, prefix) ->
                    builder.addRoute(address, prefix)
                }
                builder.addRoute("10.8.0.0", 24)
            }

            when (config.splitTunnelingMode) {
                SplitTunnelingMode.ALL -> {
                    try {
                        builder.addDisallowedApplication(applicationContext.packageName)
                    } catch (e: Exception) {
                        WhiteLogger.w(TAG) { "Failed to disallow own application ${applicationContext.packageName}: ${e.message}" }
                    }
                }
                SplitTunnelingMode.EXCLUDE -> {
                    try {
                        builder.addDisallowedApplication(applicationContext.packageName)
                    } catch (e: Exception) {
                        WhiteLogger.w(TAG) { "Failed to disallow own application ${applicationContext.packageName}: ${e.message}" }
                    }
                    config.splitTunnelingPackages.forEach { pkg ->
                        if (pkg != applicationContext.packageName) {
                            try {
                                builder.addDisallowedApplication(pkg)
                            } catch (e: Exception) {
                                WhiteLogger.w(TAG) { "Failed to disallow application $pkg: ${e.message}" }
                            }
                        }
                    }
                }
                SplitTunnelingMode.INCLUDE -> {
                    val targetPackages = config.splitTunnelingPackages.filter { it != applicationContext.packageName }
                    if (targetPackages.isNotEmpty()) {
                        targetPackages.forEach { pkg ->
                            try {
                                builder.addAllowedApplication(pkg)
                            } catch (e: Exception) {
                                WhiteLogger.w(TAG) { "Failed to allow application $pkg: ${e.message}" }
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val establishTunInterface: () -> ParcelFileDescriptor? = {
                val pfd = builder.establish()
                if (pfd != null) {
                    vpnInterface = pfd
                    acquireTransientWakeLock(5000L)
                    vpnNetworkManager.startMonitoring()
                    vpnNetworkManager.notifyVpnEstablished()

                    if (config.hotspotEnabled) {
                        startHotspotProxy(config.hotspotPort)
                    }

                    updateUnderlyingNetwork(physicalNetworks.getActiveNetworks().firstOrNull())
                } else {
                    WhiteLogger.e(TAG) { "Builder.establish() returned null (VPN permission revoked or creation failed)" }
                }
                pfd
            }

            val tunDevice = AndroidTunDevice(
                establishTunFn = establishTunInterface
            )

            val socketFactory = AndroidUdpSocketFactory(
                socketProtector = { channel ->
                    val ok = protect(channel.socket())
                    if (!ok) {
                        WhiteLogger.w(TAG) { "[PROTECT] VpnService.protect returned false for channel: $channel" }
                    }
                    ok
                }
            )

            val client = WhiteRtcClient.create(
                config = config.toWhiteRtcConfig(),
                tunDevice = tunDevice,
                socketFactory = socketFactory,
                externalScope = serviceScope
            )
            proxyClient = client

            var clientStarted = false
            engineStatusJob?.cancel()
            engineStatusJob = serviceScope.launch {
                client.status.collectLatest { st ->
                    when (st) {
                        is TunnelStatus.Disconnected -> {
                            if (clientStarted) {
                                vpnStateRepository.updateStatus(VpnStatus.Disconnected)
                            }
                        }
                        is TunnelStatus.Connecting -> {
                            clientStarted = true
                            vpnStateRepository.updateStatus(VpnStatus.Connecting(st.stage))
                            updateNotification(
                                title = activeProfileName?.let { "WhiteRTC • $it" } ?: "WhiteRTC",
                                contentText = st.stage
                            )
                        }
                        is TunnelStatus.Connected -> {
                            val domainStats = TunnelStats(
                                bytesIn = st.stats.bytesIn,
                                bytesOut = st.stats.bytesOut,
                                packetsIn = st.stats.packetsIn,
                                packetsOut = st.stats.packetsOut,
                                pingMs = st.stats.pingMs,
                                activeChannels = st.stats.activeChannels,
                                totalChannels = st.stats.totalChannels,
                                downlinkBps = st.stats.downlinkBps,
                                uplinkBps = st.stats.uplinkBps
                            )
                            vpnStateRepository.updateStatus(VpnStatus.Connected(domainStats))
                            updateNotificationStats(domainStats)
                            releaseWakeLock()
                        }
                        is TunnelStatus.Error -> {
                            vpnStateRepository.updateStatus(VpnStatus.Error(st.message))
                            updateNotification(
                                title = getString(R.string.vpn_notification_error_title),
                                contentText = st.message
                            )
                            releaseWakeLock()
                        }
                    }
                }
            }

            engineStatsJob?.cancel()
            engineStatsJob = serviceScope.launch {
                var lastNotificationTime = 0L
                client.stats.collectLatest { s ->
                    val domainStats = TunnelStats(
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
                            com.whitertc.domain.model.ChannelTelemetry(
                                index = ch.index,
                                port = ch.port,
                                mediaType = when (ch.mediaType) {
                                    com.whitertc.core.engine.ChannelMediaType.AUDIO ->
                                        com.whitertc.domain.model.ChannelMediaType.AUDIO
                                    com.whitertc.core.engine.ChannelMediaType.VIDEO ->
                                        com.whitertc.domain.model.ChannelMediaType.VIDEO
                                },
                                downlinkBps = ch.downlinkBps,
                                uplinkBps = ch.uplinkBps,
                                rttMs = ch.rttMs,
                                isActive = ch.isActive,
                                lostPackets = ch.lostPackets,
                                mtu = ch.mtu
                            )
                        }
                    )
                    vpnStateRepository.updateStats(domainStats)

                    val now = System.currentTimeMillis()
                    if (now - lastNotificationTime >= 1000L) {
                        lastNotificationTime = now
                        updateNotificationStats(domainStats)
                    }
                }
            }

            val result = client.start()
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                throw err ?: IllegalStateException("Failed to start WhiteRTC client")
            }

        } catch (e: CancellationException) {
            WhiteLogger.i(TAG) { "Tunnel startup cancelled by user" }
            stopTunnel()
            throw e
        } catch (e: Exception) {
            WhiteLogger.e(TAG, e) { "Tunnel startup error" }
            vpnStateRepository.updateStatus(VpnStatus.Error(e.message ?: "Tunnel startup error"))
            stopTunnel()
        }
    }

    private fun stopTunnel() {
        val wasRunning = isRunning.getAndSet(false)

        tunnelJob?.cancel()
        tunnelJob = null

        engineStatusJob?.cancel()
        engineStatusJob = null
        engineStatsJob?.cancel()
        engineStatsJob = null

        runCatching {
            proxyClient?.close()
        }
        proxyClient = null

        unregisterPhysicalNetworkMonitoring()
        unregisterSystemStateReceiver()
        updateUnderlyingNetwork(null)
        releaseWakeLock()

        RelayProviderRegistry.customHttpRequester = null

        stopHotspotProxy()
        vpnNetworkManager.stopMonitoring()

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        if (wasRunning) {
            serviceScope.launch {
                vpnStateRepository.updateStatus(VpnStatus.Disconnected)
            }
            updateNotification(title = "WhiteRTC", contentText = getString(R.string.vpn_notification_disconnected))
        }
    }

    private fun registerPhysicalNetworkMonitoring() {
        if (physicalNetworkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handlePhysicalNetworkUpdate(network, usable = true)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val usable = isEligiblePhysicalNetwork(
                    hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    notVpn = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
                    wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
                handlePhysicalNetworkUpdate(network, usable)
            }

            override fun onLost(network: Network) {
                handlePhysicalNetworkUpdate(network, usable = false)
            }
        }

        physicalNetworkCallback = callback
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure { e ->
            WhiteLogger.w(TAG, e) { "Failed to register NetworkCallback for physical network" }
        }

        registerSystemStateReceiver()
    }

    private fun unregisterPhysicalNetworkMonitoring() {
        physicalNetworkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        physicalNetworkCallback = null
        physicalNetworks.clear()
    }

    private fun handlePhysicalNetworkUpdate(network: Network, usable: Boolean) {
        if (!isRunning.get()) return
        val transition = physicalNetworks.update(network, usable)

        when (transition) {
            PhysicalNetworkTransition.AVAILABLE -> {
                WhiteLogger.i(TAG) { "[NET] Physical network available ($network)." }
                acquireTransientWakeLock(3000L)
                updateUnderlyingNetwork(network)
                serviceScope.launch { proxyClient?.reconnect() }
            }
            PhysicalNetworkTransition.CHANGED -> {
                WhiteLogger.i(TAG) { "[NET] Physical network changed ($network). Performing seamless socket migration..." }
                acquireTransientWakeLock(3000L)
                updateUnderlyingNetwork(network)
                serviceScope.launch { proxyClient?.reconnect() }
            }
            PhysicalNetworkTransition.UNAVAILABLE -> {
                WhiteLogger.w(TAG) { "[NET] All physical networks unavailable. Waiting for network without tearing down tun0..." }
                updateUnderlyingNetwork(null)
            }
            PhysicalNetworkTransition.NONE -> Unit
        }
    }

    private fun updateUnderlyingNetwork(network: Network?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val networks = if (network != null) arrayOf(network) else null
            setUnderlyingNetworks(networks)
        }
    }

    private fun registerSystemStateReceiver() {
        if (systemStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        val isAirplane = isAirplaneModeOn()
                        WhiteLogger.i(TAG) { "[SYSTEM] Airplane mode changed: $isAirplane" }
                        if (isAirplane) {
                            updateUnderlyingNetwork(null)
                        }
                    }
                }
            }
        }
        systemStateReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        registerReceiver(receiver, filter)
    }

    private fun unregisterSystemStateReceiver() {
        systemStateReceiver?.let {
            runCatching { unregisterReceiver(it) }
        }
        systemStateReceiver = null
    }

    private fun isAirplaneModeOn(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    private fun acquireTransientWakeLock(timeoutMs: Long = 3000L) {
        runCatching {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WhiteRTC:TransientWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun startHotspotProxy(port: Int) {
        stopHotspotProxy()
        try {
            val server = LocalVpnProxyServer(
                listenPort = port,
                vpnNetworkManager = vpnNetworkManager,
                hotspotIpDetector = hotspotIpDetector,
                connectivityManager = connectivityManager
            )
            server.start()
            proxyServer = server
            currentHotspotPort = port
            WhiteLogger.i(TAG) { "Hotspot proxy server started on port $port" }
        } catch (e: Exception) {
            WhiteLogger.e(TAG, e) { "Failed to start hotspot proxy: ${e.message}" }
        }
    }

    private fun stopHotspotProxy() {
        proxyServer?.let {
            runCatching { it.stop() }
        }
        proxyServer = null
        currentHotspotPort = -1
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getNotificationSmallIcon(): Int {
        val resId = resources.getIdentifier("ic_tile_vpn", "drawable", packageName)
        return if (resId != 0) resId else android.R.drawable.ic_dialog_info
    }

    private fun formatSpeed(bps: Long): String {
        return when {
            bps <= 0L -> "0 B/s"
            bps < 1024L -> "$bps B/s"
            bps < 1024L * 1024L -> {
                val kb = bps.toDouble() / 1024.0
                "${(kb * 10).toLong() / 10.0} KB/s"
            }
            bps < 1024L * 1024L * 1024L -> {
                val mb = bps.toDouble() / (1024.0 * 1024.0)
                "${(mb * 10).toLong() / 10.0} MB/s"
            }
            else -> {
                val gb = bps.toDouble() / (1024.0 * 1024.0 * 1024.0)
                "${(gb * 100).toLong() / 100.0} GB/s"
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> {
                val kb = bytes.toDouble() / 1024.0
                "${(kb * 10).toLong() / 10.0} KB"
            }
            bytes < 1024L * 1024L * 1024L -> {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                "${(mb * 10).toLong() / 10.0} MB"
            }
            else -> {
                val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                "${(gb * 100).toLong() / 100.0} GB"
            }
        }
    }

    private fun createNotification(
        contentText: String,
        title: String? = null,
        bigText: String? = null,
        subText: String? = null
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopIntent = Intent(this, WhiteVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val effectiveTitle = title ?: activeProfileName ?: "WhiteRTC"

        builder
            .setContentTitle(effectiveTitle)
            .setContentText(contentText)
            .setSmallIcon(getNotificationSmallIcon())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        subText?.let { builder.setSubText(it) }
        bigText?.let { builder.setStyle(Notification.BigTextStyle().bigText(it)) }

        val stopActionLabel = getString(R.string.vpn_notification_action_stop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val stopIcon = runCatching {
                android.graphics.drawable.Icon.createWithResource(
                    this,
                    android.R.drawable.ic_menu_close_clear_cancel
                )
            }.getOrNull()
            builder.addAction(
                Notification.Action.Builder(
                    stopIcon,
                    stopActionLabel,
                    stopPendingIntent
                ).build()
            )
        } else {
            @Suppress("DEPRECATION")
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                stopActionLabel,
                stopPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification(
        contentText: String,
        title: String? = null,
        bigText: String? = null,
        subText: String? = null
    ) {
        if (!isRunning.get()) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(contentText, title, bigText, subText))
    }

    private fun updateNotificationStats(stats: TunnelStats) {
        val downSpeed = formatSpeed(stats.downlinkBps)
        val upSpeed = formatSpeed(stats.uplinkBps)

        updateNotification(
            contentText = "↓ $downSpeed   ↑ $upSpeed",
            title = activeProfileName ?: "WhiteRTC"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTunnel()
        unregisterPhysicalNetworkMonitoring()
        unregisterSystemStateReceiver()
        serviceScope.cancel()
    }
}

/**
 * Android ParcelFileDescriptor TunDevice adapter.
 * Uses direct POSIX read/write streams via [FileInputStream] and [FileOutputStream]
 * because Android Linux /dev/tun does not support seeking (pwrite/pread), which
 * causes FileChannel to fail with ESPIPE (Illegal seek).
 */
private class AndroidTunDevice(
    private val establishTunFn: () -> ParcelFileDescriptor?
) : TunDevice {
    override val name: String = "vpn0"

    private val isClosed = AtomicBoolean(false)
    private var pfd: ParcelFileDescriptor? = null
    private var inStream: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private val readLock = Any()
    private val writeLock = Any()
    private val readBuffer = ByteArray(65536)
    private val writeBuffer = ByteArray(65536)

    override fun onUnderlayReady(): Boolean {
        if (isClosed.get()) return false
        synchronized(this) {
            if (pfd != null) return true
            val descriptor = establishTunFn() ?: return false
            pfd = descriptor
            inStream = FileInputStream(descriptor.fileDescriptor)
            outStream = FileOutputStream(descriptor.fileDescriptor)
            return true
        }
    }

    override fun read(buffer: BufferView): Int {
        if (isClosed.get()) return -1
        return try {
            val stream = inStream ?: return -1
            val maxLen = minOf(readBuffer.size, buffer.capacity())
            val n = synchronized(readLock) {
                if (isClosed.get()) return -1
                stream.read(readBuffer, 0, maxLen)
            }
            if (n > 0) {
                buffer.putBytes(0, readBuffer, 0, n)
            }
            n
        } catch (e: IOException) {
            if (!isClosed.get()) {
                WhiteLogger.w("WhiteVpnService") { "AndroidTunDevice read error: ${e.message}" }
            }
            -1
        }
    }

    override fun writeBatch(buffers: Array<BufferView>, lengths: IntArray, count: Int): Int {
        var written = 0
        for (i in 0 until count) {
            val len = lengths[i]
            if (len > 0) {
                write(buffers[i].slice(0, len))
                written++
            }
        }
        return written
    }

    override fun write(buffer: BufferView) {
        if (isClosed.get()) return
        val len = buffer.capacity()
        if (len <= 0) return
        val stream = outStream ?: return
        synchronized(writeLock) {
            if (isClosed.get()) return
            try {
                val targetArray = if (len <= writeBuffer.size) writeBuffer else ByteArray(len)
                buffer.getBytes(0, targetArray, 0, len)
                stream.write(targetArray, 0, len)
            } catch (e: IOException) {
                if (!isClosed.get()) {
                    WhiteLogger.w("WhiteVpnService") { "AndroidTunDevice write error: ${e.message}" }
                }
            }
        }
    }

    override fun protectSocket(channel: UdpSocketChannel) {
        // No-op: sockets are protected natively upon allocation in AndroidUdpSocketFactory
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        synchronized(this) {
            runCatching { inStream?.close() }
            runCatching { outStream?.close() }
            runCatching { pfd?.close() }
            inStream = null
            outStream = null
            pfd = null
        }
    }
}

private fun VpnConfig.toWhiteRtcConfig(): WhiteRtcConfig {
    val hasRelay = (providerType == TurnProviderType.VK_CALLS || providerType == TurnProviderType.STANDARD_TURN) && signalingSource.isNotBlank()
    val effectiveMode = when (routingMode) {
        RoutingMode.DIRECT_ONLY -> WhiteRtcConfig.MODE_DIRECT
        RoutingMode.RELAY_ONLY -> if (hasRelay) WhiteRtcConfig.MODE_TURN else WhiteRtcConfig.MODE_DIRECT
        RoutingMode.BY_TEMPLATE -> if (hasRelay) WhiteRtcConfig.MODE_AUTO else WhiteRtcConfig.MODE_DIRECT
    }
    val activeTpl = activeTemplate
    return WhiteRtcConfig(
        serverHost = serverHost,
        serverPorts = serverPorts,
        sharedSecret = sharedSecret,
        providerType = if (hasRelay) providerType.id else WhiteRtcConfig.PROVIDER_DIRECT_WEBRTC,
        connectionMode = effectiveMode,
        signalingSource = signalingSource,
        parallelChannels = parallelChannels,
        tunnelSubnet = tunnelSubnet,
        mtu = mtu,
        routingPreset = routingPreset.id,
        fallbackOnAnyFailure = fallbackOnAnyFailure,
        seamlessMigrationEnabled = seamlessMigrationEnabled,
        directProbeIntervalMs = activeTpl.probeIntervalMs,
        unreachableHosts = activeTpl.unreachableHosts,
        reachableHosts = activeTpl.reachableHosts
    )
}
