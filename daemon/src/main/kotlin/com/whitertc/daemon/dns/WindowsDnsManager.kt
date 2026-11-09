package com.whitertc.daemon.dns

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.util.UUID
import org.slf4j.LoggerFactory

class WindowsDnsManager {

    private val logger = LoggerFactory.getLogger(WindowsDnsManager::class.java)
    private val ruleGuid = "{${UUID.randomUUID()}}"

    companion object {
        private const val GPO_NRPT_BASE =
            "SOFTWARE\\Policies\\Microsoft\\Windows NT\\DNSClient\\DnsPolicyConfig"
        private const val SYSTEM_NRPT_BASE =
            "SYSTEM\\CurrentControlSet\\Services\\Dnscache\\Parameters\\DnsPolicyConfig"
        private const val DNS_CLIENT_POLICY =
            "SOFTWARE\\Policies\\Microsoft\\Windows NT\\DNSClient"
    }

    fun configureSecureDns(dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8")) {
        try {
            val serverListJoined = dnsServers.joinToString(";")
            logger.info("Configuring Windows secure DNS with servers: $serverListJoined (NRPT rule: $ruleGuid)")

            // 1. Suppress Smart Multi-Homed Name Resolution (SMHNR) and LLMNR
            runCatching {
                Advapi32Util.registrySetIntValue(
                    WinReg.HKEY_LOCAL_MACHINE, DNS_CLIENT_POLICY, "DisableSmartNameResolution", 1
                )
                Advapi32Util.registrySetIntValue(
                    WinReg.HKEY_LOCAL_MACHINE, DNS_CLIENT_POLICY, "EnableMulticast", 0
                )
            }.onFailure { e ->
                logger.warn("Could not set DisableSmartNameResolution/EnableMulticast: ${e.message}")
            }

            // 2. Mirror NRPT catch-all (".") rules across both GPO and local registry branches
            listOf(GPO_NRPT_BASE, SYSTEM_NRPT_BASE).forEach { basePath ->
                try {
                    val rulePath = "$basePath\\$ruleGuid"
                    if (!Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, rulePath)) {
                        Advapi32Util.registryCreateKey(WinReg.HKEY_LOCAL_MACHINE, rulePath)
                    }
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_LOCAL_MACHINE, rulePath, "ConfigOptions", 0x1)
                    Advapi32Util.registrySetStringArray(WinReg.HKEY_LOCAL_MACHINE, rulePath, "Namespace", arrayOf("."))
                    Advapi32Util.registrySetStringValue(WinReg.HKEY_LOCAL_MACHINE, rulePath, "GenericDNSServers", serverListJoined)
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_LOCAL_MACHINE, rulePath, "Version", 1)
                    Advapi32Util.registrySetIntValue(WinReg.HKEY_LOCAL_MACHINE, rulePath, "Status", 1)
                    logger.debug("Registered NRPT rule at $rulePath")
                } catch (e: Throwable) {
                    logger.warn("Failed to register NRPT rule under $basePath: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to configure secure DNS: ${e.message}", e)
        }
    }

    fun removeSecureDns() {
        try {
            logger.info("Removing Windows secure DNS NRPT rule: $ruleGuid")
            listOf(GPO_NRPT_BASE, SYSTEM_NRPT_BASE).forEach { basePath ->
                try {
                    val rulePath = "$basePath\\$ruleGuid"
                    if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, rulePath)) {
                        Advapi32Util.registryDeleteKey(WinReg.HKEY_LOCAL_MACHINE, rulePath)
                        logger.debug("Deleted NRPT rule at $rulePath")
                    }
                } catch (e: Throwable) {
                    logger.warn("Failed to delete NRPT rule under $basePath: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            logger.error("Error during secure DNS teardown: ${e.message}", e)
        }
    }
}
