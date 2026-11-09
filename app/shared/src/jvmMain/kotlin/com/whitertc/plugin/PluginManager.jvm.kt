package com.whitertc.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.whitertc.core.plugin.JvmPluginLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberPluginInstaller(
    onPluginInstalled: (Result<String>) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch(Dispatchers.IO) {
                try {
                    val dialog = FileDialog(null as Frame?, "Select WhiteRTC plugin (.jar, .apk)", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        name.endsWith(".jar", ignoreCase = true) ||
                        name.endsWith(".apk", ignoreCase = true) ||
                        name.endsWith(".wtp", ignoreCase = true)
                    }
                    dialog.isVisible = true
                    val file = dialog.file
                    val dir = dialog.directory
                    if (file != null && dir != null) {
                        val selected = File(dir, file)
                        val loaded = JvmPluginLoader.installPlugin(selected).getOrThrow()
                        val names = loaded.joinToString(", ") { it.displayName }
                        withContext(Dispatchers.Main) {
                            onPluginInstalled(Result.success(names.ifEmpty { file }))
                        }
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        onPluginInstalled(Result.failure(e))
                    }
                }
            }
        }
    }
}
