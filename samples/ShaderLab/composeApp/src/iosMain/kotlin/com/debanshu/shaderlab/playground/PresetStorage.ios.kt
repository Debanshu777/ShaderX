package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberPresetStorage(slot: String): PresetStorage = remember(slot) {
    storageLimit(slot)
    object : PresetStorage {
        override val supportsFiles = false
        override fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey("shaderlab.$slot.v1")
        override fun save(text: String) { require(text.encodeToByteArray().size <= storageLimit(slot)); NSUserDefaults.standardUserDefaults.setObject(text, forKey = "shaderlab.$slot.v1") }
        override fun exportFile(text: String) = error("Use selectable preset text on iOS")
        override fun importFile(onResult: (Result<String>) -> Unit) = onResult(Result.failure(IllegalStateException("Paste preset text on iOS")))
    }
}
