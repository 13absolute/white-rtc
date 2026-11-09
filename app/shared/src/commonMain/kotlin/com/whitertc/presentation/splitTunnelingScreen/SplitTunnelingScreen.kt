package com.whitertc.presentation.splitTunnelingScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.whitertc.presentation.navigation.AppNavigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SplitTunnelingScreen(
    navigator: AppNavigator,
    viewModel: SplitTunnelingScreenViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SplitTunnelingEffect.NavigateBack -> {
                    navigator.navigateBack()
                }
                is SplitTunnelingEffect.ShowMessage -> {}
            }
        }
    }

    SplitTunnelingScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = { navigator.navigateBack() }
    )
}
