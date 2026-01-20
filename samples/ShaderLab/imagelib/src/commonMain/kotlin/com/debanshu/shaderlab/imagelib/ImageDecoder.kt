package com.debanshu.shaderlab.imagelib

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
