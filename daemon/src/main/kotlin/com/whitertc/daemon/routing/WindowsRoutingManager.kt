package com.whitertc.daemon.routing

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.io.FileDescriptor
import java.net.InetAddress
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger
import com.whitertc.core.transport.JvmNioUdpSocketChannel
import com.whitertc.core.transport.UdpSocketChannel
import org.slf4j.LoggerFactory

interface Ws2_32Library : Library {
    fun setsockopt(s: Long, level: Int, optname: Int, optval: Pointer, optlen: Int): Int

    companion object {
        val INSTANCE: Ws2_32Library by lazy { Native.load("ws2_32", Ws2_32Library::class.java) }
        const val IPPROTO_IP = 0
        const val IPPROTO_IPV6 = 41
        const val IP_UNICAST_IF = 31
        const val IPV6_UNICAST_IF = 31
    }
}

interface IphlpapiLibrary : Library {
    fun GetBestInterface(destAddr: Int, bestIfIndex: IntByReference): Int

    companion object {
        val INSTANCE: IphlpapiLibrary by lazy { Native.load("iphlpapi", IphlpapiLibrary::class.java) }
    }
}

class WindowsRoutingManager(
    private val adapterName: String = "WhiteRTC",
    private val gatewayIp: String = "10.8.0.1"
) {

    private val logger = LoggerFactory.getLogger(WindowsRoutingManager::class.java)
    private val cachedPhysicalIfIndex = AtomicInteger(0)

    fun resolvePhysicalInterfaceIndex(sampleTargetIp: String = "8.8.8.8"): Int {
        val cached = cachedPhysicalIfIndex.get()
        if (cached > 0) return cached

        return try {
            val addr = InetAddress.getByName(sampleTargetIp)
            val bytes = addr.address
            val ipInt = ((bytes[3].toInt() and 0xFF) shl 24) or
                    ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    (bytes[0].toInt() and 0xFF)

            val ifIndexRef = IntByReference()
            val rc = IphlpapiLibrary.INSTANCE.GetBestInterface(ipInt, ifIndexRef)
            if (rc == 0 && ifIndexRef.value > 0) {
                val index = ifIndexRef.value
                cachedPhysicalIfIndex.set(index)
                logger.info("Discovered physical egress interface index: $index (for target $sampleTargetIp)")
                index
            } else {
                logger.warn("GetBestInterface returned error $rc. Will fall back to default routing.")
                0
            }
        } catch (e: Throwable) {
            logger.warn("Failed to resolve physical interface index: ${e.message}")
            0
        }
    }

    fun bindSocketToPhysicalInterface(channel: UdpSocketChannel, isIpv6: Boolean = false) {
        val rawChannel = channel.unwrapped
        val datagramChannel = (rawChannel as? JvmNioUdpSocketChannel)?.channel ?: return
        val socketFd = extractSocketHandle(datagramChannel)
        if (socketFd <= 0L) {
            logger.debug("Could not extract Winsock SOCKET handle from channel $channel")
            return
        }

        val ifIndex = resolvePhysicalInterfaceIndex()
        if (ifIndex <= 0) {
            logger.debug("No physical interface index available to bind socket $socketFd")
            return
        }

        try {
            val optVal = Memory(4)
            val level: Int
            val optName = Ws2_32Library.IP_UNICAST_IF

            if (!isIpv6) {
                level = Ws2_32Library.IPPROTO_IP
                // Winsock Endianness Bug: IPv4 IP_UNICAST_IF MUST be in Network Byte Order (htonl)
                optVal.setInt(0, Integer.reverseBytes(ifIndex))
            } else {
                level = Ws2_32Library.IPPROTO_IPV6
                // IPv6 IPV6_UNICAST_IF uses Host Byte Order (Little-Endian)
                optVal.setInt(0, ifIndex)
            }

            val rc = Ws2_32Library.INSTANCE.setsockopt(socketFd, level, optName, optVal, 4)
            if (rc == 0) {
                logger.debug("Successfully bound socket $socketFd to physical interface $ifIndex (isIpv6=$isIpv6)")
            } else {
                logger.warn("setsockopt UNICAST_IF failed with error code $rc for socket $socketFd")
            }
        } catch (e: Throwable) {
            logger.warn("Failed to bind socket to physical interface: ${e.message}")
        }
    }

    fun installSplitDefaultRoutes() {
        logger.info("Installing split-default routes (0.0.0.0/1 and 128.0.0.0/1) via interface '$adapterName' nexthop $gatewayIp...")
        runCommand("netsh", "interface", "ipv4", "add", "route", "0.0.0.0/1", "interface=$adapterName", "nexthop=$gatewayIp", "metric=1", "store=active")
        runCommand("netsh", "interface", "ipv4", "add", "route", "128.0.0.0/1", "interface=$adapterName", "nexthop=$gatewayIp", "metric=1", "store=active")

        // IPv6 split-default (::/1 and 8000::/1)
        runCommand("netsh", "interface", "ipv6", "add", "route", "::/1", "interface=$adapterName", "metric=1", "store=active")
        runCommand("netsh", "interface", "ipv6", "add", "route", "8000::/1", "interface=$adapterName", "metric=1", "store=active")
    }

    fun removeSplitDefaultRoutes() {
        logger.info("Removing split-default routes for interface '$adapterName'...")
        runCommand("netsh", "interface", "ipv4", "delete", "route", "0.0.0.0/1", "interface=$adapterName", "store=active")
        runCommand("netsh", "interface", "ipv4", "delete", "route", "128.0.0.0/1", "interface=$adapterName", "store=active")
        runCommand("netsh", "interface", "ipv6", "delete", "route", "::/1", "interface=$adapterName", "store=active")
        runCommand("netsh", "interface", "ipv6", "delete", "route", "8000::/1", "interface=$adapterName", "store=active")
    }

    private fun extractSocketHandle(channel: DatagramChannel): Long {
        return try {
            val fdVal = runCatching {
                val f = channel.javaClass.getDeclaredField("fdVal")
                f.isAccessible = true
                f.getLong(channel)
            }.getOrNull()
            if (fdVal != null && fdVal > 0L) return fdVal

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
                // On Windows JVM, FileDescriptor has private long handle (Windows HANDLE/SOCKET)
                val handleField = runCatching {
                    val f = FileDescriptor::class.java.getDeclaredField("handle")
                    f.isAccessible = true
                    f.getLong(fdField)
                }.getOrNull()
                if (handleField != null && handleField > 0L) return handleField

                val intFdField = runCatching {
                    val f = FileDescriptor::class.java.getDeclaredField("fd")
                    f.isAccessible = true
                    f.getInt(fdField).toLong()
                }.getOrNull()
                intFdField ?: -1L
            } else {
                -1L
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun runCommand(vararg args: String) {
        try {
            val proc = ProcessBuilder(*args).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code != 0) {
                logger.warn("Command '${args.joinToString(" ")}' exited with code $code: $out")
            }
        } catch (e: Throwable) {
            logger.warn("Failed to execute '${args.joinToString(" ")}': ${e.message}")
        }
    }
}
