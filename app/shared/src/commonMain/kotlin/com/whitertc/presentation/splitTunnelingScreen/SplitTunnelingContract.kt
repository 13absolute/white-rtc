package com.whitertc.presentation.splitTunnelingScreen

import com.whitertc.domain.model.InstalledApp
import com.whitertc.domain.model.SplitTunnelingMode

data class SplitTunnelingState(
    val mode: SplitTunnelingMode = SplitTunnelingMode.ALL,
    val allApps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true
) {
    val filteredApps: List<InstalledApp>
        get() {
            val baseList = if (showSystemApps) allApps else allApps.filter { !it.isSystem || it.packageName in selectedPackages }
            val searched = if (searchQuery.isBlank()) {
                baseList
            } else {
                baseList.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
            return searched.sortedWith(
                compareBy(
                    { it.packageName !in selectedPackages },
                    { it.name.lowercase() }
                )
            )
        }

    val selectedCount: Int
        get() = selectedPackages.size
}

sealed interface SplitTunnelingEvent {
    data class ModeChanged(val mode: SplitTunnelingMode) : SplitTunnelingEvent
    data class AppToggled(val packageName: String) : SplitTunnelingEvent
    data class SearchQueryChanged(val query: String) : SplitTunnelingEvent
    data class ShowSystemAppsToggled(val show: Boolean) : SplitTunnelingEvent
    data object SelectAllVisibleClicked : SplitTunnelingEvent
    data object DeselectAllClicked : SplitTunnelingEvent
    data object InvertSelectionClicked : SplitTunnelingEvent
}

sealed interface SplitTunnelingEffect {
    data object NavigateBack : SplitTunnelingEffect
    data class ShowMessage(val message: String) : SplitTunnelingEffect
}
