package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher {
    var showFilePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    var readJob by remember { mutableStateOf<Job?>(null) }

    FilePicker(
        show = showFilePicker,
        fileExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp"),
    ) { platformFile ->
        showFilePicker = false

        readJob?.cancel()
        readJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (platformFile == null) {
                    PickResult.Cancelled
                } else {
                    try {
                        PickResult.Success(platformFile.path, File(platformFile.path).readBytes())
                    } catch (e: Exception) {
                        PickResult.Error(e.message ?: "Unknown error reading file")
                    }
                }
            }
            currentOnResult(result)
        }
    }

    return remember {
        object : ImagePickerLauncher {
            override fun launch() {
                readJob?.cancel()
                showFilePicker = true
            }
        }
    }
}
