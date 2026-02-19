package com.debanshu.shaderlab.imagelib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.io.encoding.Base64

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickResult) -> Unit): ImagePickerLauncher {
    return remember(onResult) {
        object : ImagePickerLauncher {
            override fun launch() {
                openFilePickerAndRead { base64, fileName ->
                    when {
                        base64.isEmpty() && fileName.isEmpty() -> onResult(PickResult.Cancelled)
                        base64.isNotEmpty() && fileName.isNotEmpty() -> {
                            try {
                                val bytes = Base64.Default.decode(base64)
                                onResult(PickResult.Success(fileName, bytes))
                            } catch (e: Exception) {
                                onResult(PickResult.Error(e.message ?: "Failed to decode file"))
                            }
                        }
                        else -> onResult(PickResult.Cancelled)
                    }
                }
            }
        }
    }
}

private fun openFilePickerAndRead(callback: (base64: String, fileName: String) -> Unit): Unit =
    js("""{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.style.display = 'none';
        document.body.appendChild(input);
        input.onchange = function() {
            const file = input.files && input.files[0];
            if (input.parentNode) input.parentNode.removeChild(input);
            if (!file) {
                callback('', '');
                return;
            }
            const reader = new FileReader();
            reader.onload = function() {
                try {
                    const arrayBuffer = reader.result;
                    const bytes = new Uint8Array(arrayBuffer);
                    let binary = '';
                    for (let i = 0; i < bytes.length; i++) {
                        binary += String.fromCharCode(bytes[i]);
                    }
                    const base64 = btoa(binary);
                    callback(base64, file.name);
                } catch (e) {
                    callback('', '');
                }
            };
            reader.readAsArrayBuffer(file);
        };
        input.click();
    }""")
