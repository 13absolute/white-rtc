package com.whitertc.presentation.splitTunnelingScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.whitertc.domain.model.SplitTunnelingMode
import com.whitertc.domain.repository.AppListProvider
import com.whitertc.domain.usecase.GetVpnConfigUseCase
import com.whitertc.domain.usecase.SaveVpnConfigUseCase
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SplitTunnelingScreenViewModel(
    private val getVpnConfigUseCase: GetVpnConfigUseCase,
    private val saveVpnConfigUseCase: SaveVpnConfigUseCase,
    private val appListProvider: AppListProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SplitTunnelingState())
    val state: StateFlow<SplitTunnelingState> = _state.asStateFlow()

    private val _effect = Channel<SplitTunnelingEffect>(Channel.BUFFERED)
    val effect: Flow<SplitTunnelingEffect> = _effect.receiveAsFlow()

    private var saveDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            val config = getVpnConfigUseCase()
            _state.update {
                it.copy(
                    mode = config.splitTunnelingMode,
                    selectedPackages = config.splitTunnelingPackages
                )
            }

            val apps = appListProvider.getInstalledApps()
            _state.update {
                it.copy(
                    allApps = apps,
                    isLoading = false
                )
            }
        }
    }

    fun onEvent(event: SplitTunnelingEvent) {
        when (event) {
            is SplitTunnelingEvent.ModeChanged -> {
                _state.update { it.copy(mode = event.mode) }
                scheduleSave()
            }
            is SplitTunnelingEvent.AppToggled -> {
                val current = _state.value.selectedPackages
                val updated = if (event.packageName in current) {
                    current - event.packageName
                } else {
                    current + event.packageName
                }
                _state.update { it.copy(selectedPackages = updated) }
                scheduleSave()
            }
            is SplitTunnelingEvent.SearchQueryChanged -> {
                _state.update { it.copy(searchQuery = event.query) }
            }
            is SplitTunnelingEvent.ShowSystemAppsToggled -> {
                _state.update { it.copy(showSystemApps = event.show) }
            }
            is SplitTunnelingEvent.SelectAllVisibleClicked -> {
                val visiblePackageNames = _state.value.filteredApps.map { it.packageName }.toSet()
                val updated = _state.value.selectedPackages + visiblePackageNames
                _state.update { it.copy(selectedPackages = updated) }
                scheduleSave()
            }
            is SplitTunnelingEvent.DeselectAllClicked -> {
                _state.update { it.copy(selectedPackages = emptySet()) }
                scheduleSave()
            }
            is SplitTunnelingEvent.InvertSelectionClicked -> {
                val current = _state.value.selectedPackages
                val visible = _state.value.filteredApps.map { it.packageName }.toSet()
                val inverted = visible.mapNotNull { pkg ->
                    if (pkg in current) null else pkg
                }.toSet()
                val remainder = current - visible
                val updated = remainder + inverted
                _state.update { it.copy(selectedPackages = updated) }
                scheduleSave()
            }
        }
    }

    private fun scheduleSave() {
        saveDebounceJob?.cancel()
        saveDebounceJob = viewModelScope.launch {
            delay(250)
            val currentState = _state.value
            val currentConfig = getVpnConfigUseCase()
            saveVpnConfigUseCase(
                currentConfig.copy(
                    splitTunnelingMode = currentState.mode,
                    splitTunnelingPackages = currentState.selectedPackages
                )
            )
        }
    }
}
