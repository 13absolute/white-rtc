package com.whitertc.daemon.tun

import java.util.Locale
import com.whitertc.core.protocol.WebRtcMimicFramer

/**
 * Factory for creating platform-specific virtual L3 TUN network interfaces (Linux `/dev/net/tun` and Windows Wintun).
 *
 * Mathematical MTU calculation for the virtual interface:
 *  - Legitimate WebRTC UDP datagram ceiling (Chromium / libwebrtc kDefaultMaxPacketSize) = 1200 bytes.
 *  - Tunnel encapsulation overhead = 75 bytes:
 *      * Cleartext SRTP VP8 Header: 31B (12B RTP + 12B RFC 8285 0xBEDE + 7B VP8 Payload Descriptor)
 *      * Noise Session Framing:     12B (4B SessionIndex + 8B Nonce Counter)
 *      * TurnMux Multipath Header:  16B (Header Type, PathId, Timestamp, Monotonic Seq, FlowId)
 *      * Poly1305 AEAD Tag:         16B (AAD + ciphertext authentication)
 *  - Safe MTU for TUN interface = 1200 - 75 = 1125 bytes.
 *  - The value 1120 bytes ([WebRtcMimicFramer.RECOMMENDED_TUN_MTU]) was selected due to:
 *      1. 16-byte alignment (70 * 16B) for optimal ChaCha20/Poly1305 SIMD/block encryption.
 *      2. Ideal TCP MSS = 1080 bytes (1120 - 20B IPv4 - 20B TCP).
 *      3. Guaranteed 0% IP fragmentation: outer wire size IPv4 = 1223B, IPv6 = 1243B (strictly below IPv6 minimum
 *         1280B and cellular GTP-U tunnel limits of 1360-1420B).
 */
object TunDeviceFactory {
    fun create(
        interfaceName: String = "tun0",
        tunnelSubnet: String = "10.8.0.2/24",
        mtu: Int = WebRtcMimicFramer.RECOMMENDED_TUN_MTU
    ): TunDevice {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return when {
            os.contains("linux") -> LinuxTunDevice(
                name = interfaceName,
                tunnelSubnet = tunnelSubnet,
                mtu = mtu
            )
            os.contains("win") -> WindowsTunDevice(
                name = if (interfaceName == "tun0") "WhiteRTC" else interfaceName,
                tunnelSubnet = tunnelSubnet,
                mtu = mtu
            )
            else -> throw UnsupportedOperationException("Unsupported OS: $os. Only Linux and Windows are supported.")
        }
    }
}
