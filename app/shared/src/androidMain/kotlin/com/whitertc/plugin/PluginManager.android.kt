package com.whitertc.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
actual fun rememberPluginInstaller(
    onPluginInstalled: (Result<String>) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Failed to read selected file")
                    val fileName = "plugin_${System.currentTimeMillis()}.apk"
                    val loader = AndroidPluginLoader(context)
                    val provider = loader.installAndLoadPlugin(inputStream, fileName).getOrThrow()
                    withContext(Dispatchers.Main) {
                        onPluginInstalled(Result.success(provider.displayName))
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        onPluginInstalled(Result.failure(e))
                    }
                }
            }
        }
    }
    return remember { { launcher.launch("*/*") } }
}
