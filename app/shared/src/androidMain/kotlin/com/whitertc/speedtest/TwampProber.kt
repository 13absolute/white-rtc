package com.whitertc.speedtest

import android.net.Network
import com.whitertc.core.logging.WhiteLogger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class LatencyTelemetry(
    val meanRttMs: Double,
    val jitterRfc3550Ms: Double,
    val packetLossPercentage: Double,
    val outOfOrderCount: Int
)

/**
 * High-precision TWAMP Light (RFC 5357) prober.
 * Sends cyclic UDP datagrams with nanosecond timestamps, calculating:
 * - Round-Trip Time (RTT)
 * - RFC 3550 inter-arrival jitter via EWMA filter (factor = 1/16)
 * - Packet Loss Rate (PLR)
 * - Out-of-Order delivery events
 */
class TwampProber(
    private val remoteHost: String,
    private val remotePort: Int = 8625
) {
    companion object {
        private const val TAG = "TWAMP-PROBER"
    }

    fun runProbeSequence(
        packetsToSend: Int = 50,
        bindToNetwork: Network? = null,
        intervalMs: Long = 20L
    ): LatencyTelemetry {
        var socket: DatagramSocket? = null
        return try {
            val address = InetAddress.getByName(remoteHost)
            socket = DatagramSocket()
            socket.soTimeout = 400

            if (bindToNetwork != null) {
                try {
                    bindToNetwork.bindSocket(socket)
                } catch (e: Exception) {
                    WhiteLogger.w(TAG) { "Failed to bind DatagramSocket to physical network: ${e.message}" }
                }
            }

            val txBuffer = ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN)
            val rxBytes = ByteArray(2048)
            val rxBuffer = ByteBuffer.wrap(rxBytes).order(ByteOrder.BIG_ENDIAN)
            val rxPacket = DatagramPacket(rxBytes, rxBytes.size)

            var packetsLost = 0
            var outOfOrderEvents = 0
            var cumulativeRttNano = 0L
            var receivedCount = 0
            var highestSeqReceived = -1
            var smoothedJitter = 0.0
            var previousTransitDelay = 0.0

            for (seq in 0 until packetsToSend) {
                txBuffer.clear()
                txBuffer.putInt(seq)
                val sendTimestampNano = System.nanoTime()
                txBuffer.putLong(sendTimestampNano)
                while (txBuffer.hasRemaining()) {
                    txBuffer.put(0.toByte())
                }

                val txPacket = DatagramPacket(txBuffer.array(), 48, address, remotePort)
                socket.send(txPacket)

                var receivedCurrent = false
                val deadlineMs = System.currentTimeMillis() + 350
                while (System.currentTimeMillis() < deadlineMs) {
                    try {
                        socket.receive(rxPacket)
                        val arrivalTimestampNano = System.nanoTime()
                        rxBuffer.position(0)
                        val echoSeq = rxBuffer.getInt()
                        val echoSendTimestamp = rxBuffer.getLong()

                        if (echoSeq <= highestSeqReceived) {
                            outOfOrderEvents++
                        } else {
                            highestSeqReceived = echoSeq
                        }

                        val sampleRtt = (arrivalTimestampNano - echoSendTimestamp) / 1_000_000.0
                        cumulativeRttNano += (arrivalTimestampNano - echoSendTimestamp)

                        if (receivedCount > 0) {
                            val transitDiff = abs(sampleRtt - previousTransitDelay)
                            // RFC 3550: J = J + (|D| - J) / 16
                            smoothedJitter += (transitDiff - smoothedJitter) / 16.0
                        }
                        previousTransitDelay = sampleRtt
                        receivedCount++
                        receivedCurrent = true
                        break
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        WhiteLogger.w(TAG) { "Probe read error: ${e.message}" }
                        break
                    }
                }

                if (!receivedCurrent) {
                    packetsLost++
                }

                if (intervalMs > 0 && seq < packetsToSend - 1) {
                    Thread.sleep(intervalMs)
                }
            }

            val averageRtt = if (receivedCount > 0) (cumulativeRttNano / receivedCount.toDouble()) / 1_000_000.0 else 0.0
            val lossRatio = (packetsLost.toDouble() / packetsToSend.toDouble()) * 100.0

            LatencyTelemetry(
                meanRttMs = averageRtt,
                jitterRfc3550Ms = smoothedJitter,
                packetLossPercentage = lossRatio,
                outOfOrderCount = outOfOrderEvents
            )
        } catch (e: Exception) {
            WhiteLogger.e(TAG, e) { "Error in TWAMP probe sequence: ${e.message}" }
            LatencyTelemetry(0.0, 0.0, 100.0, 0)
        } finally {
            runCatching { socket?.close() }
        }
    }
}
