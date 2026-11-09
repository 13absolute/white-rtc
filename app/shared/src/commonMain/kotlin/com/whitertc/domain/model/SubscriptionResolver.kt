package com.whitertc.domain.model

import com.whitertc.core.logging.WhiteLogger

expect suspend fun fetchSubscriptionContent(url: String, deviceId: String, deviceName: String): String?

expect fun getLocalDeviceId(): String

expect fun getLocalDeviceName(): String

object SubscriptionResolver {

    fun isSubscriptionUrl(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        val isHttpSub = (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("white+sub://")) &&
                trimmed.contains("/sub/")
        val isWhiteSub = (trimmed.startsWith("white://") || trimmed.startsWith("whiteproxy://")) &&
                (trimmed.contains("?sub=") || trimmed.contains("&sub="))
        return isHttpSub || isWhiteSub
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("white+sub://", ignoreCase = true)) {
            return "http://" + trimmed.substring("white+sub://".length)
        }
        if ((trimmed.startsWith("white://", ignoreCase = true) || trimmed.startsWith("whiteproxy://", ignoreCase = true)) &&
            (trimmed.contains("?sub=", ignoreCase = true) || trimmed.contains("&sub=", ignoreCase = true))
        ) {
            val endpoint = ProfileLinkParser.parseServerEndpoint(trimmed)
            val host = endpoint?.host ?: trimmed.substringAfter("://").substringBefore(':').substringBefore('?').substringBefore('/')
            val subToken = trimmed.substringAfter("sub=").substringBefore('&').substringBefore('#').trim()
            val httpPort = if (trimmed.contains("http_port=", ignoreCase = true)) {
                trimmed.substringAfter("http_port=").substringBefore('&').substringBefore('#').toIntOrNull() ?: 8085
            } else 8085
            return "http://$host:$httpPort/sub/$subToken"
        }
        return trimmed
    }

    suspend fun resolveSubscription(
        rawUrl: String,
        deviceId: String,
        deviceName: String
    ): ConnectionProfile? {
        val httpUrl = normalizeUrl(rawUrl)
        WhiteLogger.i("SubscriptionResolver") { "Resolving subscription from normalized URL: $httpUrl (raw: $rawUrl)" }
        val linkContent = fetchSubscriptionContent(httpUrl, deviceId, deviceName)
        if (linkContent == null) {
            WhiteLogger.w("SubscriptionResolver") { "Subscription fetch returned null (connection failed or server returned error)" }
            return null
        }
        val lines = linkContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        val whiteLink = lines.firstOrNull { it.startsWith("white://", ignoreCase = true) || it.startsWith("whiteproxy://", ignoreCase = true) }
        if (whiteLink == null) {
            WhiteLogger.w("SubscriptionResolver") { "No valid white:// link found in server response. Raw payload:\n$linkContent" }
            return null
        }
        val profile = ProfileLinkParser.parseProfileFromLink(whiteLink)
        if (profile == null) {
            WhiteLogger.w("SubscriptionResolver") { "Failed to parse profile from link: $whiteLink" }
            return null
        }
        WhiteLogger.i("SubscriptionResolver") { "Successfully resolved profile '${profile.displayName}' (server=${profile.serverHost}:${profile.serverPort}, channels=${profile.parallelChannels})" }
        return profile.copy(name = profile.displayName)
    }
}
