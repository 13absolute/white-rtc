package com.whitertc.daemon.tun

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import com.whitertc.core.buffer.BufferView
import com.whitertc.core.protocol.WebRtcMimicFramer
import com.whitertc.core.transport.UdpSocketChannel
import com.whitertc.core.tun.TunDevice
import com.whitertc.daemon.dns.WindowsDnsManager
import com.whitertc.daemon.routing.WindowsRoutingManager
import org.slf4j.LoggerFactory

interface WintunLibrary : Library {
    companion object {
        fun load(): WintunLibrary {
            val possibleLocations = listOf(
                "wintun.dll",
                "bin/wintun.dll",
                "C:\\Program Files\\WhiteRTC\\wintun.dll",
                "C:\\Program Files\\WhiteProxy\\wintun.dll"
            )
            for (loc in possibleLocations) {
                if (File(loc).exists()) {
                    return Native.load(loc, WintunLibrary::class.java)
                }
            }

            // Extract from bundled JAR resources if present
            val arch = System.getProperty("os.arch", "").lowercase()
            val resourcePath = if (arch.contains("arm") || arch.contains("aarch64")) {
                "/win32-aarch64/wintun.dll"
            } else {
                "/win32-x86-64/wintun.dll"
            }

            val extracted = extractResourceToTemp(resourcePath)
            if (extracted != null && extracted.exists()) {
                return Native.load(extracted.absolutePath, WintunLibrary::class.java)
            }

            return Native.load("wintun", WintunLibrary::class.java)
        }

        private fun extractResourceToTemp(resourcePath: String): File? {
            return try {
                val stream = WintunLibrary::class.java.getResourceAsStream(resourcePath) ?: return null
                val tempDir = File(System.getProperty("java.io.tmpdir"), "WhiteRTC")
                tempDir.mkdirs()
                val targetFile = File(tempDir, "wintun.dll")
                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile
            } catch (_: Throwable) {
                null
            }
        }

        const val WINTUN_RING_CAPACITY = 0x400000 // 4 MiB
    }

    fun WintunCreateAdapter(name: WString, tunnelType: WString, requestedGuid: Pointer?): Pointer?
    fun WintunCloseAdapter(adapter: Pointer)
    fun WintunStartSession(adapter: Pointer, capacity: Int): Pointer?
    fun WintunEndSession(session: Pointer)
    fun WintunGetReadWaitEvent(session: Pointer): Pointer?
    fun WintunReceivePacket(session: Pointer, packetSize: IntByReference): Pointer?
    fun WintunReleaseReceivePacket(session: Pointer, packet: Pointer)
    fun WintunAllocateSendPacket(session: Pointer, packetSize: Int): Pointer?
    fun WintunSendPacket(session: Pointer, packet: Pointer)
}

class WindowsTunDevice(
    override val name: String = "WhiteRTC",
    private val tunnelSubnet: String = "10.8.0.2/24",
    private val mtu: Int = WebRtcMimicFramer.RECOMMENDED_TUN_MTU
) : TunDevice {

    private val logger = LoggerFactory.getLogger(WindowsTunDevice::class.java)
    private val wintun: WintunLibrary by lazy { WintunLibrary.load() }
    private val isClosed = AtomicBoolean(false)

    private val dnsManager = WindowsDnsManager()
    private val routingManager = WindowsRoutingManager(adapterName = name, gatewayIp = "10.8.0.1")

    private var adapterHandle: Pointer? = null
    private var sessionHandle: Pointer? = null

    init {
        logger.info("Initializing Wintun adapter '$name'...")
        val adapter = wintun.WintunCreateAdapter(
            WString(name),
            WString("WhiteRTC Tunnel"),
            null
        ) ?: throw IllegalStateException("WintunCreateAdapter failed. Ensure wintun.dll is available and process is running as Administrator.")
        adapterHandle = adapter

        val session = wintun.WintunStartSession(adapter, WintunLibrary.WINTUN_RING_CAPACITY)
            ?: run {
                wintun.WintunCloseAdapter(adapter)
                throw IllegalStateException("WintunStartSession failed.")
            }
        sessionHandle = session

        configureWindowsNetwork()
        dnsManager.configureSecureDns()
        routingManager.installSplitDefaultRoutes()
    }

    private fun configureWindowsNetwork() {
        val assignedIp = tunnelSubnet.substringBefore("/")
        val mask = "255.255.255.0"
        logger.info("Configuring Windows IP $assignedIp mask $mask on adapter '$name'...")

        runCommand("netsh", "interface", "ip", "set", "address", "name=$name", "source=static", "addr=$assignedIp", "mask=$mask")
        runCommand("netsh", "interface", "ipv4", "set", "subinterface", name, "mtu=$mtu", "store=active")
        runCommand("netsh", "interface", "ip", "set", "dns", "name=$name", "source=static", "addr=1.1.1.1")
        runCommand("netsh", "interface", "ip", "add", "dns", "name=$name", "addr=8.8.8.8", "index=2")
    }

    private fun runCommand(vararg args: String) {
        try {
            val proc = ProcessBuilder(*args).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code != 0) {
                logger.warn("Windows command '${args.joinToString(" ")}' exited with code $code: $out")
            }
        } catch (e: Throwable) {
            logger.warn("Failed to execute '${args.joinToString(" ")}': ${e.message}")
        }
    }

    override fun read(buffer: BufferView): Int {
        val session = sessionHandle ?: return -1
        if (isClosed.get()) return -1

        val sizeRef = IntByReference()
        val packetPtr = wintun.WintunReceivePacket(session, sizeRef) ?: return 0
        val size = sizeRef.value
        try {
            val toCopy = minOf(size, buffer.capacity())
            val temp = ByteArray(toCopy)
            packetPtr.read(0, temp, 0, toCopy)
            buffer.putBytes(0, temp, 0, toCopy)
            return toCopy
        } finally {
            wintun.WintunReleaseReceivePacket(session, packetPtr)
        }
    }

    override fun write(buffer: BufferView) {
        val session = sessionHandle ?: return
        if (isClosed.get()) return

        val length = buffer.capacity()
        val sendPtr = wintun.WintunAllocateSendPacket(session, length)
        if (sendPtr != null) {
            val temp = buffer.toByteArray()
            sendPtr.write(0, temp, 0, length)
            wintun.WintunSendPacket(session, sendPtr)
        }
    }

    override fun protectSocket(channel: UdpSocketChannel) {
        routingManager.bindSocketToPhysicalInterface(channel)
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            routingManager.removeSplitDefaultRoutes()
            dnsManager.removeSecureDns()

            sessionHandle?.let {
                wintun.WintunEndSession(it)
                sessionHandle = null
            }
            adapterHandle?.let {
                wintun.WintunCloseAdapter(it)
                adapterHandle = null
            }
            logger.info("Closed Windows Wintun adapter '$name' and cleanly restored routing and DNS.")
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
}
