package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable
actual fun rememberPresetStorage(slot: String): PresetStorage = remember(slot) {
    storageLimit(slot)
    object : PresetStorage {
        private val local = Path.of(System.getProperty("user.home"), ".shaderlab", "$slot.sxp")
        override val supportsFiles = true
        private fun read(path: Path): String {
            require(Files.size(path) <= storageLimit(slot)) { "Preset file is too large" }
            val bytes = Files.newInputStream(path).use { it.readNBytes(storageLimit(slot) + 1) }
            require(bytes.size <= storageLimit(slot)) { "Preset file is too large" }
            return bytes.decodeToString(throwOnInvalidSequence = true)
        }
        override fun load(): String? = if (Files.exists(local)) read(local) else null
        override fun save(text: String) {
            require(text.encodeToByteArray().size <= storageLimit(slot))
            Files.createDirectories(local.parent)
            Files.writeString(local, text)
        }
        override fun exportFile(text: String) {
            require(text.encodeToByteArray().size <= storageLimit(slot))
            val chooser = JFileChooser().apply { selectedFile = java.io.File(if (slot == "workbench") "shaderlab-draft.sxw" else "shaderlab.sxp") }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                if (!file.exists() || javax.swing.JOptionPane.showConfirmDialog(null, "Replace ${file.name}?", "Export preset", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION) Files.writeString(file.toPath(), text)
            }
        }
        override fun importFile(onResult: (Result<String>) -> Unit) {
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) onResult(runCatching { read(chooser.selectedFile.toPath()) })
        }
    }
}
