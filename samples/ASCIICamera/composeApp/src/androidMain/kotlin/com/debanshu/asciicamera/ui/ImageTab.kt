package com.debanshu.asciicamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.debanshu.asciicamera.picker.decodeImageBytes

@Composable fun ImageTab(
    pickedImageBytes: ByteArray?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    modifier: Modifier = Modifier,
    onSizeChanged: (Int, Int) -> Unit = { _, _ -> },
    renderEffect: RenderEffect? = null,
) {
    val painter = decodeImageBytes(pickedImageBytes)
    val decodeError = pickedImageBytes != null && painter == null

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            pickedImageBytes == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(onClick = onPickImage) {
                        Text("Pick an image")
                    }
                }
            }

            decodeError -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Failed to load image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(
                            modifier = Modifier.size(16.dp),
                        )
                        Button(onClick = onPickImage) {
                            Text("Retry")
                        }
                    }
                }
            }

            painter != null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { size -> onSizeChanged(size.width, size.height) }
                            .graphicsLayer {
                                clip = true
                                this.renderEffect = renderEffect
                            },
                ) {
                    Image(
                        painter = painter,
                        contentDescription = "Picked image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Button(
                    onClick = onClearImage,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}
