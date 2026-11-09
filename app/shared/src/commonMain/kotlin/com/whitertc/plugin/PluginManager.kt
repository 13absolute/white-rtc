package com.whitertc.plugin

import androidx.compose.runtime.Composable
import com.whitertc.core.provider.DirectWebRtcProvider
import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.core.provider.StandardTurnRelayProvider

data class PluginUiModel(
    val id: String,
    val displayName: String,
    val badgeLabel: String,
    val isBuiltIn: Boolean
)

object PluginRegistryHelper {
    fun getInstalledPlugins(): List<PluginUiModel> {
        return RelayProviderRegistry.allProviders()
            .filter { it != DirectWebRtcProvider }
            .map { provider ->
                PluginUiModel(
                    id = provider.providerId,
                    displayName = provider.displayName,
                    badgeLabel = provider.badgeLabel,
                    isBuiltIn = provider == StandardTurnRelayProvider
                )
            }
    }
}

/**
 * Platform-specific file picker launcher that returns a trigger lambda.
 * On JVM: opens AWT FileDialog for .jar files.
 * On Android: launches ActivityResultContracts.GetContent for .apk files.
 */
@Composable
expect fun rememberPluginInstaller(
    onPluginInstalled: (Result<String>) -> Unit
): () -> Unit
