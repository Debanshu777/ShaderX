package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable

enum class PermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    NOT_REQUIRED,
}

enum class ImagePermission {
    READ_IMAGES,
    WRITE_IMAGES,
}

interface PermissionHandler {
    fun checkPermission(permission: ImagePermission): PermissionStatus

    suspend fun requestPermission(permission: ImagePermission): PermissionStatus
}

@Composable
expect fun rememberPermissionHandler(): PermissionHandler
