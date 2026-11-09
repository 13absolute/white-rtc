package com.whitertc.speedtest

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.whitertc.domain.speedtest.BenchmarkSummary
import com.whitertc.domain.speedtest.SpeedTestResult
import com.whitertc.domain.speedtest.SpeedTestState
import com.whitertc.domain.speedtest.SpeedTestStep
import com.whitertc.domain.speedtest.TelemetryPoint
import com.whitertc.domain.speedtest.VpsSpeedTestRunner
import com.whitertc.domain.speedtest.calculateBenchmarkSummary
import org.koin.core.annotation.Single

@Single(binds = [VpsSpeedTestRunner::class])
class VpsSpeedTestRunnerImpl : VpsSpeedTestRunner {

    companion object {
        private const val TAG = "SPEEDTEST-BENCH"
    }

    override fun runBenchmark(
        serverHost: String,
        serverPort: Int,
        runsPerPhase: Int,
        downloadPayloadMb: Int,
        uploadPayloadMb: Int,
        onPrepareDirectPhase: (suspend () -> Unit)?,
        onPrepareVpnPhase: (suspend () -> Unit)?
    ): Flow<SpeedTestState> = flow {
        val directResults = mutableListOf<SpeedTestResult>()
        val vpnResults = mutableListOf<SpeedTestResult>()

        if (onPrepareDirectPhase != null) {
            emit(SpeedTestState.Running("Preparing direct measurements...", 0, runsPerPhase, isVpnPhase = false, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.PREPARING_DIRECT))
            onPrepareDirectPhase()
        }

        for (i in 1..runsPerPhase) {
            emit(SpeedTestState.Running("Direct download (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = false, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.DIRECT_DOWNLOAD))
            val (downMbps, downCurve) = runSingleDownload(serverHost, serverPort, downloadPayloadMb)
            delay(100)

            emit(SpeedTestState.Running("Direct upload (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = false, currentDownMbps = downMbps, currentUpMbps = null, step = SpeedTestStep.DIRECT_UPLOAD))
            val (upMbps, upCurve) = runSingleUpload(serverHost, serverPort, uploadPayloadMb)
            delay(100)

            val res = SpeedTestResult(i, isVpn = false, downloadMbps = downMbps, uploadMbps = upMbps, downloadCurve = downCurve, uploadCurve = upCurve)
            directResults.add(res)
            emit(SpeedTestState.Running("Direct #$i completed", i, runsPerPhase, isVpnPhase = false, currentDownMbps = downMbps, currentUpMbps = upMbps, step = SpeedTestStep.DIRECT_DONE))
        }

        if (onPrepareVpnPhase != null) {
            emit(SpeedTestState.Running("Connecting VPN tunnel...", 0, runsPerPhase, isVpnPhase = true, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.PREPARING_VPN))
            onPrepareVpnPhase()
        }

        for (i in 1..runsPerPhase) {
            emit(SpeedTestState.Running("VPN download (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = true, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.VPN_DOWNLOAD))
            val (downMbps, downCurve) = runSingleDownload(serverHost, serverPort, downloadPayloadMb)
            delay(100)

            emit(SpeedTestState.Running("VPN upload (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = true, currentDownMbps = downMbps, currentUpMbps = null, step = SpeedTestStep.VPN_UPLOAD))
            val (upMbps, upCurve) = runSingleUpload(serverHost, serverPort, uploadPayloadMb)
            delay(100)

            val res = SpeedTestResult(i, isVpn = true, downloadMbps = downMbps, uploadMbps = upMbps, downloadCurve = downCurve, uploadCurve = upCurve)
            vpnResults.add(res)
            emit(SpeedTestState.Running("VPN #$i completed", i, runsPerPhase, isVpnPhase = true, currentDownMbps = downMbps, currentUpMbps = upMbps, step = SpeedTestStep.VPN_DONE))
        }

        val summary = calculateBenchmarkSummary(directResults, vpnResults)

        emit(SpeedTestState.Completed(summary, directResults + vpnResults))
    }.flowOn(Dispatchers.IO)

    private fun runSingleDownload(
        serverHost: String,
        serverPort: Int,
        payloadMb: Int
    ): Pair<Double, List<TelemetryPoint>> {
        val targetUrl = "http://$serverHost:$serverPort/bench/download?sizeMb=$payloadMb"
        val curve = mutableListOf<TelemetryPoint>()
        var conn: HttpURLConnection? = null
        var stream: InputStream? = null
        return try {
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 10000
                requestMethod = "GET"
            }
            conn.connect()
            stream = conn.inputStream
            val buf = ByteArray(32768)
            var totalRead = 0L
            val startNs = System.nanoTime()
            var lastSampleNs = startNs

            while (true) {
                val n = stream.read(buf)
                if (n == -1) break
                totalRead += n
                val nowNs = System.nanoTime()
                if (nowNs - lastSampleNs >= 100_000_000L) { // 100ms
                    val elapsedSec = (nowNs - startNs) / 1_000_000_000.0
                    val instantBps = if (elapsedSec > 0) (totalRead * 8.0) / elapsedSec else 0.0
                    curve.add(TelemetryPoint((nowNs - startNs) / 1_000_000L, instantBps / 1_000_000.0, totalRead))
                    lastSampleNs = nowNs
                }
            }
            val totalSec = (System.nanoTime() - startNs) / 1_000_000_000.0
            val mbps = if (totalSec > 0) (totalRead * 8.0 / totalSec) / 1_000_000.0 else 0.0
            Pair(mbps, curve)
        } catch (e: Exception) {
            Pair(0.0, curve)
        } finally {
            runCatching { stream?.close() }
            runCatching { conn?.disconnect() }
        }
    }

    private fun runSingleUpload(
        serverHost: String,
        serverPort: Int,
        payloadMb: Int
    ): Pair<Double, List<TelemetryPoint>> {
        val targetUrl = "http://$serverHost:$serverPort/bench/upload"
        val curve = mutableListOf<TelemetryPoint>()
        var conn: HttpURLConnection? = null
        var out: OutputStream? = null
        return try {
            val totalBytes = payloadMb.toLong() * 1024L * 1024L
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 10000
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(totalBytes)
            }
            conn.connect()
            out = conn.outputStream
            val chunk = ByteArray(32768)
            var written = 0L
            val startNs = System.nanoTime()
            var lastSampleNs = startNs

            while (written < totalBytes) {
                val toWrite = minOf(chunk.size.toLong(), totalBytes - written).toInt()
                out.write(chunk, 0, toWrite)
                written += toWrite
                val nowNs = System.nanoTime()
                if (nowNs - lastSampleNs >= 100_000_000L) {
                    val elapsedSec = (nowNs - startNs) / 1_000_000_000.0
                    val instantBps = if (elapsedSec > 0) (written * 8.0) / elapsedSec else 0.0
                    curve.add(TelemetryPoint((nowNs - startNs) / 1_000_000L, instantBps / 1_000_000.0, written))
                    lastSampleNs = nowNs
                }
            }
            out.flush()
            val responseCode = conn.responseCode
            val totalSec = (System.nanoTime() - startNs) / 1_000_000_000.0
            val mbps = if (totalSec > 0) (written * 8.0 / totalSec) / 1_000_000.0 else 0.0
            Pair(mbps, curve)
        } catch (e: Exception) {
            Pair(0.0, curve)
        } finally {
            runCatching { out?.close() }
            runCatching { conn?.disconnect() }
        }
    }
}
