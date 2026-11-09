package com.whitertc.daemon

import java.io.File
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import com.whitertc.core.config.WhiteRtcConfig
import com.whitertc.core.logging.ConsoleLogSink
import com.whitertc.core.logging.WhiteLogger
import com.whitertc.core.plugin.JvmPluginLoader
import com.whitertc.daemon.engine.DaemonMultipathEngine
import com.whitertc.daemon.ipc.DaemonIpcServer
import com.whitertc.daemon.service.SystemdServiceHelper
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WhiteRtcDaemon")

fun main(args: Array<String>) {
    WhiteLogger.isDebugEnabled = true
    WhiteLogger.addSink(ConsoleLogSink())

    val argList = args.toList()

    // Initialize plugins from default directories (~/.whitertc/plugins, ./plugins)
    val defaultPlugins = JvmPluginLoader.loadDefaultPlugins()
    if (defaultPlugins.isNotEmpty()) {
        logger.info("Loaded {} default plugin(s): {}", defaultPlugins.size, defaultPlugins.map { it.providerId })
    }

    // Explicit plugin file passed via --plugin <path>
    if (argList.contains("--plugin")) {
        val pluginPath = argList.getOrNull(argList.indexOf("--plugin") + 1)
        if (!pluginPath.isNullOrBlank()) {
            val f = File(pluginPath)
            val res = JvmPluginLoader.loadPluginFile(f)
            if (res.isSuccess) {
                logger.info("Loaded external plugin from {}: {}", f.name, res.getOrThrow().map { it.providerId })
            } else {
                logger.warn("Failed to load plugin from {}: {}", f.name, res.exceptionOrNull()?.message)
            }
        }
    }

    // Explicit plugin directory passed via --plugins-dir <path>
    if (argList.contains("--plugins-dir") || argList.contains("--plugins")) {
        val flag = if (argList.contains("--plugins-dir")) "--plugins-dir" else "--plugins"
        val dirPath = argList.getOrNull(argList.indexOf(flag) + 1)
        if (!dirPath.isNullOrBlank()) {
            val d = File(dirPath)
            val loaded = JvmPluginLoader.loadPluginsFromDir(d)
            if (loaded.isNotEmpty()) {
                logger.info("Loaded {} external plugin(s) from {}: {}", loaded.size, d.name, loaded.map { it.providerId })
            }
        }
    }

    // 1. Config URL generation helper (pure stdout output without daemon banner)
    if (argList.contains("--generate-url") || argList.contains("--generate-link")) {
        val config = resolveConfigOrNull(argList) ?: WhiteRtcConfig.sample()
        val isBase64 = argList.contains("--base64")
        val generatedUrl = if (isBase64) config.toBase64Url() else config.toUrl()
        println(generatedUrl)
        return
    }

    logger.info("WhiteRTC Core starting...")
    logger.info("Platform: {} / {}", System.getProperty("os.name"), System.getProperty("os.arch"))

    // 2. Systemd helper actions
    if (argList.contains("--install-systemd")) {
        val execPath = argList.getOrNull(argList.indexOf("--install-systemd") + 1)
            ?: ProcessHandle.current().info().command().orElse(
                File(System.getProperty("java.home"), "bin/java").absolutePath
            )
        SystemdServiceHelper.install(execPath)
        return
    }

    if (argList.contains("--uninstall-systemd")) {
        SystemdServiceHelper.uninstall()
        return
    }

    // 3. Determine mode: Standalone L3 TUN (CLI) or Privileged IPC Daemon (GUI)
    val hasUrlArg = argList.contains("--url") || argList.any { it.startsWith("whiteproxy://", ignoreCase = true) || it.startsWith("white://", ignoreCase = true) }
    val hasConfigArg = argList.contains("--config")
    val hasDirectFile = argList.any { !it.startsWith("-") && runCatching { File(it).isFile }.getOrDefault(false) }
    val isTunnelExplicit = argList.contains("--tunnel") || argList.contains("--standalone")
    val isTunnelMode = isTunnelExplicit || hasUrlArg || hasConfigArg || hasDirectFile

    val engine = DaemonMultipathEngine()
    val latch = CountDownLatch(1)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown hook triggered. Stopping core...")
        runBlocking {
            engine.stopTunnel()
        }
        latch.countDown()
        logger.info("WhiteRTC Core cleanly terminated.")
    })

    if (isTunnelMode) {
        logger.info("Mode: Standalone CLI Tunnel Mode (L3 TUN via WhiteTunnelEngine)")
        val config = resolveConfig(argList)

        try {
            runBlocking {
                engine.startStandaloneTunnel(config)
            }
            logger.info("WhiteRTC Tunnel is active on tun0! Press Ctrl+C to stop.")
            latch.await()
        } catch (e: InterruptedException) {
            logger.info("Tunnel interrupted.")
        } catch (e: Throwable) {
            logger.error("Tunnel fatal error: ${e.message}", e)
        }
        return
    }

    // 3. Privileged TUN Mode with IPC server (managed by desktopApp GUI)
    logger.info("Mode: Privileged TUN Daemon (IPC Socket Mode for Desktop GUI)")
    val customSocket = if (argList.contains("--socket")) {
        argList.getOrNull(argList.indexOf("--socket") + 1)
    } else null

    val ipcServer = DaemonIpcServer(engine, customSocket)

    try {
        ipcServer.start()
        logger.info("Daemon ready for GUI client connections. Running main loop...")
        latch.await()
    } catch (e: InterruptedException) {
        logger.info("Daemon interrupted.")
    } catch (e: Throwable) {
        logger.error("Daemon fatal error: ${e.message}", e)
    } finally {
        ipcServer.close()
    }
}

private fun resolveConfig(argList: List<String>): WhiteRtcConfig {
    // A. Explicit --url
    if (argList.contains("--url")) {
        val url = argList.getOrNull(argList.indexOf("--url") + 1)
        if (!url.isNullOrBlank()) {
            return WhiteRtcConfig.fromUrl(url)
        }
    }

    // B. Direct argument starting with whiteproxy:// or white://
    val directUrl = argList.firstOrNull { it.startsWith("whiteproxy://", ignoreCase = true) || it.startsWith("white://", ignoreCase = true) }
    if (directUrl != null) {
        return WhiteRtcConfig.fromUrl(directUrl)
    }

    // C. Explicit --config <path>
    if (argList.contains("--config")) {
        val path = argList.getOrNull(argList.indexOf("--config") + 1)
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                val content = file.readText().trim()
                return parseConfigContent(content)
            } else {
                logger.warn("Config file not found: {}, trying parse as raw content", path)
                return parseConfigContent(path)
            }
        }
    }

    // D. Direct file path argument (e.g. Throne %s substitution without flags)
    for (arg in argList) {
        if (!arg.startsWith("-") && File(arg).isFile) {
            val content = File(arg).readText().trim()
            return parseConfigContent(content)
        }
    }

    logger.error("No config URL or file specified.")
    throw IllegalArgumentException("Configuration required. Specify --url <whiteproxy://...>, --config <path>, or configuration file argument.")
}

private fun resolveConfigOrNull(argList: List<String>): WhiteRtcConfig? {
    return try {
        resolveConfig(argList)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun parseConfigContent(content: String): WhiteRtcConfig {
    return if (content.startsWith("whiteproxy://", ignoreCase = true) || content.startsWith("white://", ignoreCase = true)) {
        WhiteRtcConfig.fromUrl(content)
    } else {
        WhiteRtcConfig.fromJson(content)
    }
}
