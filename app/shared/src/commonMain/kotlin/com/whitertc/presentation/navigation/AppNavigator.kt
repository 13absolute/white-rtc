package com.whitertc.presentation.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavBackStack

@Immutable
class AppNavigator(
    private val backStack: NavBackStack<Route>
) {
    fun navigateBack(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLast()
            true
        } else {
            false
        }
    }

    fun navigateToSettings() {
        backStack.add(Route.Settings)
    }

    fun navigateToSplitTunneling() {
        backStack.add(Route.SplitTunneling)
    }

    fun navigateToRoutingRules() {
        backStack.add(Route.RoutingRules)
    }

    fun navigateToMain() {
        if (backStack.lastOrNull() != Route.Main) {
            backStack.add(Route.Main)
        }
    }
}
