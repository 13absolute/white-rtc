package com.whitertc.desktop.di

import com.whitertc.desktop.controller.DesktopVpnController
import com.whitertc.di.AppModule
import com.whitertc.domain.repository.TurnSignalingRepository
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.domain.repository.VpnStateRepository
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(
    includes = [
        AppModule::class
    ]
)
class DesktopAppModule {

    @Single
    fun provideDesktopVpnController(
        vpnStateRepository: VpnStateRepository,
        vpnConfigRepository: VpnConfigRepository,
        turnSignalingRepository: TurnSignalingRepository
    ): DesktopVpnController {
        return DesktopVpnController(
            vpnStateRepository = vpnStateRepository,
            vpnConfigRepository = vpnConfigRepository,
            turnSignalingRepository = turnSignalingRepository
        )
    }
}

@KoinApplication(modules = [DesktopAppModule::class])
object DesktopKoinAppConfig
