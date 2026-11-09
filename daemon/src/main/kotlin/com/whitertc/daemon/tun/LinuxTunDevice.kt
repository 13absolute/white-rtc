package com.whitertc.daemon.tun

import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import com.whitertc.core.buffer.BufferView
import com.whitertc.core.protocol.WebRtcMimicFramer
import com.whitertc.core.transport.JvmNioUdpSocketChannel
import com.whitertc.core.transport.UdpSocketChannel
import com.whitertc.core.tun.TunDevice
import org.slf4j.LoggerFactory

/**
 * Linux Layer 3 TUN Device implementation using JNA to `/dev/net/tun`.
 *
 * @param name Interface name (default "tun0").
 * @param tunnelSubnet Point-to-point IP subnet (e.g. "10.8.0.2/24").
 * @param mtu Interface MTU. For Direct WebRTC mode, defaults to [WebRtcMimicFramer.RECOMMENDED_TUN_MTU]
 *            (1120) to guarantee outer UDP datagrams strictly obey the 1200B WebRTC ceiling.
 */
class LinuxTunDevice(
    override val name: String = "tun0",
    private val tunnelSubnet: String = "10.8.0.2/24",
    private val mtu: Int = WebRtcMimicFramer.RECOMMENDED_TUN_MTU
) : TunDevice {

    private val logger = LoggerFactory.getLogger(LinuxTunDevice::class.java)
    private val clib = LinuxCLibrary.INSTANCE
    private val isClosed = AtomicBoolean(false)
    private val writeLock = Any()

    val fd: Int

    init {
        // Clear any stale interface lingering from a previous unclean session
        runCatching { runCommand("ip", "link", "delete", name) }

        logger.info("Opening /dev/net/tun for interface $name...")
        val rawFd = clib.open("/dev/net/tun", LinuxCLibrary.O_RDWR or LinuxCLibrary.O_CLOEXEC)
        if (rawFd < 0) {
            val err = com.sun.jna.Native.getLastError()
            throw IllegalStateException("Failed to open /dev/net/tun (errno=$err: ${clib.strerror(err)}). Are you running with root/CAP_NET_ADMIN privileges?")
        }
        this.fd = rawFd

        try {
            val ifr = LinuxCLibrary.IfReq()
            val nameBytes = name.toByteArray(StandardCharsets.US_ASCII)
            System.arraycopy(nameBytes, 0, ifr.ifr_name, 0, minOf(nameBytes.size, 15))
            ifr.ifr_flags = (LinuxCLibrary.IFF_TUN.toInt() or LinuxCLibrary.IFF_NO_PI.toInt()).toShort()

            val rc = clib.ioctl(rawFd, LinuxCLibrary.TUNSETIFF, ifr)
            if (rc < 0) {
                val err = com.sun.jna.Native.getLastError()
                throw IllegalStateException("ioctl(TUNSETIFF) failed on $name (errno=$err: ${clib.strerror(err)})")
            }
            logger.info("Allocated Linux Layer 3 TUN interface: $name (fd=$rawFd)")

            configureNetworkAndRoutes()
        } catch (e: Throwable) {
            clib.close(rawFd)
            throw e
        }
    }

    private fun configureNetworkAndRoutes() {
        val rawIp = tunnelSubnet.substringBefore("/")
        val prefix = tunnelSubnet.substringAfter("/", "24")
        val assignedIp = if (rawIp.endsWith(".0")) {
            rawIp.substringBeforeLast(".") + ".2"
        } else {
            rawIp
        }
        val serverPeerIp = rawIp.substringBeforeLast(".") + ".1"
        logger.info("Configuring IP $assignedIp/$prefix and MTU $mtu on $name...")

        runCommand("ip", "addr", "add", "$assignedIp/$prefix", "dev", name)
        runCommand("ip", "link", "set", "dev", name, "mtu", mtu.toString(), "qlen", "4000", "up")

        val effectiveMss = (mtu - 40).coerceAtLeast(536)
        logger.info("Configuring policy routing (fwmark 0xca6c/0xffff -> table 51820 pref 101, TCP MSS $effectiveMss)...")
        // Remove stale rules from previous unclean shutdown if any
        runCatching { runCommand("ip", "rule", "del", "pref", "70") }
        runCatching { runCommand("ip", "rule", "del", "pref", "71") }
        runCatching { runCommand("ip", "rule", "del", "pref", "101") }
        runCatching { runCommand("ip", "rule", "del", "pref", "100") }
        runCatching { runCommand("ip", "rule", "del", "pref", "32764") }
        runCatching { runCommand("ip", "rule", "del", "not", "fwmark", "0x%x".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820") }
        runCatching { runCommand("ip", "rule", "del", "not", "fwmark", "0x%x/0xffff".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820") }
        for (i in 0 until 3) {
            runCatching { runCommand("ip", "rule", "del", "pref", "${90 + i}") }
        }
        runCatching { runCommand("ip", "route", "flush", "table", "51820") }

        // 0. Protect SSH: keep TCP port 22 incoming/outgoing in main routing table (crucial for headless VPS)
        runCatching { runCommand("ip", "rule", "add", "ipproto", "tcp", "sport", "22", "table", "main", "pref", "70") }
        runCatching { runCommand("ip", "rule", "add", "ipproto", "tcp", "dport", "22", "table", "main", "pref", "71") }

        // Loose reverse path filtering (rp_filter=2) required for asymmetric policy routing (table 51820)
        runCatching { runCommand("sysctl", "-w", "net.ipv4.conf.all.rp_filter=2") }
        runCatching { runCommand("sysctl", "-w", "net.ipv4.conf.default.rp_filter=2") }

        // 1. LAN Bypass: keep local LAN subnets in main routing table (RFC 1918)
        val lanSubnets = listOf("192.168.0.0/16", "10.0.0.0/8", "172.16.0.0/12")
        lanSubnets.forEachIndexed { idx, subnet ->
            runCatching { runCommand("ip", "rule", "add", "to", subnet, "table", "main", "pref", "${90 + idx}") }
        }

        // 2. Main VPN Policy Routing: route all non-VPN-daemon traffic to table 51820
        runCommand("ip", "rule", "add", "not", "fwmark", "0x%x/0xffff".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820", "pref", "101")
        runCommand("ip", "route", "add", "default", "dev", name, "table", "51820", "src", assignedIp)

        // 3. Suppress prefixlength 0: allow local device routes in main table to take precedence over default
        runCatching { runCommand("ip", "rule", "add", "from", "all", "lookup", "main", "suppress_prefixlength", "0", "pref", "32764") }

        // 4. TCP MSS Clamping to prevent PMTU black holes
        runCatching {
            runCommand("iptables", "-t", "mangle", "-D", "FORWARD", "-o", name, "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS", "--set-mss", effectiveMss.toString())
        }
        runCatching {
            runCommand("iptables", "-t", "mangle", "-A", "FORWARD", "-o", name, "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS", "--set-mss", effectiveMss.toString())
        }

        // Block IPv6 leak to prevent Happy Eyeballs bypassing VPN on local ISP
        logger.info("Configuring IPv6 leak protection (table 51820 unreachable)...")
        runCatching { runCommand("ip", "-6", "rule", "del", "pref", "101") }
        runCatching { runCommand("ip", "-6", "rule", "del", "pref", "100") }
        runCatching { runCommand("ip", "-6", "route", "flush", "table", "51820") }
        runCatching {
            runCommand("ip", "-6", "rule", "add", "not", "fwmark", "0x%x/0xffff".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820", "pref", "101")
            runCommand("ip", "-6", "route", "add", "unreachable", "default", "table", "51820")
        }

        // Configure DNS with resolvectl if available
        val hasResolvectl = runCommand("resolvectl", "status")
        if (hasResolvectl) {
            logger.info("Configuring DNS via resolvectl for $name (1.1.1.1, 8.8.8.8, domain ~., default-route true)...")
            runCatching {
                runCommand("resolvectl", "dns", name, "1.1.1.1", "8.8.8.8")
                runCommand("resolvectl", "domain", name, "~.")
                runCommand("resolvectl", "default-route", name, "true")
                runCommand("resolvectl", "flush-caches")
            }
        }
    }

    private fun teardownNetworkAndRoutes() {
        logger.info("Tearing down routing table 51820 and $name...")
        val effectiveMss = (mtu - 40).coerceAtLeast(536)
        runCatching { runCommand("resolvectl", "revert", name) }
        runCatching {
            runCommand("iptables", "-t", "mangle", "-D", "FORWARD", "-o", name, "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS", "--set-mss", effectiveMss.toString())
        }
        for (i in 0 until 3) {
            runCatching { runCommand("ip", "rule", "del", "pref", "${90 + i}") }
        }
        runCatching { runCommand("ip", "rule", "del", "pref", "70") }
        runCatching { runCommand("ip", "rule", "del", "pref", "71") }
        runCatching { runCommand("ip", "rule", "del", "pref", "101") }
        runCatching { runCommand("ip", "rule", "del", "pref", "100") }
        runCatching { runCommand("ip", "rule", "del", "pref", "32764") }
        runCatching { runCommand("ip", "rule", "del", "not", "fwmark", "0x%x/0xffff".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820") }
        runCatching { runCommand("ip", "rule", "del", "not", "fwmark", "0x%x".format(LinuxCLibrary.WHITEPROXY_FWMARK), "table", "51820") }
        runCatching { runCommand("ip", "route", "flush", "table", "51820") }
        runCatching { runCommand("ip", "-6", "rule", "del", "pref", "101") }
        runCatching { runCommand("ip", "-6", "rule", "del", "pref", "100") }
        runCatching { runCommand("ip", "-6", "route", "flush", "table", "51820") }
        runCatching { runCommand("ip", "link", "set", "dev", name, "down") }
        runCatching { runCommand("ip", "link", "delete", name) }
    }

    private fun runCommand(vararg args: String): Boolean {
        return try {
            val proc = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                logger.warn("Command ${args.joinToString(" ")} exited with code $exitCode: $output")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.warn("Failed to execute command ${args.joinToString(" ")}: ${e.message}")
            false
        }
    }

    override fun read(buffer: BufferView): Int {
        if (isClosed.get()) return -1
        return try {
            val nio = buffer.nioBuffer
            val bytesRead = clib.read(fd, nio, nio.remaining())
            if (bytesRead > 0) {
                nio.position(nio.position() + bytesRead)
                bytesRead
            } else 0
        } catch (_: Exception) {
            -1
        }
    }

    override fun write(buffer: BufferView) {
        if (isClosed.get()) return
        try {
            synchronized(writeLock) {
                val nio = buffer.nioBuffer
                val toWrite = nio.remaining()
                val written = clib.write(fd, nio, toWrite)
                if (written > 0) {
                    nio.position(nio.position() + written)
                }
            }
        } catch (e: Exception) {
            logger.trace("write to $name failed: ${e.message}")
        }
    }

    override fun protectSocket(channel: UdpSocketChannel) {
        val nio = (channel.unwrapped as? JvmNioUdpSocketChannel)?.channel ?: return
        val socketFd = extractSocketFd(nio)
        if (socketFd > 0) {
            val mark = intArrayOf(LinuxCLibrary.WHITEPROXY_FWMARK)
            val rc = clib.setsockopt(socketFd, LinuxCLibrary.SOL_SOCKET, LinuxCLibrary.SO_MARK, mark, 4)
            if (rc != 0) {
                val err = com.sun.jna.Native.getLastError()
                logger.warn("setsockopt(SO_MARK) failed for socket fd=$socketFd (errno=$err: ${clib.strerror(err)})")
            } else {
                logger.info("Marked socket fd=$socketFd with fwmark 0xca6c")
            }
        } else {
            logger.warn("Could not extract socket fd to protect DatagramChannel")
        }
    }

    private fun extractSocketFd(channel: DatagramChannel): Int {
        return try {
            val fdValField = runCatching {
                val f = channel.javaClass.getDeclaredField("fdVal")
                f.isAccessible = true
                f.getInt(channel)
            }.getOrNull()
            if (fdValField != null && fdValField > 0) return fdValField

            val fdField = runCatching {
                val f = channel.javaClass.getDeclaredField("fd")
                f.isAccessible = true
                f.get(channel) as? FileDescriptor
            }.getOrNull() ?: runCatching {
                val socket = channel.socket()
                val f = socket.javaClass.getDeclaredField("fd")
                f.isAccessible = true
                f.get(socket) as? FileDescriptor
            }.getOrNull() ?: runCatching {
                val socket = channel.socket()
                val implF = socket.javaClass.getDeclaredField("impl")
                implF.isAccessible = true
                val impl = implF.get(socket)
                val f = impl.javaClass.getDeclaredField("fd")
                f.isAccessible = true
                f.get(impl) as? FileDescriptor
            }.getOrNull()

            if (fdField != null) {
                val intFdField = FileDescriptor::class.java.getDeclaredField("fd")
                intFdField.isAccessible = true
                intFdField.getInt(fdField)
            } else {
                -1
            }
        } catch (e: Throwable) {
            -1
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            teardownNetworkAndRoutes()
            clib.close(fd)
            logger.info("Closed TUN interface $name (fd=$fd)")
        }
    }

    override fun writeBatch(buffers: Array<com.whitertc.core.buffer.BufferView>, lengths: IntArray, count: Int): Int {
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
