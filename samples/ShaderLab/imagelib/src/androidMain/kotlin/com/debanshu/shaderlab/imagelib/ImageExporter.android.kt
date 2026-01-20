package com.debanshu.shaderlab.imagelib

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of image exporter using MediaStore.
 */
class AndroidImageExporter(
    private val context: Context,
) : ImageExporter {
    override val isSupported: Boolean = true

    override suspend fun exportImage(
        imageBytes: ByteArray,
        fileName: String,
        config: ExportConfig,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val bitmap =
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ?: return@withContext ExportResult.Error("Failed to decode image")

                val mimeType =
                    when (config.format) {
                        ImageFormat.PNG -> "image/png"
                        ImageFormat.JPEG -> "image/jpeg"
                        ImageFormat.WEBP -> "image/webp"
                    }

                val extension =
                    when (config.format) {
                        ImageFormat.PNG -> "png"
                        ImageFormat.JPEG -> "jpg"
                        ImageFormat.WEBP -> "webp"
                    }

                val compressFormat =
                    when (config.format) {
                        ImageFormat.PNG -> {
                            Bitmap.CompressFormat.PNG
                        }

                        ImageFormat.JPEG -> {
                            Bitmap.CompressFormat.JPEG
                        }

                        ImageFormat.WEBP -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
                            }
                        }
                    }

                val contentValues =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$extension")
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ShaderLab")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                val resolver = context.contentResolver
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: return@withContext ExportResult.Error("Failed to create media entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(compressFormat, config.quality, outputStream)
                } ?: return@withContext ExportResult.Error("Failed to open output stream")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                ExportResult.Success(uri.toString())
            } catch (e: Exception) {
                ExportResult.Error(e.message ?: "Unknown error during export")
            }
        }
}

private var exporterContext: Context? = null

fun initImageExporter(context: Context) {
    exporterContext = context.applicationContext
}

actual fun createImageExporter(): ImageExporter {
    val context =
        exporterContext
            ?: throw IllegalStateException("ImageExporter not initialized. Call initImageExporter(context) first.")
    return AndroidImageExporter(context)
}
