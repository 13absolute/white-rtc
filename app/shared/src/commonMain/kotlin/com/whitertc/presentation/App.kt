package com.whitertc.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.whitertc.presentation.mainScreen.MainScreen
import com.whitertc.presentation.navigation.AppNavigator
import com.whitertc.presentation.navigation.Route
import com.whitertc.presentation.routingRulesScreen.RoutingRulesScreen
import com.whitertc.presentation.settingScreen.SettingScreen
import com.whitertc.presentation.splitTunnelingScreen.SplitTunnelingScreen
import com.whitertc.presentation.theme.WhiteRtcTheme

@Composable
fun App(autoBenchmark: Boolean = false) {
    val configuration = remember {
        SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.Main::class)
                    subclass(Route.Settings::class)
                    subclass(Route.SplitTunneling::class)
                    subclass(Route.RoutingRules::class)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val backStack = rememberNavBackStack(configuration, Route.Main) as NavBackStack<Route>
    val navigator = remember(backStack) { AppNavigator(backStack) }

    WhiteRtcTheme {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { navigator.navigateBack() }
        ) { route ->
            when (route) {
                is Route.Main -> NavEntry(route) {
                    MainScreen(navigator = navigator, autoBenchmark = autoBenchmark)
                }
                is Route.Settings -> NavEntry(route) {
                    SettingScreen(navigator = navigator)
                }
                is Route.SplitTunneling -> NavEntry(route) {
                    SplitTunnelingScreen(navigator = navigator)
                }
                is Route.RoutingRules -> NavEntry(route) {
                    RoutingRulesScreen(navigator = navigator)
                }
            }
        }
    }
}