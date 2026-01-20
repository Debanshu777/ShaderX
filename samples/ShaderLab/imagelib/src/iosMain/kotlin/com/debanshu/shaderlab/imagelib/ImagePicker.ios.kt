package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSSelectorFromString
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher =
    remember(onResult) {
        IOSImagePickerLauncher(onResult)
    }

private class IOSImagePickerLauncher(
    private val onResult: (PickResult) -> Unit,
) : ImagePickerLauncher {
    private var currentDelegate: ImagePickerDelegate? = null

    override fun launch() {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary

        val delegate =
            ImagePickerDelegate(
                onResult = { result ->
                    onResult(result)
                    currentDelegate = null
                },
            )

        currentDelegate = delegate
        picker.delegate = delegate

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ImagePickerDelegate(
    private val onResult: (PickResult) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    @OptIn(ExperimentalForeignApi::class)
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image == null) {
            onResult(PickResult.Error("Failed to get image"))
            return
        }

        val jpegData = UIImageJPEGRepresentation(image, 1.0)
        if (jpegData == null) {
            onResult(PickResult.Error("Failed to convert image to data"))
            return
        }

        val bytes = jpegData.toByteArray()
        onResult(PickResult.Success("picked_image", bytes))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(PickResult.Cancelled)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        memcpy(bytes.refTo(0), this.bytes, this.length)
    }
    return bytes
}
