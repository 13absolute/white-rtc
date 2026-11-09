package com.whitertc.domain.model

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun fetchSubscriptionContent(url: String, deviceId: String, deviceName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val encodedDeviceId = URLEncoder.encode(deviceId, "UTF-8")
        val encodedDeviceName = URLEncoder.encode(deviceName, "UTF-8")
        val separator = if (url.contains("?")) "&" else "?"
        val fullUrl = "$url${separator}deviceId=$encodedDeviceId&deviceName=$encodedDeviceName"

        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "WhiteRtc-Desktop/1.0")
            setRequestProperty("X-Device-Id", deviceId)
            setRequestProperty("X-Device-Name", deviceName)
            setRequestProperty("Accept", "text/plain")
        }

        if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    }.getOrNull()
}

actual fun getLocalDeviceId(): String {
    val seed = (System.getProperty("user.name", "") + "@" + (System.getenv("HOSTNAME") ?: "desktop") + System.getProperty("os.name", "")).hashCode().toLong() and 0xFFFFFFFFL
    return "pc_${seed.toString(16)}"
}

actual fun getLocalDeviceName(): String {
    val os = System.getProperty("os.name", "PC")
    return "Desktop ($os)"
}
