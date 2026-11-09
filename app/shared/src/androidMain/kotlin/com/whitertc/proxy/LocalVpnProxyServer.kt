package com.whitertc.proxy

import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.whitertc.core.logging.WhiteLogger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import com.whitertc.proxy.BidirectionalTcpRelay.closeQuietly

class LocalVpnProxyServer(
    private val vpnNetworkManager: VpnNetworkManager,
    private val hotspotIpDetector: HotspotIpDetector? = null,
    private val connectivityManager: ConnectivityManager? = null,
    private val listenPort: Int = 1080,
    private val maxConcurrentSessions: Int = 256
) {

    companion object {
        private const val TAG = "LocalVpnProxyServer"
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val IDLE_RELAY_TIMEOUT_MS = 180_000
        private const val MAX_HTTP_HEADER_BYTES = 16 * 1024

        private const val SOCKS5_VERSION: Int = 0x05
        private const val AUTH_NO_AUTH: Byte = 0x00
        private const val AUTH_NO_ACCEPTABLE: Byte = 0xFF.toByte()

        private const val CMD_CONNECT: Int = 0x01
        private const val CMD_UDP_ASSOCIATE: Int = 0x03
        private const val ATYP_IPV4: Int = 0x01
        private const val ATYP_DOMAIN: Int = 0x03
        private const val ATYP_IPV6: Int = 0x04

        private const val REP_SUCCEEDED: Byte = 0x00
        private const val REP_GENERAL_FAILURE: Byte = 0x01
        private const val REP_CONN_NOT_ALLOWED: Byte = 0x02
        private const val REP_HOST_UNREACHABLE: Byte = 0x04
        private const val REP_CMD_NOT_SUPPORTED: Byte = 0x07
        private const val REP_ADDR_NOT_SUPPORTED: Byte = 0x08
    }

    private val threadId = AtomicInteger(0)
    private var proxyDispatcher: CoroutineDispatcher? = null
    private var serverScope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val openServerSockets = Collections.synchronizedSet(mutableSetOf<ServerSocket>())

    private val sessionSemaphore = Semaphore(maxConcurrentSessions)

    @Synchronized
    fun start() {
        if (acceptJob?.isActive == true) return

        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "VpnHotspotProxy-io-${threadId.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        proxyDispatcher = dispatcher
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        serverScope = scope

        acceptJob = scope.launch {
            runEndpointWatcher()
        }
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        synchronized(openServerSockets) {
            for (s in openServerSockets) {
                s.closeQuietly()
            }
            openServerSockets.clear()
        }
        serverScope?.cancel()
        serverScope = null
        (proxyDispatcher as? java.io.Closeable)?.closeQuietly()
        proxyDispatcher = null
        WhiteLogger.i(TAG) { "LocalVpnProxyServer stopped" }
    }

    private suspend fun CoroutineScope.runEndpointWatcher() {
        if (hotspotIpDetector == null) {
            val listener = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("127.0.0.1", listenPort), 128)
                }
            } catch (e: IOException) {
                WhiteLogger.e(TAG, e) { "Failed to bind ServerSocket on 127.0.0.1:$listenPort" }
                return
            }
            openServerSockets.add(listener)
            WhiteLogger.i(TAG) { "LocalVpnProxyServer listening on 127.0.0.1:$listenPort (fallback mode)" }
            runAcceptLoop(listener, HotspotEndpoint("lo", "127.0.0.1", 8, false))
            return
        }

        val activeListeners = mutableMapOf<String, Pair<ServerSocket, Job>>()

        try {
            hotspotIpDetector.observeHotspotEndpoints().collect { endpoints ->
                val activeIps = endpoints.map { it.ipAddress }.toSet()

                // Close listeners for IPs no longer active
                val toRemove = activeListeners.keys - activeIps
                for (ip in toRemove) {
                    val (socket, job) = activeListeners.remove(ip) ?: continue
                    WhiteLogger.i(TAG) { "Hotspot interface closed: stopping proxy listener on $ip:$listenPort" }
                    job.cancel()
                    socket.closeQuietly()
                    openServerSockets.remove(socket)
                }

                // Start listeners for newly discovered hotspot IPs
                for (endpoint in endpoints) {
                    val ip = endpoint.ipAddress
                    if (ip !in activeListeners) {
                        val socket = try {
                            ServerSocket().apply {
                                reuseAddress = true
                                bind(InetSocketAddress(ip, listenPort), 128)
                            }
                        } catch (e: IOException) {
                            WhiteLogger.e(TAG, e) { "Failed to bind ServerSocket on $ip:$listenPort" }
                            continue
                        }

                        openServerSockets.add(socket)
                        WhiteLogger.i(TAG) { "Hotspot proxy listening on $ip:$listenPort (SOCKS5 + HTTP Mixed Mode)" }
                        val job = launch {
                            runAcceptLoop(socket, endpoint)
                        }
                        activeListeners[ip] = socket to job
                    }
                }

                if (activeListeners.isEmpty()) {
                    WhiteLogger.i(TAG) { "No active hotspot interfaces detected. Waiting for Mobile Hotspot to be enabled..." }
                }
            }
        } finally {
            for ((_, pair) in activeListeners) {
                pair.second.cancel()
                pair.first.closeQuietly()
                openServerSockets.remove(pair.first)
            }
            activeListeners.clear()
        }
    }

    private suspend fun CoroutineScope.runAcceptLoop(
        listener: ServerSocket,
        endpoint: HotspotEndpoint
    ) {
        try {
            while (isActive && !listener.isClosed) {
                val clientSocket = try {
                    listener.accept()
                } catch (e: IOException) {
                    if (isActive && !listener.isClosed) {
                        WhiteLogger.w(TAG) { "Accept error on proxy ServerSocket (${endpoint.ipAddress}): ${e.message}" }
                    }
                    break
                }

                // 1. Anti-Detection: Reject loopback connection attempts with immediate TCP RST
                val remoteAddress = clientSocket.inetAddress
                if (remoteAddress == null || (remoteAddress.isLoopbackAddress && endpoint.ipAddress != "127.0.0.1")) {
                    WhiteLogger.w(TAG) { "Anti-Detection: loopback probe on hotspot proxy dropped with RST" }
                    clientSocket.resetAndCloseQuietly()
                    continue
                }

                // 2. Subnet verification: ensure client is in the hotspot subnet
                if (endpoint.ipAddress != "127.0.0.1") {
                    val isAllowed = isAddressInSubnet(remoteAddress, endpoint.ipAddress, endpoint.prefixLength)
                    if (!isAllowed) {
                        WhiteLogger.w(TAG) { "Security: rejected connection from unauthorized address $remoteAddress" }
                        clientSocket.resetAndCloseQuietly()
                        continue
                    }
                }

                // 3. Anti-Spy / Anti-Detection: Check connection owner UID on Android 10+
                if (isLocalAppConnection(clientSocket)) {
                    WhiteLogger.w(TAG) { "Anti-Spy: connection from local app on device rejected with RST" }
                    clientSocket.resetAndCloseQuietly()
                    continue
                }

                // 4. Session concurrency limit
                if (!sessionSemaphore.tryAcquire()) {
                    WhiteLogger.w(TAG) { "Max concurrent sessions ($maxConcurrentSessions) reached, dropping connection" }
                    clientSocket.resetAndCloseQuietly()
                    continue
                }

                launch {
                    try {
                        handleClientConnection(clientSocket)
                    } finally {
                        clientSocket.closeQuietly()
                        sessionSemaphore.release()
                    }
                }
            }
        } finally {
            listener.closeQuietly()
            openServerSockets.remove(listener)
        }
    }

    private suspend fun handleClientConnection(clientSocket: Socket) {
        var remoteSocket: Socket? = null
        try {
            configureSocketForLowLatency(clientSocket, HANDSHAKE_TIMEOUT_MS)

            val bufferedIn = BufferedInputStream(clientSocket.getInputStream(), 4096)
            val clientOut = clientSocket.getOutputStream()

            bufferedIn.mark(4)
            val firstByte = bufferedIn.read()
            if (firstByte == -1) return

            if (firstByte == SOCKS5_VERSION) {
                remoteSocket = handleSocks5Handshake(clientSocket, bufferedIn, clientOut)
                if (remoteSocket != null) {
                    configureSocketForLowLatency(clientSocket, IDLE_RELAY_TIMEOUT_MS)
                    BidirectionalTcpRelay.relay(clientSocket, remoteSocket)
                }
            } else {
                bufferedIn.reset()
                val httpTarget = handleHttpHandshake(bufferedIn, clientOut)
                remoteSocket = httpTarget.remoteSocket
                configureSocketForLowLatency(clientSocket, IDLE_RELAY_TIMEOUT_MS)
                BidirectionalTcpRelay.relay(
                    clientSocket = clientSocket,
                    remoteSocket = remoteSocket,
                    initialRemotePayload = httpTarget.initialForwardPayload
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            WhiteLogger.d(TAG) { "Proxy session terminated: ${e.message}" }
        } finally {
            remoteSocket.closeQuietly()
            clientSocket.closeQuietly()
        }
    }

    private suspend fun handleSocks5Handshake(
        clientSocket: Socket,
        input: InputStream,
        output: OutputStream
    ): Socket? {
        val dataIn = DataInputStream(input)

        val numMethods = dataIn.readUnsignedByte()
        val methods = ByteArray(numMethods)
        dataIn.readFully(methods)

        if (!methods.contains(AUTH_NO_AUTH)) {
            output.write(byteArrayOf(SOCKS5_VERSION.toByte(), AUTH_NO_ACCEPTABLE))
            output.flush()
            throw IOException("SOCKS5 client does not support NO_AUTH (0x00)")
        }

        output.write(byteArrayOf(SOCKS5_VERSION.toByte(), AUTH_NO_AUTH))
        output.flush()

        val version = dataIn.readUnsignedByte()
        if (version != SOCKS5_VERSION) {
            throw IOException("Invalid SOCKS5 request version: $version")
        }

        val command = dataIn.readUnsignedByte()
        dataIn.readUnsignedByte() // RSV (0x00)
        val addressType = dataIn.readUnsignedByte()

        if (command == CMD_UDP_ASSOCIATE) {
            handleSocks5UdpAssociate(clientSocket, input, output, addressType, dataIn)
            return null
        }

        if (command != CMD_CONNECT) {
            sendSocks5Reply(output, REP_CMD_NOT_SUPPORTED)
            throw IOException("Unsupported SOCKS5 command: $command")
        }

        val vpnNetwork = vpnNetworkManager.requireVpnNetwork()

        val targetAddresses: List<InetAddress> = try {
            when (addressType) {
                ATYP_IPV4 -> {
                    val rawIpv4 = ByteArray(4)
                    dataIn.readFully(rawIpv4)
                    listOf(InetAddress.getByAddress(rawIpv4))
                }
                ATYP_DOMAIN -> {
                    val domainLen = dataIn.readUnsignedByte()
                    val domainBytes = ByteArray(domainLen)
                    dataIn.readFully(domainBytes)
                    val domain = String(domainBytes, StandardCharsets.UTF_8)
                    vpnNetworkManager.resolveHostThroughVpn(domain, vpnNetwork)
                }
                ATYP_IPV6 -> {
                    val rawIpv6 = ByteArray(16)
                    dataIn.readFully(rawIpv6)
                    listOf(InetAddress.getByAddress(rawIpv6))
                }
                else -> {
                    sendSocks5Reply(output, REP_ADDR_NOT_SUPPORTED)
                    throw IOException("Unsupported SOCKS5 ATYP: $addressType")
                }
            }
        } catch (e: IOException) {
            sendSocks5Reply(output, REP_HOST_UNREACHABLE)
            throw e
        }

        if (targetAddresses.any { isForbiddenTarget(it) }) {
            sendSocks5Reply(output, REP_CONN_NOT_ALLOWED)
            throw IOException("Access to loopback target is prohibited")
        }

        val targetPort = dataIn.readUnsignedShort()
        val outboundSocket = try {
            connectThroughVpnNetwork(vpnNetwork, targetAddresses, targetPort)
        } catch (e: IOException) {
            sendSocks5Reply(output, REP_GENERAL_FAILURE)
            throw e
        }

        sendSocks5Reply(output, REP_SUCCEEDED)
        return outboundSocket
    }

    private suspend fun handleSocks5UdpAssociate(
        clientSocket: Socket,
        input: InputStream,
        output: OutputStream,
        clientAddressType: Int,
        dataIn: DataInputStream
    ) {
        when (clientAddressType) {
            ATYP_IPV4 -> {
                val rawIpv4 = ByteArray(4)
                dataIn.readFully(rawIpv4)
            }
            ATYP_DOMAIN -> {
                val domainLen = dataIn.readUnsignedByte()
                val domainBytes = ByteArray(domainLen)
                dataIn.readFully(domainBytes)
            }
            ATYP_IPV6 -> {
                val rawIpv6 = ByteArray(16)
                dataIn.readFully(rawIpv6)
            }
            else -> {
                sendSocks5Reply(output, REP_ADDR_NOT_SUPPORTED)
                throw IOException("Unsupported SOCKS5 ATYP in UDP ASSOCIATE")
            }
        }
        dataIn.readUnsignedShort()

        val vpnNetwork = vpnNetworkManager.requireVpnNetwork()
        val bindAddress = clientSocket.localAddress ?: InetAddress.getByName("127.0.0.1")
        val localUdpSocket = DatagramSocket(InetSocketAddress(bindAddress, 0))
        val outboundUdpSocket = DatagramSocket(InetSocketAddress("0.0.0.0", 0))

        try {
            vpnNetworkManager.bindDatagramSocketToVpn(outboundUdpSocket, vpnNetwork)

            val localAddrBytes = clientSocket.localAddress?.address ?: byteArrayOf(0, 0, 0, 0)
            val bndAtyp = if (localAddrBytes.size == 16) ATYP_IPV6 else ATYP_IPV4
            val reply = ByteArray(4 + localAddrBytes.size + 2)
            reply[0] = SOCKS5_VERSION.toByte()
            reply[1] = REP_SUCCEEDED
            reply[2] = 0x00
            reply[3] = bndAtyp.toByte()
            System.arraycopy(localAddrBytes, 0, reply, 4, localAddrBytes.size)
            val portOffset = 4 + localAddrBytes.size
            val localPort = localUdpSocket.localPort
            reply[portOffset] = ((localPort shr 8) and 0xFF).toByte()
            reply[portOffset + 1] = (localPort and 0xFF).toByte()

            output.write(reply)
            output.flush()

            clientSocket.soTimeout = 0

            coroutineScope {
                val clientUdpAddress = AtomicReference<SocketAddress?>(null)

                val clientToRemoteJob = launch(Dispatchers.IO) {
                    val rxBuffer = ByteArray(65535)
                    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                    while (isActive && !localUdpSocket.isClosed) {
                        try {
                            localUdpSocket.receive(rxPacket)
                        } catch (_: Exception) {
                            break
                        }
                        if (rxPacket.address != clientSocket.inetAddress) continue
                        clientUdpAddress.set(rxPacket.socketAddress)
                        val length = rxPacket.length
                        if (length < 10) continue

                        val buf = rxPacket.data
                        val frag = buf[2].toInt() and 0xFF
                        if (frag != 0) continue

                        val atyp = buf[3].toInt() and 0xFF
                        var offset = 4
                        val targetAddress: InetAddress? = when (atyp) {
                            ATYP_IPV4 -> {
                                if (length < offset + 4 + 2) null
                                else {
                                    val ipBytes = ByteArray(4)
                                    System.arraycopy(buf, offset, ipBytes, 0, 4)
                                    offset += 4
                                    InetAddress.getByAddress(ipBytes)
                                }
                            }
                            ATYP_DOMAIN -> {
                                val dLen = buf[offset].toInt() and 0xFF
                                offset += 1
                                if (length < offset + dLen + 2) null
                                else {
                                    val dBytes = ByteArray(dLen)
                                    System.arraycopy(buf, offset, dBytes, 0, dLen)
                                    offset += dLen
                                    val host = String(dBytes, StandardCharsets.UTF_8)
                                    runCatching {
                                        vpnNetworkManager.resolveHostThroughVpn(host, vpnNetwork).firstOrNull()
                                    }.getOrNull()
                                }
                            }
                            ATYP_IPV6 -> {
                                if (length < offset + 16 + 2) null
                                else {
                                    val ipBytes = ByteArray(16)
                                    System.arraycopy(buf, offset, ipBytes, 0, 16)
                                    offset += 16
                                    InetAddress.getByAddress(ipBytes)
                                }
                            }
                            else -> null
                        }

                        if (targetAddress == null || isForbiddenTarget(targetAddress)) continue
                        val targetPort = ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
                        offset += 2

                        val payloadLen = length - offset
                        if (payloadLen > 0) {
                            val fwdPacket = DatagramPacket(buf, offset, payloadLen, targetAddress, targetPort)
                            try {
                                outboundUdpSocket.send(fwdPacket)
                            } catch (_: Exception) {}
                        }
                    }
                }

                val remoteToClientJob = launch(Dispatchers.IO) {
                    val rxBuffer = ByteArray(65535)
                    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                    while (isActive && !outboundUdpSocket.isClosed) {
                        try {
                            outboundUdpSocket.receive(rxPacket)
                        } catch (_: Exception) {
                            break
                        }
                        val clientAddr = clientUdpAddress.get() ?: continue
                        val srcAddr = rxPacket.address
                        val srcPort = rxPacket.port
                        val rawSrcAddr = srcAddr.address
                        val respAtyp = if (rawSrcAddr.size == 16) ATYP_IPV6 else ATYP_IPV4

                        val headerLen = 4 + rawSrcAddr.size + 2
                        val totalLen = headerLen + rxPacket.length
                        val sendBuf = ByteArray(totalLen)
                        sendBuf[0] = 0x00
                        sendBuf[1] = 0x00
                        sendBuf[2] = 0x00
                        sendBuf[3] = respAtyp.toByte()
                        System.arraycopy(rawSrcAddr, 0, sendBuf, 4, rawSrcAddr.size)
                        val pOff = 4 + rawSrcAddr.size
                        sendBuf[pOff] = ((srcPort shr 8) and 0xFF).toByte()
                        sendBuf[pOff + 1] = (srcPort and 0xFF).toByte()
                        System.arraycopy(rxPacket.data, rxPacket.offset, sendBuf, headerLen, rxPacket.length)

                        val respPacket = DatagramPacket(sendBuf, totalLen, clientAddr)
                        try {
                            localUdpSocket.send(respPacket)
                        } catch (_: Exception) {}
                    }
                }

                withContext(Dispatchers.IO) {
                    try {
                        val monitorBuf = ByteArray(64)
                        while (isActive) {
                            val r = input.read(monitorBuf)
                            if (r == -1) break
                        }
                    } catch (_: Exception) {}
                }

                clientToRemoteJob.cancel()
                remoteToClientJob.cancel()
            }
        } finally {
            localUdpSocket.closeQuietly()
            outboundUdpSocket.closeQuietly()
        }
    }

    private data class HttpTarget(val remoteSocket: Socket, val initialForwardPayload: ByteArray?)

    private suspend fun handleHttpHandshake(input: InputStream, output: OutputStream): HttpTarget {
        val headerBytes = readHttpHeader(input)
        val headerString = String(headerBytes, StandardCharsets.US_ASCII)
        val firstLine = headerString.lineSequence().firstOrNull()
            ?: throw IOException("Empty HTTP request line")

        val parts = firstLine.split(" ")
        if (parts.size < 3) throw IOException("Malformed HTTP request line: $firstLine")

        val method = parts[0].uppercase()
        val uriStr = parts[1]
        val vpnNetwork = vpnNetworkManager.requireVpnNetwork()

        return if (method == "CONNECT") {
            val hostPort = uriStr.split(":")
            val host = hostPort[0]
            val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
            val addrs = vpnNetworkManager.resolveHostThroughVpn(host, vpnNetwork)
            val remoteSocket = connectThroughVpnNetwork(vpnNetwork, addrs, port)

            val establishedResponse = "HTTP/1.1 200 Connection Established\r\n\r\n"
            output.write(establishedResponse.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            HttpTarget(remoteSocket, null)
        } else {
            val uri = URI(uriStr)
            val host = uri.host ?: throw IOException("HTTP request missing host in URI: $uriStr")
            val port = if (uri.port != -1) uri.port else 80
            val addrs = vpnNetworkManager.resolveHostThroughVpn(host, vpnNetwork)
            val remoteSocket = connectThroughVpnNetwork(vpnNetwork, addrs, port)
            HttpTarget(remoteSocket, headerBytes)
        }
    }

    private fun readHttpHeader(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        var matched = 0
        while (baos.size() < MAX_HTTP_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) throw EOFException("Unexpected EOF while reading HTTP header")
            baos.write(b)
            if (b == '\r'.code && (matched == 0 || matched == 2)) matched++
            else if (b == '\n'.code && (matched == 1 || matched == 3)) {
                matched++
                if (matched == 4) return baos.toByteArray()
            } else {
                matched = if (b == '\r'.code) 1 else 0
            }
        }
        throw IOException("HTTP header exceeded maximum allowed size of $MAX_HTTP_HEADER_BYTES bytes")
    }

    private suspend fun connectThroughVpnNetwork(
        vpnNetwork: android.net.Network,
        addresses: List<InetAddress>,
        port: Int
    ): Socket = withContext(Dispatchers.IO) {
        var lastException: IOException? = null
        for (addr in addresses) {
            val socket = Socket()
            try {
                vpnNetworkManager.bindSocketToVpn(socket, vpnNetwork)
                configureSocketForLowLatency(socket, CONNECT_TIMEOUT_MS)
                socket.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
                return@withContext socket
            } catch (e: IOException) {
                lastException = e
                socket.closeQuietly()
            }
        }
        throw lastException ?: IOException("Failed to connect to any resolved address for port $port")
    }

    private fun configureSocketForLowLatency(socket: Socket, timeoutMs: Int) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = timeoutMs
            socket.sendBufferSize = 64 * 1024
            socket.receiveBufferSize = 64 * 1024
        } catch (_: Exception) {}
    }

    private fun sendSocks5Reply(output: OutputStream, replyCode: Byte) {
        val reply = byteArrayOf(
            SOCKS5_VERSION.toByte(),
            replyCode,
            0x00,
            ATYP_IPV4.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00
        )
        try {
            output.write(reply)
            output.flush()
        } catch (_: Exception) {}
    }

    private fun isAddressInSubnet(address: InetAddress, subnetIp: String, prefixLength: Short): Boolean {
        val targetIp = address.address
        val subnet = try { InetAddress.getByName(subnetIp).address } catch (_: Exception) { return false }
        if (targetIp.size != subnet.size) return false
        val mask = -1 shl (32 - prefixLength)
        val targetInt = ByteBuffer.wrap(targetIp).int
        val subnetInt = ByteBuffer.wrap(subnet).int
        return (targetInt and mask) == (subnetInt and mask)
    }

    private fun isForbiddenTarget(addr: InetAddress): Boolean {
        return addr.isLoopbackAddress || addr.isAnyLocalAddress
    }

    private fun isLocalAppConnection(clientSocket: Socket): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        val cm = connectivityManager ?: return false
        val clientRemote = clientSocket.remoteSocketAddress as? InetSocketAddress ?: return false
        val clientLocal = clientSocket.localSocketAddress as? InetSocketAddress ?: return false

        return try {
            val ownerUid = cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_TCP,
                clientRemote,
                clientLocal
            )
            ownerUid != android.os.Process.INVALID_UID && ownerUid != android.os.Process.myUid()
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun Socket?.resetAndCloseQuietly() {
        if (this == null) return
        try {
            setSoLinger(true, 0)
        } catch (_: Exception) {}
        try {
            close()
        } catch (_: Exception) {}
    }
}
