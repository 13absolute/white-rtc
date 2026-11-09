package com.whitertc.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.whitertc.core.logging.WhiteLogger
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
class VpsSpeedTestRunnerImpl(
    private val context: Context
) : VpsSpeedTestRunner {

    companion object {
        private const val TAG = "SPEEDTEST-BENCH"
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun findPhysicalNetwork(): Network? {
        val allNets = runCatching { connectivityManager.allNetworks }.getOrNull() ?: return null
        for (net in allNets) {
            val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) {
                return net
            }
        }
        return null
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
        WhiteLogger.i(TAG) { "=======================================================" }
        WhiteLogger.i(TAG) { "BENCHMARK START: $runsPerPhase direct vs $runsPerPhase over VPN (DL: ${downloadPayloadMb}MB, UL: ${uploadPayloadMb}MB)" }
        WhiteLogger.i(TAG) { "Server: http://$serverHost:$serverPort" }
        WhiteLogger.i(TAG) { "=======================================================" }

        val directResults = mutableListOf<SpeedTestResult>()
        val vpnResults = mutableListOf<SpeedTestResult>()

        // 1. PREPARATION AND DIRECT TESTS (Direct Baseline)
        if (onPrepareDirectPhase != null) {
            emit(SpeedTestState.Running("Preparing physical network...", 0, runsPerPhase, isVpnPhase = false, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.PREPARING_DIRECT))
            onPrepareDirectPhase()
        }
        val physicalNet = findPhysicalNetwork()
        WhiteLogger.i(TAG) { "Physical network for direct phase: ${physicalNet?.let { "found (handle=${it.networkHandle})" } ?: "default network"}" }

        val prober = TwampProber(serverHost, 8625)

        for (i in 1..runsPerPhase) {
            emit(SpeedTestState.Running("Direct probe (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = false, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.DIRECT_PROBE))
            val telemetry = prober.runProbeSequence(packetsToSend = 30, bindToNetwork = physicalNet, intervalMs = 20)

            emit(SpeedTestState.Running("Direct download (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = false, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.DIRECT_DOWNLOAD))
            val (downMbps, downCurve) = runSingleDownload(serverHost, serverPort, downloadPayloadMb, bindToNetwork = physicalNet)
            delay(200)

            emit(SpeedTestState.Running("Direct upload (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = false, currentDownMbps = downMbps, currentUpMbps = null, step = SpeedTestStep.DIRECT_UPLOAD))
            val (upMbps, upCurve) = runSingleUpload(serverHost, serverPort, uploadPayloadMb, bindToNetwork = physicalNet)
            delay(200)

            val res = SpeedTestResult(
                runIndex = i,
                isVpn = false,
                downloadMbps = downMbps,
                uploadMbps = upMbps,
                downloadCurve = downCurve,
                uploadCurve = upCurve,
                meanRttMs = telemetry.meanRttMs,
                jitterMs = telemetry.jitterRfc3550Ms,
                packetLossPercent = telemetry.packetLossPercentage,
                outOfOrderCount = telemetry.outOfOrderCount
            )
            directResults.add(res)
            WhiteLogger.i(TAG) { "[DIRECT #$i/$runsPerPhase] Down: ${"%.2f".format(downMbps)} Mbps | Up: ${"%.2f".format(upMbps)} Mbps | RTT: ${"%.1f".format(telemetry.meanRttMs)} ms | Jitter: ${"%.2f".format(telemetry.jitterRfc3550Ms)} ms | Loss: ${"%.1f".format(telemetry.packetLossPercentage)}% | OoO: ${telemetry.outOfOrderCount}" }
            logCurveSample("DIRECT #$i DL", downCurve)
            emit(SpeedTestState.Running("Direct #$i completed", i, runsPerPhase, isVpnPhase = false, currentDownMbps = downMbps, currentUpMbps = upMbps, step = SpeedTestStep.DIRECT_DONE))
        }

        // 2. PREPARATION AND VPN TESTS (Overlay)
        if (onPrepareVpnPhase != null) {
            emit(SpeedTestState.Running("Connecting VPN tunnel...", 0, runsPerPhase, isVpnPhase = true, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.PREPARING_VPN))
            onPrepareVpnPhase()
        }

        for (i in 1..runsPerPhase) {
            emit(SpeedTestState.Running("VPN probe (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = true, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.VPN_PROBE))
            val telemetry = prober.runProbeSequence(packetsToSend = 30, bindToNetwork = null, intervalMs = 20)

            emit(SpeedTestState.Running("VPN download (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = true, currentDownMbps = null, currentUpMbps = null, step = SpeedTestStep.VPN_DOWNLOAD))
            val (downMbps, downCurve) = runSingleDownload(serverHost, serverPort, downloadPayloadMb, bindToNetwork = null)
            delay(200)

            emit(SpeedTestState.Running("VPN upload (#$i/$runsPerPhase)...", i, runsPerPhase, isVpnPhase = true, currentDownMbps = downMbps, currentUpMbps = null, step = SpeedTestStep.VPN_UPLOAD))
            val (upMbps, upCurve) = runSingleUpload(serverHost, serverPort, uploadPayloadMb, bindToNetwork = null)
            delay(200)

            val res = SpeedTestResult(
                runIndex = i,
                isVpn = true,
                downloadMbps = downMbps,
                uploadMbps = upMbps,
                downloadCurve = downCurve,
                uploadCurve = upCurve,
                meanRttMs = telemetry.meanRttMs,
                jitterMs = telemetry.jitterRfc3550Ms,
                packetLossPercent = telemetry.packetLossPercentage,
                outOfOrderCount = telemetry.outOfOrderCount
            )
            vpnResults.add(res)
            WhiteLogger.i(TAG) { "[VPN-MUX #$i/$runsPerPhase] Down: ${"%.2f".format(downMbps)} Mbps | Up: ${"%.2f".format(upMbps)} Mbps | RTT: ${"%.1f".format(telemetry.meanRttMs)} ms | Jitter: ${"%.2f".format(telemetry.jitterRfc3550Ms)} ms | Loss: ${"%.1f".format(telemetry.packetLossPercentage)}% | OoO: ${telemetry.outOfOrderCount}" }
            logCurveSample("VPN-MUX #$i DL", downCurve)
            emit(SpeedTestState.Running("VPN #$i completed", i, runsPerPhase, isVpnPhase = true, currentDownMbps = downMbps, currentUpMbps = upMbps, step = SpeedTestStep.VPN_DONE))
        }

        val summary = calculateBenchmarkSummary(directResults, vpnResults)

        WhiteLogger.i(TAG) { "=======================================================" }
        WhiteLogger.i(TAG) { "BENCHMARK SUMMARY (Averages, Medians & 10% Trimmed):" }
        WhiteLogger.i(TAG) { "Direct Baseline: Down avg=${"%.2f".format(summary.directDownAvg)} (med=${"%.2f".format(summary.directDownMedian)}, trim=${"%.2f".format(summary.directDownTrimmedMean)}) Mbps, Up avg=${"%.2f".format(summary.directUpAvg)} (med=${"%.2f".format(summary.directUpMedian)}, trim=${"%.2f".format(summary.directUpTrimmedMean)}) Mbps" }
        WhiteLogger.i(TAG) { "Direct Latency:  RTT avg=${"%.1f".format(summary.directRttAvg)} ms, Jitter=${"%.2f".format(summary.directJitterAvg)} ms, Loss=${"%.1f".format(summary.directLossAvg)}%, OoO=${summary.directOoOTotal}" }
        WhiteLogger.i(TAG) { "VPN Multipath:   Down avg=${"%.2f".format(summary.vpnDownAvg)} (med=${"%.2f".format(summary.vpnDownMedian)}, trim=${"%.2f".format(summary.vpnDownTrimmedMean)}) Mbps, Up avg=${"%.2f".format(summary.vpnUpAvg)} (med=${"%.2f".format(summary.vpnUpMedian)}, trim=${"%.2f".format(summary.vpnUpTrimmedMean)}) Mbps" }
        WhiteLogger.i(TAG) { "VPN Latency:     RTT avg=${"%.1f".format(summary.vpnRttAvg)} ms, Jitter=${"%.2f".format(summary.vpnJitterAvg)} ms, Loss=${"%.1f".format(summary.vpnLossAvg)}%, OoO=${summary.vpnOoOTotal}" }
        WhiteLogger.i(TAG) { "Efficiency (Avg):    Download=${"%.1f".format(summary.downRatioPercent)}%, Upload=${"%.1f".format(summary.upRatioPercent)}%" }
        WhiteLogger.i(TAG) { "Efficiency (Median): Download=${"%.1f".format(summary.medianDownRatioPercent)}%, Upload=${"%.1f".format(summary.medianUpRatioPercent)}%" }
        WhiteLogger.i(TAG) { "=======================================================" }

        dumpJsonSummary(runsPerPhase, downloadPayloadMb, uploadPayloadMb, summary, directResults, vpnResults)

        WhiteLogger.i(TAG) { "BENCHMARK_COMPLETE" }

        emit(SpeedTestState.Completed(summary, directResults + vpnResults))
    }.flowOn(Dispatchers.IO)

    private fun logCurveSample(tag: String, curve: List<TelemetryPoint>) {
        if (curve.isEmpty()) return
        val step = maxOf(1, curve.size / 5)
        val sampled = curve.filterIndexed { idx, _ -> idx % step == 0 || idx == curve.lastIndex }
        val repr = sampled.joinToString(" -> ") { "${"%.1f".format(it.instantMbps)}M" }
        WhiteLogger.i(TAG) { "  [$tag Ramp-up] $repr" }
    }

    private fun dumpJsonSummary(
        runs: Int,
        dlMb: Int,
        ulMb: Int,
        s: BenchmarkSummary,
        direct: List<SpeedTestResult>,
        vpn: List<SpeedTestResult>
    ) {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"timestamp\": \"${System.currentTimeMillis()}\",\n")
        sb.append("  \"runsPerPhase\": $runs,\n")
        sb.append("  \"downloadPayloadMb\": $dlMb,\n")
        sb.append("  \"uploadPayloadMb\": $ulMb,\n")
        sb.append("  \"direct\": {\n")
        sb.append("    \"downAvg\": ${s.directDownAvg},\n")
        sb.append("    \"downMedian\": ${s.directDownMedian},\n")
        sb.append("    \"downTrimmedMean\": ${s.directDownTrimmedMean},\n")
        sb.append("    \"upAvg\": ${s.directUpAvg},\n")
        sb.append("    \"upMedian\": ${s.directUpMedian},\n")
        sb.append("    \"upTrimmedMean\": ${s.directUpTrimmedMean},\n")
        sb.append("    \"rttAvg\": ${s.directRttAvg},\n")
        sb.append("    \"jitterAvg\": ${s.directJitterAvg},\n")
        sb.append("    \"lossAvg\": ${s.directLossAvg},\n")
        sb.append("    \"outOfOrderTotal\": ${s.directOoOTotal}\n")
        sb.append("  },\n")
        sb.append("  \"vpn\": {\n")
        sb.append("    \"downAvg\": ${s.vpnDownAvg},\n")
        sb.append("    \"downMedian\": ${s.vpnDownMedian},\n")
        sb.append("    \"downTrimmedMean\": ${s.vpnDownTrimmedMean},\n")
        sb.append("    \"upAvg\": ${s.vpnUpAvg},\n")
        sb.append("    \"upMedian\": ${s.vpnUpMedian},\n")
        sb.append("    \"upTrimmedMean\": ${s.vpnUpTrimmedMean},\n")
        sb.append("    \"rttAvg\": ${s.vpnRttAvg},\n")
        sb.append("    \"jitterAvg\": ${s.vpnJitterAvg},\n")
        sb.append("    \"lossAvg\": ${s.vpnLossAvg},\n")
        sb.append("    \"outOfOrderTotal\": ${s.vpnOoOTotal}\n")
        sb.append("  },\n")
        sb.append("  \"efficiency\": {\n")
        sb.append("    \"avgDownPercent\": ${s.downRatioPercent},\n")
        sb.append("    \"medianDownPercent\": ${s.medianDownRatioPercent},\n")
        sb.append("    \"avgUpPercent\": ${s.upRatioPercent},\n")
        sb.append("    \"medianUpPercent\": ${s.medianUpRatioPercent}\n")
        sb.append("  },\n")
        sb.append("  \"results\": [\n")
        val all = direct + vpn
        all.forEachIndexed { i, r ->
            val sep = if (i == all.lastIndex) "" else ","
            sb.append("    {\"run\": ${r.runIndex}, \"isVpn\": ${r.isVpn}, \"downMbps\": ${r.downloadMbps}, \"upMbps\": ${r.uploadMbps}, \"rttMs\": ${r.meanRttMs}, \"jitterMs\": ${r.jitterMs}, \"lossPct\": ${r.packetLossPercent}, \"ooo\": ${r.outOfOrderCount}}$sep\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        WhiteLogger.i(TAG) { "BENCHMARK_JSON_START" }
        WhiteLogger.i(TAG) { sb.toString() }
        WhiteLogger.i(TAG) { "BENCHMARK_JSON_END" }
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun openAndConnect(url: URL, bindToNetwork: Network?, configure: (HttpURLConnection) -> Unit): HttpURLConnection {
        if (bindToNetwork != null) {
            val bound = bindToNetwork.openConnection(url) as HttpURLConnection
            bound.connectTimeout = 10000
            bound.readTimeout = 30000
            configure(bound)
            try {
                bound.connect()
                return bound
            } catch (e: Exception) {
                WhiteLogger.w(TAG) { "Network bind connect failed (${e.message}), falling back to default network" }
                runCatching { bound.disconnect() }
            }
        }
        val defConn = url.openConnection() as HttpURLConnection
        defConn.connectTimeout = 10000
        defConn.readTimeout = 30000
        configure(defConn)
        defConn.connect()
        return defConn
    }

    private fun runSingleDownload(serverHost: String, serverPort: Int, sizeMb: Int, bindToNetwork: Network?): Pair<Double, List<TelemetryPoint>> {
        return try {
            val url = URL("http://$serverHost:$serverPort/speedtest/download?sizeMb=$sizeMb")
            val conn = openAndConnect(url, bindToNetwork) { c ->
                c.requestMethod = "GET"
                c.setRequestProperty("Connection", "close")
            }

            val buffer = ByteArray(64 * 1024)
            var totalBytes = 0L
            val points = mutableListOf<TelemetryPoint>()
            val startNs = System.nanoTime()
            var lastSampleNs = startNs
            var lastSampleBytes = 0L

            conn.inputStream.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    val nowNs = System.nanoTime()
                    val sampleDeltaNs = nowNs - lastSampleNs
                    if (sampleDeltaNs >= 250_000_000L) {
                        val deltaBytes = totalBytes - lastSampleBytes
                        val instantMbps = (deltaBytes * 8.0) / ((sampleDeltaNs / 1_000_000_000.0) * 1_000_000.0)
                        val elapsedMs = (nowNs - startNs) / 1_000_000L
                        points.add(TelemetryPoint(elapsedMs, instantMbps, totalBytes))
                        lastSampleNs = nowNs
                        lastSampleBytes = totalBytes
                    }
                }
            }
            val elapsedSec = maxOf(0.0001, (System.nanoTime() - startNs) / 1_000_000_000.0)
            conn.disconnect()
            val finalMbps = (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
            Pair(finalMbps, points)
        } catch (e: Exception) {
            WhiteLogger.w(TAG) { "Download error (bound=${bindToNetwork != null}): ${e.message}" }
            Pair(0.0, emptyList())
        }
    }

    private fun runSingleUpload(serverHost: String, serverPort: Int, sizeMb: Int, bindToNetwork: Network?): Pair<Double, List<TelemetryPoint>> {
        return try {
            val url = URL("http://$serverHost:$serverPort/speedtest/upload")
            val totalBytes = sizeMb.toLong() * 1024L * 1024L
            val conn = openAndConnect(url, bindToNetwork) { c ->
                c.doOutput = true
                c.requestMethod = "POST"
                c.setFixedLengthStreamingMode(totalBytes)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setRequestProperty("Connection", "close")
            }

            val buffer = ByteArray(64 * 1024)
            var written = 0L
            val points = mutableListOf<TelemetryPoint>()
            val startNs = System.nanoTime()
            var lastSampleNs = startNs
            var lastSampleBytes = 0L
            var lastLogNs = startNs

            conn.outputStream.use { out ->
                while (written < totalBytes) {
                    val toWrite = minOf(buffer.size.toLong(), totalBytes - written).toInt()
                    out.write(buffer, 0, toWrite)
                    written += toWrite
                    val nowNs = System.nanoTime()
                    val sampleDeltaNs = nowNs - lastSampleNs
                    if (sampleDeltaNs >= 250_000_000L) {
                        val deltaBytes = written - lastSampleBytes
                        val instantMbps = (deltaBytes * 8.0) / ((sampleDeltaNs / 1_000_000_000.0) * 1_000_000.0)
                        val elapsedMs = (nowNs - startNs) / 1_000_000L
                        points.add(TelemetryPoint(elapsedMs, instantMbps, written))
                        lastSampleNs = nowNs
                        lastSampleBytes = written
                    }
                    if (nowNs - lastLogNs > 500_000_000L || written >= totalBytes) {
                        lastLogNs = nowNs
                        WhiteLogger.i(TAG) { "[UPLOAD-PROGRESS] written=$written / $totalBytes (${written * 100 / totalBytes}%)" }
                    }
                }
                out.flush()
            }
            val elapsedSec = maxOf(0.0001, (System.nanoTime() - startNs) / 1_000_000_000.0)
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val finalMbps = (totalBytes * 8.0) / (elapsedSec * 1_000_000.0)
            Pair(finalMbps, points)
        } catch (e: Exception) {
            WhiteLogger.w(TAG, e) { "Upload error (bound=${bindToNetwork != null}): ${e.message}" }
            Pair(0.0, emptyList())
        }
    }
}
