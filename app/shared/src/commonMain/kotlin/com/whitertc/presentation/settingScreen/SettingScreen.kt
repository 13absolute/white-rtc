package com.whitertc.presentation.settingScreen

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
fun SettingScreen(
    navigator: AppNavigator,
    viewModel: SettingScreenViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SettingEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is SettingEffect.ShowResourceMessage -> {
                    val message = getString(effect.res, *effect.args.toTypedArray())
                    snackbarHostState.showSnackbar(message)
                }
                is SettingEffect.NavigateBack -> {
                    navigator.navigateBack()
                }
            }
        }
    }

    SettingScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = { navigator.navigateBack() },
        onNavigateToSplitTunneling = { navigator.navigateToSplitTunneling() },
        onNavigateToRoutingRules = { navigator.navigateToRoutingRules() },
        snackbarHostState = snackbarHostState
    )
}