package com.debanshu.shaderlab.imagelib

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    var readJob by remember { mutableStateOf<Job?>(null) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            readJob?.cancel()
            readJob = scope.launch {
                val result = withContext(Dispatchers.IO) {
                    if (uri == null) {
                        PickResult.Cancelled
                    } else {
                        try {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null) {
                                PickResult.Success(uri.toString(), bytes)
                            } else {
                                PickResult.Error("Failed to read image data")
                            }
                        } catch (e: Exception) {
                            PickResult.Error(e.message ?: "Unknown error reading image")
                        }
                    }
                }
                currentOnResult(result)
            }
        }

    return remember(launcher) {
        object : ImagePickerLauncher {
            override fun launch() {
                readJob?.cancel()
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}
