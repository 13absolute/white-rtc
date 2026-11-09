package com.whitertc.proxy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.whitertc.domain.model.InstalledApp
import com.whitertc.domain.repository.AppListProvider
import org.koin.core.annotation.Single

@Single
class AndroidAppListProvider(
    private val context: Context
) : AppListProvider {

    @Volatile
    private var cachedApps: List<InstalledApp>? = null

    override suspend fun getInstalledApps(): List<InstalledApp> {
        cachedApps?.let { return it }

        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val myPackageName = context.packageName

            val installedList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != myPackageName }
                .map { appInfo ->
                    val isSystemFlag = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isLaunchable = pm.getLaunchIntentForPackage(appInfo.packageName) != null

                    // Only pure non-launchable background system daemons are marked as isSystem = true
                    val isSystem = isSystemFlag && !isUpdatedSystem && !isLaunchable

                    val icon = runCatching {
                        appInfo.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
                    }.getOrNull()

                    val label = runCatching {
                        appInfo.loadLabel(pm).toString()
                    }.getOrDefault(appInfo.packageName)

                    InstalledApp(
                        name = label,
                        packageName = appInfo.packageName,
                        icon = icon,
                        isSystem = isSystem
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()

            cachedApps = installedList
            installedList
        }
    }
}
