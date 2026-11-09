package com.whitertc.proxy

import com.whitertc.domain.model.InstalledApp
import com.whitertc.domain.repository.AppListProvider
import org.koin.core.annotation.Single

@Single
class DesktopAppListProvider : AppListProvider {

    override suspend fun getInstalledApps(): List<InstalledApp> {
        return listOf(
            InstalledApp("Telegram Desktop", "org.telegram.messenger", null, false),
            InstalledApp("Google Chrome", "com.google.android.chrome", null, false),
            InstalledApp("YouTube", "com.google.android.youtube", null, false),
            InstalledApp("Spotify", "com.spotify.music", null, false)
        )
    }
}
