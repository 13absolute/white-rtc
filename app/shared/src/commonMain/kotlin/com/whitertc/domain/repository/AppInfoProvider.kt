package com.whitertc.domain.repository

interface AppInfoProvider {
    val appVersion: String
    val buildNumber: String
    val platformName: String
    val isDesktop: Boolean get() = false
}
