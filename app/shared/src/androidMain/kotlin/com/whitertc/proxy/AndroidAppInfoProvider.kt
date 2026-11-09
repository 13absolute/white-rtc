package com.whitertc.proxy

import android.content.Context
import android.os.Build
import com.whitertc.domain.repository.AppInfoProvider
import org.koin.core.annotation.Single

@Single
class AndroidAppInfoProvider(private val context: Context) : AppInfoProvider {
    override val appVersion: String
        get() = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

    override val buildNumber: String
        get() = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toString()
            }
        } catch (_: Exception) {
            "1"
        }

    override val platformName: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
