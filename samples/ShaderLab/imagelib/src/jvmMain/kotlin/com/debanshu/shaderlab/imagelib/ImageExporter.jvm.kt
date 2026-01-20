package com.debanshu.shaderlab.imagelib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

class JvmImageExporter : ImageExporter {
    override val isSupported: Boolean = true

    override suspend fun exportImage(
        imageBytes: ByteArray,
        fileName: String,
        config: ExportConfig,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val extension =
                    when (config.format) {
                        ImageFormat.PNG -> "png"
                        ImageFormat.JPEG -> "jpg"
                        ImageFormat.WEBP -> "webp"
                    }

                val suggestedName = "$fileName.$extension"

                val selectedFile = showSaveDialog(suggestedName)

                if (selectedFile != null) {
                    var file = selectedFile
                    if (!file.name.lowercase().endsWith(".$extension")) {
                        file = File(file.absolutePath + ".$extension")
                    }

                    file.writeBytes(imageBytes)
                    ExportResult.Success(file.absolutePath)
                } else {
                    ExportResult.Error("Export cancelled")
                }
            } catch (e: Exception) {
                ExportResult.Error(e.message ?: "Unknown error during export")
            }
        }

    private suspend fun showSaveDialog(suggestedName: String): File? =
        withContext(Dispatchers.IO) {
            var result: File? = null
            val latch = java.util.concurrent.CountDownLatch(1)

            SwingUtilities.invokeLater {
                val dialog = FileDialog(null as Frame?, "Save Image", FileDialog.SAVE)
                dialog.file = suggestedName
                dialog.isVisible = true

                val directory = dialog.directory
                val file = dialog.file

                result =
                    if (directory != null && file != null) {
                        File(directory, file)
                    } else {
                        null
                    }
                latch.countDown()
            }

            latch.await()
            result
        }
}

actual fun createImageExporter(): ImageExporter = JvmImageExporter()
