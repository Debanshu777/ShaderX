package com.debanshu.shaderlab.imagelib

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                try {
                    val bytes =
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes()
                        }
                    if (bytes != null) {
                        onResult(PickResult.Success(uri.toString(), bytes))
                    } else {
                        onResult(PickResult.Error("Failed to read image data"))
                    }
                } catch (e: Exception) {
                    onResult(PickResult.Error(e.message ?: "Unknown error reading image"))
                }
            } else {
                onResult(PickResult.Cancelled)
            }
        }

    return remember(launcher) {
        object : ImagePickerLauncher {
            override fun launch() {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}
