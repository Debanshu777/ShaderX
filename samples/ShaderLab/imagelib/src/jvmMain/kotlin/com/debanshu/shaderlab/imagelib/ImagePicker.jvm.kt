package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.io.File

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher {
    var showFilePicker by remember { mutableStateOf(false) }

    FilePicker(
        show = showFilePicker,
        fileExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp"),
    ) { platformFile ->
        showFilePicker = false

        if (platformFile != null) {
            try {
                val file = File(platformFile.path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    onResult(PickResult.Success(platformFile.path, bytes))
                } else {
                    onResult(PickResult.Error("File not found: ${platformFile.path}"))
                }
            } catch (e: Exception) {
                onResult(PickResult.Error(e.message ?: "Unknown error reading file"))
            }
        } else {
            onResult(PickResult.Cancelled)
        }
    }

    return remember {
        object : ImagePickerLauncher {
            override fun launch() {
                showFilePicker = true
            }
        }
    }
}
