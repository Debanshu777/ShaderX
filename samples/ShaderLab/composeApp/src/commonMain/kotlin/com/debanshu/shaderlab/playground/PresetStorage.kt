package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable

/** Isolated bounded preset/workbench slots; files are exchanged only after a user gesture. */
interface PresetStorage {
    val supportsFiles: Boolean
    fun load(): String?
    fun save(text: String)
    fun exportFile(text: String)
    fun importFile(onResult: (Result<String>) -> Unit)
}

@Composable
expect fun rememberPresetStorage(slot: String = "preset"): PresetStorage

internal fun storageLimit(slot: String): Int {
    require(slot == "preset" || slot == "workbench") { "Unknown local storage slot" }
    return if (slot == "workbench") WorkbenchLimits.PROJECT_BYTES else PresetCodec.MAX_BYTES
}
