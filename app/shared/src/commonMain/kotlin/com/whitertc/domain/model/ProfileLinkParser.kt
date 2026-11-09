package com.whitertc.domain.model

import kotlin.time.Clock

object ProfileLinkParser {

    const val DEFAULT_DIRECT_NAME = "Direct"
    const val DEFAULT_VK_NAME = "VK Calls"

    fun cleanProfileDisplayName(name: String): String {
        val trimmed = name.trim()
        val devicePattern = Regex("""\s*\((?:Phone|Desktop|Android|PC|Device|iOS|Windows|Mac|Linux).*\)$""", RegexOption.IGNORE_CASE)
        var cleaned = trimmed.replace(devicePattern, "").trim()
        while (cleaned.endsWith(")") && cleaned.count { it == '(' } < cleaned.count { it == ')' }) {
            cleaned = cleaned.dropLast(1).trim()
        }
        return cleaned.ifBlank { trimmed }
    }

    data class ParsedServerEndpoint(
        val host: String,
        val port: Int,
        val serverPorts: List<Int>,
        val secret: String,
        val name: String?,
        val signalingSource: String,
        val isVkRelay: Boolean,
        val tunnelSubnet: String? = null,
        val parallelChannels: Int = 8
    )

    fun parseServerEndpoint(rawInput: String): ParsedServerEndpoint? {
        val input = rawInput.trim()
        if (input.isBlank()) return null

        val lower = input.lowercase()
        val prefix = when {
            lower.startsWith("whiteproxy://") -> input.substring(0, "whiteproxy://".length)
            lower.startsWith("white://") -> input.substring(0, "white://".length)
            else -> null
        }

        if (prefix != null) {
            val withoutScheme = input.substring(prefix.length)
            val fragment = if (withoutScheme.contains('#')) withoutScheme.substringAfter('#').trim() else null
            val beforeFragment = withoutScheme.substringBefore('#')

            val hostPortPart = beforeFragment.substringBefore('?')
            val queryPart = if (beforeFragment.contains('?')) beforeFragment.substringAfter('?') else ""

            val host = hostPortPart.substringBefore(':').trim()
            val port = hostPortPart.substringAfter(':', "3478").trim().toIntOrNull() ?: 3478

            val queryParams = if (queryPart.isNotBlank()) {
                queryPart.split('&').associate { param ->
                    val key = param.substringBefore('=')
                    val value = param.substringAfter('=', "")
                    key to value
                }
            } else emptyMap()

            val rawName = fragment?.ifBlank { null } ?: queryParams["name"]?.ifBlank { null }
            val name = rawName?.let { cleanProfileDisplayName(urlDecode(it)) }?.ifBlank { null }
            val secret = queryParams["secret"]?.let { urlDecode(it).trim() }?.ifBlank { null } ?: ""
            val signaling = queryParams["signaling"]?.let { urlDecode(it).trim() }?.ifBlank { "" } ?: ""
            val provider = queryParams["provider"]?.lowercase()

            val ipParam = queryParams["ip"]?.let { urlDecode(it).trim() }?.ifBlank { null }
            val subnetParam = queryParams["subnet"]?.let { urlDecode(it).trim() }?.ifBlank { null }
            val tunnelSubnet = when {
                subnetParam != null -> if (subnetParam.contains("/")) subnetParam else "$subnetParam/24"
                ipParam != null -> if (ipParam.contains("/")) ipParam else "$ipParam/24"
                else -> null
            }

            val portsParam = queryParams["ports"]
            val parsedPorts = portsParam?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..65535 }
            val serverPorts = if (!parsedPorts.isNullOrEmpty()) parsedPorts else listOf(port)

            val channelsParam = queryParams["channels"]?.toIntOrNull()
                ?: queryParams["parallelChannels"]?.toIntOrNull()
                ?: 8
            val parallelChannels = channelsParam.coerceIn(1, 32)

            val isVk = provider == "vk_calls" || signaling.isNotBlank()

            return ParsedServerEndpoint(
                host = host,
                port = port,
                serverPorts = serverPorts,
                secret = secret,
                name = name,
                signalingSource = signaling,
                isVkRelay = isVk,
                tunnelSubnet = tunnelSubnet,
                parallelChannels = parallelChannels
            )
        }

        // Raw host or host:port
        val hostPart = input.substringBefore(':').trim()
        val portPart = if (input.contains(':')) input.substringAfter(':').trim().toIntOrNull() ?: 3478 else 3478

        if (hostPart.matches(Regex("^[a-zA-Z0-9.-]+$")) && !hostPart.startsWith("http")) {
            return ParsedServerEndpoint(
                host = hostPart,
                port = portPart,
                serverPorts = listOf(portPart),
                secret = "",
                name = null,
                signalingSource = "",
                isVkRelay = false
            )
        }

        return null
    }

    fun parseProfileFromLink(rawInput: String): ConnectionProfile? {
        val input = rawInput.trim()
        if (input.isBlank()) return null

        // WhiteRTC URI or IP endpoint
        val endpoint = parseServerEndpoint(input) ?: return null
        val defaultName = if (endpoint.isVkRelay) DEFAULT_VK_NAME else DEFAULT_DIRECT_NAME
        val profileName = endpoint.name ?: defaultName
        val providerType = if (endpoint.isVkRelay) TurnProviderType.VK_CALLS else TurnProviderType.DIRECT_WEBRTC

        return ConnectionProfile(
            id = "profile_${Clock.System.now().toEpochMilliseconds()}",
            name = profileName,
            providerType = providerType,
            serverHost = endpoint.host,
            serverPort = endpoint.port,
            serverPorts = endpoint.serverPorts,
            sharedSecret = endpoint.secret,
            signalingSource = endpoint.signalingSource,
            isActive = false,
            pingMs = 0L,
            tunnelSubnet = endpoint.tunnelSubnet ?: "10.8.0.2/24",
            parallelChannels = endpoint.parallelChannels
        )
    }

    fun generateShareLink(profile: ConnectionProfile): String {
        val encodedName = urlEncode(profile.name)
        val portsStr = profile.serverPorts.joinToString(",")
        val isVk = profile.providerType == TurnProviderType.VK_CALLS

        val queryParts = mutableListOf<String>()
        if (profile.sharedSecret.isNotBlank()) {
            queryParts.add("secret=${urlEncode(profile.sharedSecret)}")
        }
        if (profile.serverPorts.isNotEmpty()) {
            queryParts.add("ports=$portsStr")
        }
        if (profile.parallelChannels != 8) {
            queryParts.add("channels=${profile.parallelChannels}")
        }
        if (isVk) {
            queryParts.add("provider=vk_calls")
            if (profile.signalingSource.isNotBlank()) {
                queryParts.add("signaling=${urlEncode(profile.signalingSource)}")
            }
        } else {
            queryParts.add("provider=direct_webrtc")
        }
        val queryString = queryParts.joinToString("&")
        return "white://${profile.serverHost}:${profile.serverPort}?$queryString#$encodedName"
    }

    fun urlEncode(value: String): String {
        val sb = StringBuilder()
        val bytes = value.encodeToByteArray()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) {
                sb.append(c.toChar())
            } else if (c == ' '.code) {
                sb.append('+')
            } else {
                sb.append('%')
                val hex = c.toString(16).uppercase()
                if (hex.length == 1) sb.append('0')
                sb.append(hex)
            }
        }
        return sb.toString()
    }

    fun urlDecode(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            when (val c = value[i]) {
                '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                '%' -> {
                    if (i + 2 < value.length) {
                        val hex = value.substring(i + 1, i + 3)
                        val byteVal = hex.toIntOrNull(16)
                        if (byteVal != null) {
                            bytes.add(byteVal.toByte())
                            i += 3
                            continue
                        }
                    }
                    bytes.add('%'.code.toByte())
                    i++
                }
                else -> {
                    bytes.addAll(c.toString().encodeToByteArray().toList())
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
