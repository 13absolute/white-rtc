package com.whitertc.desktop.controller

import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object PrivilegeElevationLauncher {

    private val logger = LoggerFactory.getLogger(PrivilegeElevationLauncher::class.java)

    suspend fun elevateAndStartDaemon(
        onProgress: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        when {
            os.contains("linux") -> elevateLinux(onProgress)
            os.contains("win") -> elevateWindows(onProgress)
            else -> {
                logger.error("Unsupported OS for auto-elevation: $os")
                false
            }
        }
    }

    private suspend fun elevateLinux(onProgress: (String) -> Unit): Boolean {
        // 0. If socket already exists, daemon is already up
        if (hasSocket()) {
            logger.info("Daemon socket is already active.")
            return true
        }

        onProgress("Checking WhiteRTC Daemon status...")
        logger.info("Attempting Linux privilege elevation for WhiteRTC Daemon...")

        // 1. If service exists in systemd, restart it (kills any zombie and recreates socket)
        val hasServiceFile = File("/etc/systemd/system/whiteproxy.service").exists()
        if (hasServiceFile) {
            onProgress("Restarting whiteproxy system service...")
            val started = runElevatedCommand(
                onProgress = onProgress,
                description = "Restarting whiteproxy system service",
                command = arrayOf("systemctl", "restart", "whiteproxy")
            )
            if (started && waitForSocket(5)) {
                logger.info("whiteproxy service successfully restarted!")
                return true
            } else {
                logger.warn("Failed to restart whiteproxy service or socket did not appear.")
            }
        }

        // 2. Find daemon binary
        val daemonBin = findDaemonBinary(onProgress)
        if (daemonBin == null) {
            logger.error("Could not find or build daemon binary.")
            return false
        }

        val absDaemonBin = daemonBin.absolutePath
        logger.info("Found daemon binary at: $absDaemonBin")

        // 3. Install and enable systemd service via elevation
        onProgress("Installing and starting whiteproxy system service...")
        val installed = runElevatedCommand(
            onProgress = onProgress,
            description = "Installing WhiteRTC Daemon system service",
            command = arrayOf(absDaemonBin, "--install-systemd", absDaemonBin)
        )

        if (installed) {
            logger.info("whiteproxy successfully installed and started as systemd service!")
            return waitForSocket()
        }

        // 4. Fallback: run daemon directly via elevation if systemd install was rejected
        logger.info("Systemd install failed or cancelled, trying direct daemon execution...")
        onProgress("Directly starting WhiteRTC daemon...")
        return runDirectDaemon(absDaemonBin, onProgress)
    }

    fun findDaemonBinary(onProgress: (String) -> Unit = {}): File? {
        val userDir = File(System.getProperty("user.dir", "."))
        val candidateBinaries = listOf(
            File(userDir, "daemon/build/install/daemon/bin/daemon"),
            File(userDir.parentFile ?: userDir, "daemon/build/install/daemon/bin/daemon"),
            File("/mnt/dev_nvme/dev/White Proxy/daemon/build/install/daemon/bin/daemon"),
            File("/mnt/dev_nvme/dev/WhiteRTC/daemon/build/install/daemon/bin/daemon"),
            File("/opt/whiteproxy/bin/daemon"),
            File("/usr/local/bin/daemon")
        )

        val existing = candidateBinaries.firstOrNull { it.exists() && it.canExecute() }
        if (existing != null) return existing

        logger.info("Daemon binary not found in candidate paths, compiling via :daemon:installDist...")
        onProgress("Compiling daemon binary (:daemon:installDist)...")

        val gradlewFiles = listOf(
            File(userDir, "gradlew"),
            File(userDir.parentFile ?: userDir, "gradlew"),
            File("/mnt/dev_nvme/dev/White Proxy/gradlew"),
            File("/mnt/dev_nvme/dev/WhiteRTC/gradlew")
        )
        val gradlew = gradlewFiles.firstOrNull { it.exists() } ?: return null

        return runCatching {
            val buildProc = ProcessBuilder(gradlew.absolutePath, ":daemon:installDist")
                .directory(gradlew.parentFile)
                .redirectErrorStream(true)
                .start()
            val code = buildProc.waitFor()
            if (code == 0) {
                candidateBinaries.firstOrNull { it.exists() && it.canExecute() }
            } else {
                null
            }
        }.getOrNull()
    }

    fun getDaemonBinaryTimestamp(): Long? {
        val binary = findDaemonBinary() ?: return null
        val jar = File(binary.parentFile?.parentFile, "lib/daemon-1.0.0.jar")
        return if (jar.exists()) jar.lastModified() else binary.lastModified()
    }

    private fun runElevatedCommand(
        onProgress: (String) -> Unit,
        description: String,
        vararg command: String
    ): Boolean {
        // Attempt 1: pkexec (native desktop Polkit prompt)
        val hasPkexec = File("/usr/bin/pkexec").exists()
        if (hasPkexec) {
            logger.info("Running via pkexec: ${command.joinToString(" ")}")
            onProgress("$description (Polkit)...")
            val proc = runCatching {
                ProcessBuilder("pkexec", *command)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull()

            if (proc != null) {
                val code = proc.waitFor()
                if (code == 0) {
                    logger.info("pkexec command succeeded ($code)")
                    return true
                }
                logger.warn("pkexec exited with code $code")
            }
        }

        // Attempt 2: zenity password prompt fallback -> sudo -S
        val hasZenity = File("/usr/bin/zenity").exists()
        if (hasZenity) {
            logger.info("Attempting zenity password prompt fallback...")
            onProgress("Requesting sudo password...")
            val password = runCatching {
                val zProc = ProcessBuilder(
                    "zenity",
                    "--password",
                    "--title=WhiteRTC VPN",
                    "--text=Superuser (root) privileges are required to configure network routes:"
                ).start()
                val pass = zProc.inputStream.bufferedReader().readLine()?.trim()
                val zCode = zProc.waitFor()
                if (zCode == 0 && !pass.isNullOrBlank()) pass else null
            }.getOrNull()

            if (!password.isNullOrBlank()) {
                val sudoProc = ProcessBuilder("sudo", "-S", *command)
                    .redirectErrorStream(true)
                    .start()
                sudoProc.outputStream.bufferedWriter().use {
                    it.write(password)
                    it.newLine()
                    it.flush()
                }
                val sudoCode = sudoProc.waitFor()
                if (sudoCode == 0) {
                    logger.info("sudo -S command succeeded ($sudoCode)")
                    return true
                }
                logger.warn("sudo -S failed with code $sudoCode")
            }
        }

        return false
    }

    private suspend fun runDirectDaemon(absDaemonBin: String, onProgress: (String) -> Unit): Boolean {
        // Direct launch in background via pkexec
        if (File("/usr/bin/pkexec").exists()) {
            onProgress("Starting daemon in background via pkexec...")
            val proc = runCatching {
                ProcessBuilder("pkexec", absDaemonBin)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull()
            if (proc != null) {
                return waitForSocket()
            }
        }
        return false
    }

    private suspend fun elevateWindows(onProgress: (String) -> Unit): Boolean {
        onProgress("Requesting Windows Administrator privileges (UAC)...")
        val userDir = File(System.getProperty("user.dir", "."))
        val candidateBatch = listOf(
            File(userDir, "daemon/build/install/daemon/bin/daemon.bat"),
            File(userDir.parentFile ?: userDir, "daemon/build/install/daemon/bin/daemon.bat"),
            File("C:\\Program Files\\WhiteProxy\\white-proxy-daemon.bat")
        ).firstOrNull { it.exists() } ?: return false

        logger.info("Launching Windows daemon with UAC elevation: ${candidateBatch.absolutePath}")
        val proc = ProcessBuilder(
            "powershell",
            "-Command",
            "Start-Process",
            "\"${candidateBatch.absolutePath}\"",
            "-Verb",
            "RunAs",
            "-WindowStyle",
            "Hidden"
        ).start()
        proc.waitFor()
        return waitForSocket()
    }

    private fun hasSocket(): Boolean {
        val paths = listOf("/run/whiteproxy/daemon.sock", "/tmp/whiteproxy_daemon.sock")
        for (p in paths) {
            val file = File(p)
            if (!file.exists()) continue
            try {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
                    ch.configureBlocking(true)
                    ch.connect(UnixDomainSocketAddress.of(p))
                    return true
                }
            } catch (_: Exception) {
                // Stale dead socket: process not listening, delete dead file
                file.delete()
            }
        }
        return false
    }

    private suspend fun waitForSocket(timeoutSec: Int = 10): Boolean {
        val deadline = System.currentTimeMillis() + (timeoutSec * 1000L)
        while (System.currentTimeMillis() < deadline) {
            if (hasSocket()) {
                logger.info("Daemon socket detected!")
                delay(300)
                return true
            }
            delay(250)
        }
        logger.warn("Daemon socket did not appear within $timeoutSec seconds.")
        return false
    }
}
