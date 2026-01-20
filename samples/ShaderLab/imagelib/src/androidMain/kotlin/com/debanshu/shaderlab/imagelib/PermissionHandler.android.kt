package com.debanshu.shaderlab.imagelib

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberPermissionHandler(): PermissionHandler {
    val context = LocalContext.current
    val pendingResult = remember { mutableStateOf<((PermissionStatus) -> Unit)?>(null) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val status = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
        pendingResult.value?.invoke(status)
        pendingResult.value = null
    }
    
    return remember(context, launcher) {
        object : PermissionHandler {
            override fun checkPermission(permission: ImagePermission): PermissionStatus {
                val androidPermission = getAndroidPermission(permission)
                    ?: return PermissionStatus.NOT_REQUIRED
                
                return when {
                    ContextCompat.checkSelfPermission(context, androidPermission) == 
                        PackageManager.PERMISSION_GRANTED -> PermissionStatus.GRANTED
                    else -> PermissionStatus.NOT_REQUESTED
                }
            }
            
            override suspend fun requestPermission(permission: ImagePermission): PermissionStatus {
                val androidPermission = getAndroidPermission(permission)
                    ?: return PermissionStatus.NOT_REQUIRED
                
                // Check if already granted
                if (ContextCompat.checkSelfPermission(context, androidPermission) == 
                    PackageManager.PERMISSION_GRANTED) {
                    return PermissionStatus.GRANTED
                }
                
                return suspendCancellableCoroutine { continuation ->
                    pendingResult.value = { status ->
                        continuation.resume(status)
                    }
                    launcher.launch(androidPermission)
                }
            }
            
            private fun getAndroidPermission(permission: ImagePermission): String? {
                return when (permission) {
                    ImagePermission.READ_IMAGES -> {
                        when {
                            // Android 13+ uses new photo permissions
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> 
                                Manifest.permission.READ_MEDIA_IMAGES
                            // Android 10+ uses scoped storage, no permission needed for picker
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> null
                            else -> Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    }
                    ImagePermission.WRITE_IMAGES -> {
                        when {
                            // Android 10+ uses MediaStore, no write permission needed
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> null
                            else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }
                    }
                }
            }
        }
    }
}

