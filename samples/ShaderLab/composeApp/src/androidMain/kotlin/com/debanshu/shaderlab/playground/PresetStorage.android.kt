package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPresetStorage(slot: String): PresetStorage {
    storageLimit(slot)
    val context = LocalContext.current
    return remember(context, slot) {
        object : PresetStorage {
            private val key = if (slot == "preset") "v1" else "$slot.v1"
            private val prefs = context.getSharedPreferences("shaderlab_presets", 0)
            override val supportsFiles = false
            override fun load(): String? = prefs.getString(key, null)
            override fun save(text: String) { require(text.encodeToByteArray().size <= storageLimit(slot)); check(prefs.edit().putString(key, text).commit()) { "Local preset save failed" } }
            override fun exportFile(text: String) = error("Use selectable preset text on Android")
            override fun importFile(onResult: (Result<String>) -> Unit) = onResult(Result.failure(IllegalStateException("Paste preset text on Android")))
        }
    }
}
