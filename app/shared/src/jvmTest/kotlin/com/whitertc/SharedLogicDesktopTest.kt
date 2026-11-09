package com.whitertc

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.whitertc.core.plugin.JvmPluginLoader
import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.core.provider.StandardTurnRelayProvider
import com.whitertc.plugin.PluginRegistryHelper

class SharedLogicDesktopTest {

    @Test
    fun testAndroidApkPackagingAndSpiDescriptor() {
        val apkFile = File("../../provider-vk/build/outputs/plugin/provider-vk-android.apk")
        val candidate = if (apkFile.exists()) apkFile else File("provider-vk/build/outputs/plugin/provider-vk-android.apk")
        if (!candidate.exists()) {
            println("[SKIP] provider-vk-android.apk not found at ${candidate.absolutePath}")
            return
        }

        assertTrue(candidate.length() > 5000, "APK must be a non-trivial archive")

        ZipFile(candidate).use { zip ->
            val dexEntry = zip.getEntry("classes.dex")
            assertNotNull(dexEntry, "classes.dex must be packed into APK")
            assertTrue(dexEntry.size > 1000, "classes.dex must contain compiled dex bytecode")

            val spiEntry = zip.getEntry("META-INF/services/com.whitertc.core.provider.RelayProvider")
            assertNotNull(spiEntry, "SPI descriptor must be packed into APK")

            val entryClass = zip.getInputStream(spiEntry).bufferedReader().readText().trim()
            assertEquals("com.whitertc.provider.vk.VkCallsRelayProvider", entryClass)
        }
    }

    @Test
    fun testEndToEndPluginLoadingAndBadgeValidation() {
        val jarFile = File("../../provider-vk/build/libs/provider-vk-desktop.jar")
        val candidate = if (jarFile.exists()) jarFile else File("provider-vk/build/libs/provider-vk-desktop.jar")
        if (!candidate.exists()) {
            println("[SKIP] provider-vk-desktop.jar not found at ${candidate.absolutePath}")
            return
        }

        val loadResult = JvmPluginLoader.loadPluginJar(candidate)
        assertTrue(loadResult.isSuccess, "Plugin JAR must load successfully: ${loadResult.exceptionOrNull()?.message}")

        // 1. Check dynamic badging
        val vkBadge = RelayProviderRegistry.getBadge("https://vk.com/call/join/test-call-hash-12345")
        assertEquals("+VK", vkBadge)

        val turnBadge = RelayProviderRegistry.getBadge("turn://user:pass@192.168.1.1:3478")
        assertEquals("+TURN", turnBadge)

        val directBadge = RelayProviderRegistry.getBadge("white://some-direct-connection")
        assertNull(directBadge, "Direct connections should not display a relay badge")

        // 2. Check match and validate
        val validVk = RelayProviderRegistry.matchAndValidate("https://vk.com/call/join/valid_hash_abc")
        assertNotNull(validVk)
        assertEquals("+VK", validVk.badgeLabel)
        assertNull(validVk.errorMessage)

        val validTurn = RelayProviderRegistry.matchAndValidate("turn://alice:secret@turn.example.com:3478")
        assertNotNull(validTurn)
        assertEquals("+TURN", validTurn.badgeLabel)
        assertNull(validTurn.errorMessage)

        val invalidTurnPort = RelayProviderRegistry.matchAndValidate("turn://alice:secret@turn.example.com:99999")
        assertNotNull(invalidTurnPort)
        assertNotNull(invalidTurnPort.errorMessage)

        // 3. Check UI model helper
        val uiPlugins = PluginRegistryHelper.getInstalledPlugins()
        val vkUi = uiPlugins.firstOrNull { it.id == "vk_calls" }
        assertNotNull(vkUi)
        assertEquals("+VK", vkUi.badgeLabel)
        assertEquals(false, vkUi.isBuiltIn)

        val turnUi = uiPlugins.firstOrNull { it.id == StandardTurnRelayProvider.providerId }
        assertNotNull(turnUi)
        assertEquals("+TURN", turnUi.badgeLabel)
        assertEquals(true, turnUi.isBuiltIn)
    }
}