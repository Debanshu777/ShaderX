package com.debanshu.shaderlab.imagelib

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import kotlin.coroutines.resume

class IOSImageExporter : ImageExporter {
    override val isSupported: Boolean = true

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun exportImage(
        imageBytes: ByteArray,
        fileName: String,
        config: ExportConfig,
    ): ExportResult =
        suspendCancellableCoroutine { continuation ->
            val data =
                imageBytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
                }

            val uiImage = UIImage.imageWithData(data)
            if (uiImage == null) {
                continuation.resume(ExportResult.Error("Failed to create UIImage from data"))
                return@suspendCancellableCoroutine
            }

            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromImage(uiImage)
            }) { success, error ->
                if (success) {
                    continuation.resume(ExportResult.Success(null))
                } else {
                    val message = error?.localizedDescription ?: "Unknown error"
                    continuation.resume(ExportResult.Error(message))
                }
            }
        }
}

actual fun createImageExporter(): ImageExporter = IOSImageExporter()
