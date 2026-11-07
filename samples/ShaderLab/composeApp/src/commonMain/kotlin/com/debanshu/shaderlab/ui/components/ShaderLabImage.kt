package com.debanshu.shaderlab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.debanshu.shaderlab.imagelib.rememberDecodedImage
import com.debanshu.shaderlab.viewmodel.ImageSource

@Composable
fun ShaderLabImage(
    imageSource: ImageSource,
    modifier: Modifier = Modifier,
    contentDescription: String = "Preview image",
    contentScale: ContentScale = ContentScale.Fit,
) {
    when (imageSource) {
        is ImageSource.Bundled -> {
            SampleImage(
                resourceName = imageSource.resourceName,
                modifier = modifier,
                contentScale = contentScale,
            )
        }

        is ImageSource.Picked -> {
            val decoded = rememberDecodedImage(imageSource.bytes)
            val imageBitmap = decoded.bitmap
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = contentScale,
                )
            } else {
                Box(
                    modifier = modifier.background(
                        if (decoded.isLoading) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.errorContainer,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (decoded.isLoading) "Loading image…" else "Unable to load image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
