package com.whitertc.domain.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

actual fun getLocalIpAddress(): String {
    return try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val targetIface = interfaces.firstOrNull {
            it.isUp && !it.isLoopback && !it.isPointToPoint
        }
        val addr = targetIface?.interfaceAddresses?.firstOrNull {
            val a = it.address
            a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress
        }?.address?.hostAddress

        addr ?: "127.0.0.1"
    } catch (_: Throwable) {
        "127.0.0.1"
    }
}
