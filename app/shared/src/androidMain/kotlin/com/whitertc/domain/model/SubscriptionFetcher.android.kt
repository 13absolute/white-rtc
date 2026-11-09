package com.whitertc.domain.model

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.whitertc.core.logging.WhiteLogger

actual suspend fun fetchSubscriptionContent(url: String, deviceId: String, deviceName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8")
        val encodedDeviceName = URLEncoder.encode(deviceName, "UTF-8")
        val separator = if (url.contains("?")) "&" else "?"
        val fullUrl = "$url${separator}deviceId=$encodedDeviceId&deviceName=$encodedDeviceName"

        WhiteLogger.i("SubscriptionFetcher") { "Connecting to subscription server: $fullUrl (deviceId=$deviceId)" }

        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "WhiteRtc-Android/1.0")
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("X-Device-Name", deviceName)
            setRequestProperty("Accept", "text/plain")
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            WhiteLogger.i("SubscriptionFetcher") { "Subscription fetch succeeded (HTTP $code, ${content.length} chars)" }
            content
        } else {
            val errBody = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            WhiteLogger.w("SubscriptionFetcher") { "Subscription server error: HTTP $code (${conn.responseMessage}). Body: $errBody" }
            null
        }
    }.onFailure { e ->
        WhiteLogger.e("SubscriptionFetcher", "Failed to fetch subscription from $url: ${e::class.simpleName}: ${e.message}", e)
    }.getOrNull()
}

actual fun getLocalDeviceId(): String {
    val model = android.os.Build.MODEL ?: "android"
    val id = android.os.Build.ID ?: "id"
    val seed = (model + id).hashCode().toLong() and 0xFFFFFFFFL
    return "android_${seed.toString(16)}"
}

actual fun getLocalDeviceName(): String {
    val model = android.os.Build.MODEL ?: "Android"
    return "Phone ($model)"
}
