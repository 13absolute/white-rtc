package com.whitertc.vpn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BypassLanPolicyTest {

    private fun ipToLong(ip: String): Long {
        val parts = ip.split('.').map { it.toLong() }
        require(parts.size == 4)
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    private fun matchesSubnet(ip: String, subnetIp: String, prefix: Int): Boolean {
        val ipLong = ipToLong(ip)
        val subnetLong = ipToLong(subnetIp)
        val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        return (ipLong and mask) == (subnetLong and mask)
    }

    private fun isRouted(ip: String): Boolean {
        return BypassLanPolicy.PUBLIC_IPV4_SUBNETS.any { (subnet, prefix) ->
            matchesSubnet(ip, subnet, prefix)
        }
    }

    @Test
    fun publicIpsAreRouted() {
        assertTrue(isRouted("8.8.8.8"), "8.8.8.8 should be routed")
        assertTrue(isRouted("1.1.1.1"), "1.1.1.1 should be routed")
        assertTrue(isRouted("77.88.8.8"), "77.88.8.8 should be routed")
        assertTrue(isRouted("142.250.180.14"), "142.250.180.14 should be routed")
        assertTrue(isRouted("95.163.34.153"), "95.163.34.153 (VK TURN) should be routed")
    }

    @Test
    fun privateIpsAreNotRouted() {
        // Wi-Fi hotspot subnet
        assertFalse(isRouted("192.168.43.1"), "192.168.43.1 should be bypassed")
        assertFalse(isRouted("192.168.43.50"), "192.168.43.50 should be bypassed")
        assertFalse(isRouted("192.168.1.1"), "192.168.1.1 (router) should be bypassed")
        assertFalse(isRouted("192.168.0.1"), "192.168.0.1 (router) should be bypassed")

        // Class A private (10.0.0.0/8)
        assertFalse(isRouted("10.0.0.1"), "10.0.0.1 should be bypassed")
        assertFalse(isRouted("10.66.66.1"), "10.66.66.1 should be bypassed")
        assertFalse(isRouted("10.255.255.254"), "10.255.255.254 should be bypassed")

        // Class B private (172.16.0.0/12)
        assertFalse(isRouted("172.16.0.1"), "172.16.0.1 should be bypassed")
        assertFalse(isRouted("172.20.10.1"), "172.20.10.1 (hotspot) should be bypassed")
        assertFalse(isRouted("172.31.255.254"), "172.31.255.254 should be bypassed")

        // Multicast / reserved (224.0.0.0+)
        assertFalse(isRouted("224.0.0.1"), "224.0.0.1 (multicast) should be bypassed")
        assertFalse(isRouted("239.255.255.250"), "SSDP should be bypassed")
    }
}
