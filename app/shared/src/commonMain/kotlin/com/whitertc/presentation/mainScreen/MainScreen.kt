package com.whitertc.presentation.mainScreen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.whitertc.presentation.navigation.AppNavigator
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(
    navigator: AppNavigator,
    autoBenchmark: Boolean = false,
    viewModel: MainScreenViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(autoBenchmark) {
        if (autoBenchmark) {
            viewModel.onEvent(MainEvent.RunSpeedTest)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MainEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is MainEffect.ShowResourceMessage -> {
                    val message = getString(effect.res, *effect.args.toTypedArray())
                    snackbarHostState.showSnackbar(message)
                }
                is MainEffect.RequestVpnPermission -> {
                    // Handled by Android platform bridge or VpnService intent
                }
            }
        }
    }

    MainScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSettings = { navigator.navigateToSettings() }
    )
}