package com.whitertc.proxy

import com.whitertc.domain.repository.AppInfoProvider
import org.koin.core.annotation.Single

@Single
class DesktopAppInfoProvider : AppInfoProvider {
    override val appVersion: String get() = "1.0.0"
    override val buildNumber: String get() = "1"
    override val platformName: String get() = "Desktop (${System.getProperty("os.name")})"
    override val isDesktop: Boolean get() = true
}
