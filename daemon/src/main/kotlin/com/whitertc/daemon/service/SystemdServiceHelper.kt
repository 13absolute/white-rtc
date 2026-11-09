package com.whitertc.daemon.service

import java.io.File
import org.slf4j.LoggerFactory

object SystemdServiceHelper {
    private val logger = LoggerFactory.getLogger(SystemdServiceHelper::class.java)

    private const val SERVICE_PATH = "/etc/systemd/system/whiteproxy.service"

    fun install(execPath: String) {
        val unit = """
            [Unit]
            Description=WhiteRTC Multipath VPN Privileged Daemon
            After=network.target network-online.target
            Wants=network-online.target

            [Service]
            Type=simple
            Environment="JAVA_HOME=${System.getProperty("java.home")}"
            Environment="PATH=${System.getProperty("java.home")}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            Environment="JAVA_OPTS=--add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --enable-native-access=ALL-UNNAMED"
            ExecStart="$execPath"
            Restart=always
            RestartSec=1s
            AmbientCapabilities=CAP_NET_ADMIN CAP_NET_RAW CAP_NET_BIND_SERVICE
            CapabilityBoundingSet=CAP_NET_ADMIN CAP_NET_RAW CAP_NET_BIND_SERVICE
            RuntimeDirectory=whiteproxy
            RuntimeDirectoryMode=0777

            [Install]
            WantedBy=multi-user.target
        """.trimIndent()

        val serviceFile = File(SERVICE_PATH)
        logger.info("Writing systemd unit to $SERVICE_PATH...")
        serviceFile.writeText(unit)

        logger.info("Reloading systemd daemon and enabling whiteproxy service...")
        runCommand("systemctl", "daemon-reload")
        runCommand("systemctl", "enable", "--now", "whiteproxy")
        logger.info("WhiteRTC daemon installed and started via systemd!")
    }

    fun uninstall() {
        logger.info("Stopping and disabling whiteproxy service...")
        runCatching { runCommand("systemctl", "stop", "whiteproxy") }
        runCatching { runCommand("systemctl", "disable", "whiteproxy") }
        File(SERVICE_PATH).delete()
        runCatching { runCommand("systemctl", "daemon-reload") }
        logger.info("WhiteRTC systemd service uninstalled.")
    }

    private fun runCommand(vararg args: String) {
        val proc = ProcessBuilder(*args).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        if (code != 0) {
            logger.warn("systemctl command failed ($code): $out")
        }
    }
}
