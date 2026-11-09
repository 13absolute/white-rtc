package com.whitertc.domain.speedtest

import kotlinx.coroutines.flow.Flow

data class TelemetryPoint(
    val elapsedMs: Long,
    val instantMbps: Double,
    val bytesTransferred: Long
)

data class SpeedTestResult(
    val runIndex: Int,
    val isVpn: Boolean,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val downloadCurve: List<TelemetryPoint>,
    val uploadCurve: List<TelemetryPoint>,
    val meanRttMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val packetLossPercent: Double = 0.0,
    val outOfOrderCount: Int = 0
)

data class BenchmarkSummary(
    val directDownAvg: Double,
    val directUpAvg: Double,
    val vpnDownAvg: Double,
    val vpnUpAvg: Double,
    val downRatioPercent: Double,
    val upRatioPercent: Double,
    val directDownMedian: Double,
    val directUpMedian: Double,
    val vpnDownMedian: Double,
    val vpnUpMedian: Double,
    val medianDownRatioPercent: Double,
    val medianUpRatioPercent: Double,
    val directDownTrimmedMean: Double = directDownAvg,
    val directUpTrimmedMean: Double = directUpAvg,
    val vpnDownTrimmedMean: Double = vpnDownAvg,
    val vpnUpTrimmedMean: Double = vpnUpAvg,
    val directRttAvg: Double = 0.0,
    val vpnRttAvg: Double = 0.0,
    val directJitterAvg: Double = 0.0,
    val vpnJitterAvg: Double = 0.0,
    val directLossAvg: Double = 0.0,
    val vpnLossAvg: Double = 0.0,
    val directOoOTotal: Int = 0,
    val vpnOoOTotal: Int = 0
)

fun calculateBenchmarkSummary(
    directResults: List<SpeedTestResult>,
    vpnResults: List<SpeedTestResult>
): BenchmarkSummary {
    fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val middle = size / 2
        return if (size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    fun List<Double>.trimmedMean(fraction: Double = 0.1): Double {
        if (isEmpty()) return 0.0
        if (size < 5) return average()
        val sorted = sorted()
        val dropCount = maxOf(1, (size * fraction).toInt())
        val trimmed = sorted.drop(dropCount).dropLast(dropCount)
        return if (trimmed.isNotEmpty()) trimmed.average() else average()
    }

    val directDownAvg = if (directResults.isNotEmpty()) directResults.map { it.downloadMbps }.average() else 0.0
    val directUpAvg = if (directResults.isNotEmpty()) directResults.map { it.uploadMbps }.average() else 0.0
    val vpnDownAvg = if (vpnResults.isNotEmpty()) vpnResults.map { it.downloadMbps }.average() else 0.0
    val vpnUpAvg = if (vpnResults.isNotEmpty()) vpnResults.map { it.uploadMbps }.average() else 0.0

    val directDownMedian = directResults.map { it.downloadMbps }.median()
    val directUpMedian = directResults.map { it.uploadMbps }.median()
    val vpnDownMedian = vpnResults.map { it.downloadMbps }.median()
    val vpnUpMedian = vpnResults.map { it.uploadMbps }.median()

    val directDownTrimmed = directResults.map { it.downloadMbps }.trimmedMean(0.1)
    val directUpTrimmed = directResults.map { it.uploadMbps }.trimmedMean(0.1)
    val vpnDownTrimmed = vpnResults.map { it.downloadMbps }.trimmedMean(0.1)
    val vpnUpTrimmed = vpnResults.map { it.uploadMbps }.trimmedMean(0.1)

    val downRatio = if (directDownAvg > 0) (vpnDownAvg / directDownAvg) * 100.0 else 0.0
    val upRatio = if (directUpAvg > 0) (vpnUpAvg / directUpAvg) * 100.0 else 0.0
    val medianDownRatio = if (directDownMedian > 0) (vpnDownMedian / directDownMedian) * 100.0 else 0.0
    val medianUpRatio = if (directUpMedian > 0) (vpnUpMedian / directUpMedian) * 100.0 else 0.0

    val directRttAvg = if (directResults.isNotEmpty()) directResults.map { it.meanRttMs }.average() else 0.0
    val vpnRttAvg = if (vpnResults.isNotEmpty()) vpnResults.map { it.meanRttMs }.average() else 0.0
    val directJitterAvg = if (directResults.isNotEmpty()) directResults.map { it.jitterMs }.average() else 0.0
    val vpnJitterAvg = if (vpnResults.isNotEmpty()) vpnResults.map { it.jitterMs }.average() else 0.0
    val directLossAvg = if (directResults.isNotEmpty()) directResults.map { it.packetLossPercent }.average() else 0.0
    val vpnLossAvg = if (vpnResults.isNotEmpty()) vpnResults.map { it.packetLossPercent }.average() else 0.0
    val directOoOTotal = directResults.sumOf { it.outOfOrderCount }
    val vpnOoOTotal = vpnResults.sumOf { it.outOfOrderCount }

    return BenchmarkSummary(
        directDownAvg = directDownAvg,
        directUpAvg = directUpAvg,
        vpnDownAvg = vpnDownAvg,
        vpnUpAvg = vpnUpAvg,
        downRatioPercent = downRatio,
        upRatioPercent = upRatio,
        directDownMedian = directDownMedian,
        directUpMedian = directUpMedian,
        vpnDownMedian = vpnDownMedian,
        vpnUpMedian = vpnUpMedian,
        medianDownRatioPercent = medianDownRatio,
        medianUpRatioPercent = medianUpRatio,
        directDownTrimmedMean = directDownTrimmed,
        directUpTrimmedMean = directUpTrimmed,
        vpnDownTrimmedMean = vpnDownTrimmed,
        vpnUpTrimmedMean = vpnUpTrimmed,
        directRttAvg = directRttAvg,
        vpnRttAvg = vpnRttAvg,
        directJitterAvg = directJitterAvg,
        vpnJitterAvg = vpnJitterAvg,
        directLossAvg = directLossAvg,
        vpnLossAvg = vpnLossAvg,
        directOoOTotal = directOoOTotal,
        vpnOoOTotal = vpnOoOTotal
    )
}

enum class SpeedTestStep {
    PREPARING_DIRECT,
    DIRECT_PROBE,
    DIRECT_DOWNLOAD,
    DIRECT_UPLOAD,
    DIRECT_DONE,
    PREPARING_VPN,
    VPN_PROBE,
    VPN_DOWNLOAD,
    VPN_UPLOAD,
    VPN_DONE
}

sealed interface SpeedTestState {
    data object Idle : SpeedTestState
    data class Running(
        val stepDescription: String,
        val currentRun: Int,
        val totalRuns: Int,
        val isVpnPhase: Boolean,
        val currentDownMbps: Double?,
        val currentUpMbps: Double?,
        val step: SpeedTestStep = SpeedTestStep.DIRECT_PROBE
    ) : SpeedTestState
    data class Completed(
        val summary: BenchmarkSummary,
        val results: List<SpeedTestResult>
    ) : SpeedTestState
    data class Error(val message: String) : SpeedTestState
}

interface VpsSpeedTestRunner {
    fun runBenchmark(
        serverHost: String,
        serverPort: Int,
        runsPerPhase: Int,
        downloadPayloadMb: Int,
        uploadPayloadMb: Int,
        onPrepareDirectPhase: (suspend () -> Unit)?,
        onPrepareVpnPhase: (suspend () -> Unit)?
    ): Flow<SpeedTestState>
}

