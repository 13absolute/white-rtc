package com.whitertc.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileLinkParserTest {

    @Test
    fun testParseStandardWhiteLinkWithFragmentName() {
        val link = "white://198.51.100.1:3478?ports=3478,7882,50001&secret=secret123#Amsterdam+Fast"
        val profile = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(profile)
        assertEquals("Amsterdam Fast", profile.name)
        assertEquals("198.51.100.1", profile.serverHost)
        assertEquals(3478, profile.serverPort)
        assertEquals(listOf(3478, 7882, 50001), profile.serverPorts)
        assertEquals("secret123", profile.sharedSecret)
        assertEquals(TurnProviderType.DIRECT_WEBRTC, profile.providerType)
    }

    @Test
    fun testParseCyrillicNameInFragment() {
        val link = "white://198.51.100.1:3478?ports=3478,7882#%D0%9D%D0%B8%D0%B4%D0%B5%D1%80%D0%BB%D0%B0%D0%BD%D0%B4%D1%8B"
        val profile = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(profile)
        assertEquals("Нидерланды", profile.name)
    }

    @Test
    fun testParseMissingNameDefaultsToDirect() {
        val link = "white://198.51.100.1:3478?ports=3478,7882&secret=xyz"
        val profile = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(profile)
        assertEquals("Direct", profile.name)
        assertEquals(TurnProviderType.DIRECT_WEBRTC, profile.providerType)
    }

    @Test
    fun testParseNameFromQueryParam() {
        val link = "white://198.51.100.1:3478?name=Frankfurt+Node&secret=xyz"
        val profile = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(profile)
        assertEquals("Frankfurt Node", profile.name)
    }

    @Test
    fun testWhiteproxySchemeAlias() {
        val link = "whiteproxy://10.0.0.1:3478#Legacy+Cluster"
        val profile = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(profile)
        assertEquals("Legacy Cluster", profile.name)
        assertEquals("10.0.0.1", profile.serverHost)
    }

    @Test
    fun testRawHostPortDefaultsToDirect() {
        val raw = "192.168.1.1:3478"
        val profile = ProfileLinkParser.parseProfileFromLink(raw)

        assertNotNull(profile)
        assertEquals("Direct", profile.name)
        assertEquals("192.168.1.1", profile.serverHost)
        assertEquals(3478, profile.serverPort)
    }

    @Test
    fun testVkJoinLinkIsNotStandaloneProfile() {
        val vkLink = "https://vk.com/call/join/test-room-hash"
        val profile = ProfileLinkParser.parseProfileFromLink(vkLink)

        assertNull(profile, "Raw relay URL cannot be parsed as a standalone WhiteProxy server profile")
    }

    @Test
    fun testGenerateAndParseShareLinkRoundTrip() {
        val original = ConnectionProfile(
            id = "test_1",
            name = "Мой Сервер (SFU)",
            providerType = TurnProviderType.DIRECT_WEBRTC,
            serverHost = "vpn.myserver.org",
            serverPort = 3478,
            serverPorts = listOf(3478, 7882),
            sharedSecret = "my-secret-key",
            signalingSource = "",
            isActive = false,
            pingMs = 50L
        )

        val link = ProfileLinkParser.generateShareLink(original)
        val parsed = ProfileLinkParser.parseProfileFromLink(link)

        assertNotNull(parsed)
        assertEquals(original.name, parsed.name)
        assertEquals(original.serverHost, parsed.serverHost)
        assertEquals(original.serverPort, parsed.serverPort)
        assertEquals(original.serverPorts, parsed.serverPorts)
        assertEquals(original.sharedSecret, parsed.sharedSecret)
        assertEquals(original.providerType, parsed.providerType)
    }

    @Test
    fun testSubscriptionResolverWhiteScheme() {
        val whiteSub = "white://198.51.100.1:3478?ports=3478,7882&sub=sub0123456789abcdef0123456789abcdef#profile"
        val standardWhite = "white://198.51.100.1:3478?ports=3478,7882&secret=xyz#profile"

        assertEquals(true, SubscriptionResolver.isSubscriptionUrl(whiteSub))
        assertEquals(false, SubscriptionResolver.isSubscriptionUrl(standardWhite))

        val normalized = SubscriptionResolver.normalizeUrl(whiteSub)
        assertEquals("http://198.51.100.1:8085/sub/sub0123456789abcdef0123456789abcdef", normalized)
    }

    @Test
    fun testParseProfileWithAssignedIp() {
        val link = "white://198.51.100.1:3478?ports=3478,7882&secret=xyz&ip=10.8.0.6#device"
        val parsed = ProfileLinkParser.parseProfileFromLink(link)
        assertNotNull(parsed)
        assertEquals("10.8.0.6/24", parsed.tunnelSubnet)
        assertEquals("device", parsed.name)
        assertEquals("xyz", parsed.sharedSecret)

        val generated = ProfileLinkParser.generateShareLink(parsed)
        WhiteLinkValidator.assertValidWhiteLink(generated)
        assertFalse(generated.contains("ip="))
        assertFalse(generated.contains("10.8."))
    }

    @Test
    fun testStrictWhiteLinkValidatorRejectsForbiddenKeysAndLeaks() {
        // Valid link passes
        WhiteLinkValidator.assertValidWhiteLink("white://198.51.100.1:3478?ports=3478,7882&secret=xyz#device")

        // Link with &ip= must fail
        assertFailsWith<AssertionError> {
            WhiteLinkValidator.assertValidWhiteLink("white://198.51.100.1:3478?ports=3478,7882&secret=xyz&ip=10.8.0.6#device")
        }

        // Link with &subnet= must fail
        assertFailsWith<AssertionError> {
            WhiteLinkValidator.assertValidWhiteLink("white://198.51.100.1:3478?ports=3478,7882&secret=xyz&subnet=10.8.0.0/24#device")
        }

        // Link with unknown query param must fail
        assertFailsWith<AssertionError> {
            WhiteLinkValidator.assertValidWhiteLink("white://198.51.100.1:3478?ports=3478,7882&secret=xyz&hack=true#device")
        }
    }

    @Test
    fun testCleanProfileDisplayName() {
        assertEquals("device", ProfileLinkParser.cleanProfileDisplayName("device (Phone (21081111RG))"))
        assertEquals("device", ProfileLinkParser.cleanProfileDisplayName("device (Phone)"))
        assertEquals("device", ProfileLinkParser.cleanProfileDisplayName("device (Desktop (Linux))"))
        assertEquals("device", ProfileLinkParser.cleanProfileDisplayName("device (Android (Xiaomi))"))
        assertEquals("device", ProfileLinkParser.cleanProfileDisplayName("device)"))
        assertEquals("my (test) profile", ProfileLinkParser.cleanProfileDisplayName("my (test) profile (Phone (123))"))
        assertEquals("Direct", ProfileLinkParser.cleanProfileDisplayName("Direct"))
        assertEquals("VK Calls (hash)", ProfileLinkParser.cleanProfileDisplayName("VK Calls (hash)"))
    }
}

object WhiteLinkValidator {
    private val ALLOWED_QUERY_KEYS = setOf("ports", "secret", "provider", "signaling", "channels", "mtu")
    private val RFC1918_REGEX = Regex("""\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3})(?:/\d{1,2})?\b""")

    fun assertValidWhiteLink(link: String) {
        val trimmed = link.trim()
        assertTrue(
            trimmed.startsWith("white://") || trimmed.startsWith("whiteproxy://"),
            "Link must start with white:// or whiteproxy://: $trimmed"
        )

        val withoutScheme = trimmed.substringAfter("://")
        val beforeFragment = withoutScheme.substringBefore('#')
        val hostPortPart = beforeFragment.substringBefore('?')
        val queryPart = if (beforeFragment.contains('?')) beforeFragment.substringAfter('?') else ""

        val host = hostPortPart.substringBefore(':').trim()
        assertTrue(host.isNotBlank(), "Host must not be blank in link: $link")

        if (hostPortPart.contains(':')) {
            val port = hostPortPart.substringAfter(':').trim().toIntOrNull()
            assertNotNull(port, "Port must be a valid integer in link: $link")
            assertTrue(port in 1..65535, "Port must be in 1..65535 in link: $link")
        }

        assertTrue(queryPart.isNotBlank(), "Query parameters must not be empty in link: $link")
        val queryParams = queryPart.split('&').filter { it.isNotBlank() }.associate { param ->
            val key = param.substringBefore('=')
            val value = param.substringAfter('=', "")
            key to value
        }

        assertTrue(queryParams.containsKey("secret"), "Mandatory parameter 'secret' missing in link: $link")
        assertTrue(queryParams.containsKey("ports"), "Mandatory parameter 'ports' missing in link: $link")

        val illegalKeys = queryParams.keys - ALLOWED_QUERY_KEYS
        assertTrue(illegalKeys.isEmpty(), "Link contains forbidden query keys $illegalKeys in link: $link")

        assertFalse(
            queryParams.keys.any { it.contains("ip", ignoreCase = true) || it.contains("subnet", ignoreCase = true) },
            "Link query parameter names must not contain 'ip' or 'subnet': $queryParams"
        )

        assertFalse(
            RFC1918_REGEX.containsMatchIn(queryPart),
            "Link query leaked internal RFC 1918 subnet address: $queryPart"
        )
    }
}
