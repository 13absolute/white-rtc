package com.whitertc.domain.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

actual fun getLocalIpAddress(): String {
    return try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val hotspotIface = interfaces.firstOrNull { iface ->
            val name = iface.name?.lowercase() ?: ""
            iface.isUp && !iface.isLoopback && (
                name.startsWith("swlan") ||
                name.startsWith("ap") ||
                name.startsWith("softap") ||
                name.startsWith("wlan1") ||
                name.startsWith("rndis") ||
                name.startsWith("p2p")
            )
        }
        val targetIface = hotspotIface ?: interfaces.firstOrNull {
            it.isUp && !it.isLoopback && !it.isPointToPoint
        }
        val addr = targetIface?.interfaceAddresses?.firstOrNull {
            val a = it.address
            a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress
        }?.address?.hostAddress

        addr ?: "192.168.43.1"
    } catch (_: Throwable) {
        "192.168.43.1"
    }
}
