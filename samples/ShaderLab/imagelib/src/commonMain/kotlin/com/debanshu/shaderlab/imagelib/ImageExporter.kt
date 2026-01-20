package com.debanshu.shaderlab.imagelib

data class ExportConfig(
    val format: ImageFormat = ImageFormat.PNG,
    val quality: Int = 100,
)

enum class ImageFormat {
    PNG,
    JPEG,
    WEBP,
}

sealed class ExportResult {
    data class Success(
        val path: String?,
    ) : ExportResult()

    data class Error(
        val message: String,
    ) : ExportResult()

    data object NotSupported : ExportResult()
}

interface ImageExporter {
    suspend fun exportImage(
        imageBytes: ByteArray,
        fileName: String,
        config: ExportConfig = ExportConfig(),
    ): ExportResult

    val isSupported: Boolean
}

expect fun createImageExporter(): ImageExporter
