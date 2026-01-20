package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPermissionHandler(): PermissionHandler = remember { JvmPermissionHandler() }

private class JvmPermissionHandler : PermissionHandler {
    override fun checkPermission(permission: ImagePermission): PermissionStatus = PermissionStatus.NOT_REQUIRED

    override suspend fun requestPermission(permission: ImagePermission): PermissionStatus = PermissionStatus.NOT_REQUIRED
}
