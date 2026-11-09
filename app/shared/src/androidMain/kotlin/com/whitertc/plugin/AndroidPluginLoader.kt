package com.whitertc.plugin

import android.content.Context
import android.os.Build
import dalvik.system.PathClassLoader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile
import com.whitertc.core.logging.WhiteLogger
import com.whitertc.core.provider.RelayProvider
import com.whitertc.core.provider.RelayProviderRegistry

/**
 * Dynamic plugin loader for Android runtime.
 * Implements Android 14+ Safer DCL (Dynamic Code Loading) requirements:
 * 1. Storage strictly inside private app directories.
 * 2. Immutable read-only file mode (file.setReadOnly()) before PathClassLoader instantiation.
 * 3. Extraction of architecture-matched native libraries (.so).
 * 4. Reflection instantiation and registration into RelayProviderRegistry.
 */
class AndroidPluginLoader(private val context: Context) {

    companion object {
        private const val TAG = "AndroidPluginLoader"
        private const val PLUGINS_DIR = "relay_plugins"
        private const val NATIVE_LIBS_DIR = "native_libs"
        private const val SPI_DESCRIPTOR_PATH = "META-INF/services/com.whitertc.core.provider.RelayProvider"
        private val providerFileMap = mutableMapOf<String, File>()
    }

    /**
     * Resolves provider entry classes from the APK's META-INF/services descriptor.
     */
    private fun resolveEntryClasses(apkFile: File): List<String> {
        val classNames = mutableListOf<String>()
        runCatching {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(SPI_DESCRIPTOR_PATH) ?: return@use
                zip.getInputStream(entry).bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { classNames.add(it) }
                }
            }
        }.onFailure { e ->
            WhiteLogger.w(TAG) { "Failed to read SPI descriptor from ${apkFile.name}: ${e.message}" }
        }
        return classNames
    }

    /**
     * Loads a plugin APK from an input stream (e.g. from app assets, content URI, or downloaded file),
     * enforces Safer DCL constraints, instantiates the entry provider, and registers it.
     * If entryClassName is not specified, dynamically resolves it from META-INF/services/ inside the APK.
     */
    @Synchronized
    fun installAndLoadPlugin(
        inputStream: InputStream,
        targetFileName: String = "plugin_${System.currentTimeMillis()}.apk",
        entryClassName: String? = null
    ): Result<RelayProvider> = runCatching {
        val storageDir = File(context.filesDir, PLUGINS_DIR).apply { mkdirs() }
        val nativeLibsDir = File(storageDir, NATIVE_LIBS_DIR).apply { mkdirs() }
        val targetApk = File(storageDir, targetFileName)

        if (targetApk.exists()) {
            targetApk.delete()
        }

        // Write file and enforce Android 14+ read-only requirement
        FileOutputStream(targetApk).use { output ->
            inputStream.copyTo(output)
            output.flush()
        }

        if (!targetApk.setReadOnly() && targetApk.canWrite()) {
            throw SecurityException("Failed to mark plugin file read-only: ${targetApk.absolutePath}")
        }

        // Extract native libraries if any
        extractNativeLibs(targetApk, nativeLibsDir)

        val classNames = if (!entryClassName.isNullOrBlank()) {
            listOf(entryClassName)
        } else {
            val discovered = resolveEntryClasses(targetApk)
            if (discovered.isEmpty()) {
                throw IllegalArgumentException("Plugin ${targetApk.name} does not contain descriptor $SPI_DESCRIPTOR_PATH and entry class is not specified")
            }
            discovered
        }

        // Instantiate PathClassLoader
        val classLoader = PathClassLoader(
            targetApk.absolutePath,
            nativeLibsDir.absolutePath,
            context.classLoader
        )

        var lastProvider: RelayProvider? = null
        for (className in classNames) {
            val loadedClass = classLoader.loadClass(className)
            val constructor = loadedClass.getDeclaredConstructor()
            val rawInstance = constructor.newInstance()

            val provider = rawInstance as? RelayProvider
                ?: throw ClassCastException("Class $className does not implement RelayProvider")

            RelayProviderRegistry.register(provider)
            providerFileMap[provider.providerId] = targetApk
            WhiteLogger.i(TAG) { "Successfully loaded and registered plugin: ${provider.providerId} (${provider.displayName})" }
            lastProvider = provider
        }

        lastProvider ?: throw IllegalStateException("Failed to initialize RelayProvider from ${targetApk.name}")
    }

    /**
     * Loads a plugin from an already installed file in private storage.
     * If entryClassName is not specified, dynamically resolves it from META-INF/services/ inside the APK.
     */
    @Synchronized
    fun loadExistingPlugin(
        targetFileName: String,
        entryClassName: String? = null
    ): Result<RelayProvider> = runCatching {
        val storageDir = File(context.filesDir, PLUGINS_DIR)
        val nativeLibsDir = File(storageDir, NATIVE_LIBS_DIR)
        val targetApk = File(storageDir, targetFileName)

        if (!targetApk.exists()) {
            throw IllegalArgumentException("Plugin APK not found: ${targetApk.absolutePath}")
        }

        if (targetApk.canWrite()) {
            targetApk.setReadOnly()
        }

        val classNames = if (!entryClassName.isNullOrBlank()) {
            listOf(entryClassName)
        } else {
            val discovered = resolveEntryClasses(targetApk)
            if (discovered.isEmpty()) {
                throw IllegalArgumentException("Plugin ${targetApk.name} does not contain descriptor $SPI_DESCRIPTOR_PATH and entry class is not specified")
            }
            discovered
        }

        val classLoader = PathClassLoader(
            targetApk.absolutePath,
            nativeLibsDir.absolutePath,
            context.classLoader
        )

        var lastProvider: RelayProvider? = null
        for (className in classNames) {
            val loadedClass = classLoader.loadClass(className)
            val constructor = loadedClass.getDeclaredConstructor()
            val rawInstance = constructor.newInstance()

            val provider = rawInstance as? RelayProvider
                ?: throw ClassCastException("Class $className does not implement RelayProvider")

            RelayProviderRegistry.register(provider)
            providerFileMap[provider.providerId] = targetApk
            WhiteLogger.i(TAG) { "Loaded existing plugin: ${provider.providerId}" }
            lastProvider = provider
        }

        lastProvider ?: throw IllegalStateException("Failed to initialize RelayProvider from ${targetApk.name}")
    }

    /**
     * Scans the internal storage directory for all installed plugin APKs and loads them.
     */
    @Synchronized
    fun loadAllInstalledPlugins(): List<RelayProvider> {
        val storageDir = File(context.filesDir, PLUGINS_DIR)
        if (!storageDir.exists() || !storageDir.isDirectory) return emptyList()
        val apks = storageDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
            ?: return emptyList()

        val loaded = mutableListOf<RelayProvider>()
        for (apk in apks) {
            loadExistingPlugin(apk.name).onSuccess {
                loaded.add(it)
            }.onFailure { e ->
                WhiteLogger.w(TAG) { "Failed to load plugin ${apk.name}: ${e.message}" }
            }
        }
        return loaded
    }

    /**
     * Unregisters a provider from RelayProviderRegistry and removes its APK file from internal storage.
     */
    @Synchronized
    fun deletePlugin(providerId: String): Boolean {
        val storageDir = File(context.filesDir, PLUGINS_DIR)
        val file = providerFileMap.remove(providerId) ?: run {
            storageDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
                ?.firstOrNull { apk ->
                    loadExistingPlugin(apk.name).getOrNull()?.providerId == providerId
                }
        }
        RelayProviderRegistry.unregister(providerId)
        if (file != null && file.exists()) {
            file.setWritable(true)
            val deleted = file.delete()
            WhiteLogger.i(TAG) { "Deleted plugin file: ${file.name} (success=$deleted)" }
            return deleted
        }
        return true
    }

    private fun extractNativeLibs(apkFile: File, outputDir: File) {
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        ZipFile(apkFile).use { zip ->
            for (abi in supportedAbis) {
                val libPrefix = "lib/$abi/"
                val entries = zip.entries().asSequence()
                    .filter { it.name.startsWith(libPrefix) && it.name.endsWith(".so") }
                    .toList()

                if (entries.isNotEmpty()) {
                    for (entry in entries) {
                        val libName = entry.name.substringAfterLast('/')
                        val outFile = File(outputDir, libName)
                        if (outFile.exists()) outFile.delete()

                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        outFile.setReadOnly()
                    }
                    break
                }
            }
        }
    }
}
