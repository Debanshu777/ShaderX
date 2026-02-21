package com.debanshu.asciicamera.camera

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable fun rememberCameraPermissionState(onPermissionResult: (Boolean) -> Unit): CameraPermissionState {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted -> onPermissionResult(granted) }

    return remember(launcher) {
        object : CameraPermissionState {
            override fun requestPermission() {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

interface CameraPermissionState {
    public fun requestPermission()
}
