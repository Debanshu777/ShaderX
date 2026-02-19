package com.debanshu.shaderlab.imagelib

import kotlin.io.encoding.Base64

class WasmImageExporter : ImageExporter {
    override val isSupported: Boolean = true

    override suspend fun exportImage(
        imageBytes: ByteArray,
        fileName: String,
        config: ExportConfig,
    ): ExportResult {
        return try {
            val extension =
                when (config.format) {
                    ImageFormat.PNG -> "png"
                    ImageFormat.JPEG -> "jpg"
                    ImageFormat.WEBP -> "webp"
                }
            val fullFileName = if (fileName.lowercase().endsWith(".$extension")) fileName else "$fileName.$extension"

            val base64 = Base64.Default.encode(imageBytes)
            downloadFileFromBase64(base64, fullFileName)
            ExportResult.Success(null)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "Unknown error during export")
        }
    }
}

private fun downloadFileFromBase64(base64: String, fileName: String): Unit =
    js("""{
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const blob = new Blob([bytes]);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        URL.revokeObjectURL(url);
    }""")

actual fun createImageExporter(): ImageExporter = WasmImageExporter()
