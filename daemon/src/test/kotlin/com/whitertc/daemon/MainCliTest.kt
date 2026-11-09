package com.whitertc.daemon

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.whitertc.core.config.WhiteRtcConfig

class MainCliTest {

    @Test
    fun testGenerateUrlFromCli() {
        val oldOut = System.out
        val outStream = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(outStream))
            main(arrayOf("--generate-url"))
            val output = outStream.toString().trim()
            assertTrue(output.contains("white://"), "Actual output: '$output'")
            val urlLine = output.lines().first { it.startsWith("white://") }
            val parsed = WhiteRtcConfig.fromUrl(urlLine)
            assertEquals("127.0.0.1", parsed.serverHost)
            assertEquals("whitertc-secret-key-32b-long!!", parsed.sharedSecret)
        } finally {
            System.setOut(oldOut)
        }
    }

    @Test
    fun testGenerateBase64UrlFromCli() {
        val oldOut = System.out
        val outStream = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(outStream))
            main(arrayOf("--generate-url", "--base64"))
            val output = outStream.toString().trim()
            assertTrue(output.contains("white://"), "Actual output: '$output'")
            val urlLine = output.lines().first { it.startsWith("white://") }
            val parsed = WhiteRtcConfig.fromUrl(urlLine)
            assertEquals("127.0.0.1", parsed.serverHost)
            assertEquals("whitertc-secret-key-32b-long!!", parsed.sharedSecret)
        } finally {
            System.setOut(oldOut)
        }
    }
}
