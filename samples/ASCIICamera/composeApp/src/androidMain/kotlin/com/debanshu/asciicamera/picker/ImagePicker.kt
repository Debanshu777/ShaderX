package com.debanshu.asciicamera.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    var readJob by remember { mutableStateOf<Job?>(null) }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            readJob?.cancel()
            readJob = scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        uri?.let { context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } }
                    } catch (_: Exception) {
                        null
                    }
                }
                currentOnResult(bytes)
            }
        }

    return remember(launcher) {
        {
            readJob?.cancel()
            launcher.launch("image/*")
        }
    }
}
