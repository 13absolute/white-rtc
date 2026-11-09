package com.whitertc.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.whitertc.core.plugin.JvmPluginLoader
import com.whitertc.desktop.controller.DesktopVpnController
import com.whitertc.desktop.di.DesktopKoinAppConfig
import com.whitertc.presentation.App
import com.whitertc.resources.Res
import com.whitertc.resources.app_icon
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.koin.plugin.module.dsl.startKoin

fun main() = application {
    val koinApp = startKoin<DesktopKoinAppConfig> {}
    val controller = koinApp.koin.get<DesktopVpnController>()
    JvmPluginLoader.loadDefaultPlugins()

    val shutdownHook = Thread({
        runBlocking {
            runCatching { controller.disconnectAndClose() }
        }
    }, "whitertc-shutdown-hook")
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val windowState = rememberWindowState(
        width = 410.dp,
        height = 740.dp
    )

    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = {
            coroutineScope.launch {
                runCatching { controller.disconnectAndClose() }
                runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
                exitApplication()
            }
        },
        title = "WhiteRTC",
        state = windowState,
        resizable = false,
        icon = painterResource(Res.drawable.app_icon)
    ) {
        LaunchedEffect(controller) {
            controller.start()
        }
        App()
    }
}
