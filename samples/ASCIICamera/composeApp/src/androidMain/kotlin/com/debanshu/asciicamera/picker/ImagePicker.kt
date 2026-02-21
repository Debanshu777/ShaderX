package com.debanshu.asciicamera.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        onResult(input.readBytes())
                    } ?: onResult(null)
                } catch (_: Exception) {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
        }

    return remember(launcher) {
        { launcher.launch("image/*") }
    }
}
