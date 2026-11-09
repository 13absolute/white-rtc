package com.whitertc.presentation.routingRulesScreen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.getString
import com.whitertc.presentation.navigation.AppNavigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RoutingRulesScreen(
    navigator: AppNavigator,
    viewModel: RoutingRulesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is RoutingRulesEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is RoutingRulesEffect.ShowResourceMessage -> {
                    val text = getString(effect.res, *effect.args.toTypedArray())
                    snackbarHostState.showSnackbar(text)
                }
                is RoutingRulesEffect.NavigateBack -> {
                    navigator.navigateBack()
                }
            }
        }
    }

    RoutingRulesScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = { navigator.navigateBack() },
        snackbarHostState = snackbarHostState
    )
}
