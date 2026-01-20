package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable

sealed class PickResult {
    data class Success(
        val uri: String,
        val bytes: ByteArray,
    ) : PickResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return uri == other.uri && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = uri.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data object Cancelled : PickResult()

    data class Error(
        val message: String,
    ) : PickResult()
}

interface ImagePickerLauncher {
    fun launch()
}

@Composable
expect fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher
