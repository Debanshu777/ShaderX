package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

@Composable
actual fun rememberPermissionHandler(): PermissionHandler {
    return remember { IOSPermissionHandler() }
}

private class IOSPermissionHandler : PermissionHandler {
    
    override fun checkPermission(permission: ImagePermission): PermissionStatus {
        val status = PHPhotoLibrary.authorizationStatus()
        return mapAuthorizationStatus(status)
    }
    
    override suspend fun requestPermission(permission: ImagePermission): PermissionStatus {
        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorization { status ->
                val permissionStatus = mapAuthorizationStatus(status)
                continuation.resume(permissionStatus)
            }
        }
    }
    
    private fun mapAuthorizationStatus(status: PHAuthorizationStatus): PermissionStatus {
        return when (status) {
            PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> PermissionStatus.GRANTED
            PHAuthorizationStatusDenied -> PermissionStatus.DENIED
            PHAuthorizationStatusNotDetermined -> PermissionStatus.NOT_REQUESTED
            else -> PermissionStatus.DENIED
        }
    }
}

