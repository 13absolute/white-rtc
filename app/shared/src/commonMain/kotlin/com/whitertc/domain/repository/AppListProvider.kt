package com.whitertc.domain.repository

import com.whitertc.domain.model.InstalledApp

interface AppListProvider {
    suspend fun getInstalledApps(): List<InstalledApp>
}
