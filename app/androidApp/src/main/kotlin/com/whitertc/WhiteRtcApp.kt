package com.whitertc

import android.app.Application
import com.whitertc.di.AppModule
import com.whitertc.domain.repository.AppLogManager
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.plugin.AndroidPluginLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.KoinApplication
import org.koin.core.logger.Level
import org.koin.plugin.module.dsl.startKoin

@KoinApplication(modules = [AppModule::class])
class WhiteRtcApp : Application() {

    private val logManager: AppLogManager by inject()
    private val configRepository: VpnConfigRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin<WhiteRtcApp> {
            androidLogger(level = Level.DEBUG)
            androidContext(this@WhiteRtcApp)
        }
        logManager.initialize()
        CoroutineScope(Dispatchers.IO).launch {
            val config = configRepository.getConfig()
            logManager.setDebugEnabled(config.debugLoggingEnabled)
        }
        AndroidPluginLoader(this).loadAllInstalledPlugins()
    }
}
