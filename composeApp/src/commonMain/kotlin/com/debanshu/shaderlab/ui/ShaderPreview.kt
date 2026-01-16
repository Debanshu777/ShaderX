package com.debanshu.shaderlab.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.imagelib.decodeImageBytes
import com.debanshu.shaderlab.shaderlib.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect
import com.debanshu.shaderlab.shaderlib.factory.ShaderFactory
import com.debanshu.shaderlab.shaderlib.factory.create
import com.debanshu.shaderlab.ui.components.SampleImage
import com.debanshu.shaderlab.viewmodel.ImageSource

@Composable
fun ShaderPreview(
    imageSource: ImageSource,
    effect: ShaderEffect?,
    onWaveTimeUpdate: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var contentWidth by remember { mutableFloatStateOf(0f) }
    var contentHeight by remember { mutableFloatStateOf(0f) }
    val factory = remember { ShaderFactory.create() }

    val animatableEffect = effect as? AnimatedShaderEffect
    val shouldAnimate = animatableEffect?.isAnimating == true

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "waveTime",
    )

    LaunchedEffect(shouldAnimate, animatedTime) {
        if (shouldAnimate) {
            onWaveTimeUpdate(animatedTime)
        }
    }

    val renderEffect =
        remember(effect, contentWidth, contentHeight) {
            if (effect != null && contentWidth > 0 && contentHeight > 0 && factory.isSupported()) {
                factory.createRenderEffect(effect, contentWidth, contentHeight).getOrNull()
            } else {
                null
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { size ->
                    contentWidth = size.width.toFloat()
                    contentHeight = size.height.toFloat()
                },
        contentAlignment = Alignment.Center,
    ) {
        ImageWithEffect(
            imageSource = imageSource,
            renderEffect = renderEffect,
            modifier = Modifier.fillMaxSize(),
        )

        effect?.let {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = it.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (!factory.isSupported() && effect != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Shaders not supported on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ImageWithEffect(
    imageSource: ImageSource,
    renderEffect: androidx.compose.ui.graphics.RenderEffect?,
    modifier: Modifier = Modifier,
) {
    when (imageSource) {
        is ImageSource.Bundled -> {
            Box(
                modifier =
                    modifier.graphicsLayer {
                        this.renderEffect = renderEffect
                    },
            ) {
                SampleImage(
                    resourceName = imageSource.resourceName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        is ImageSource.Picked -> {
            val imageBitmap =
                remember(imageSource.bytes) {
                    imageSource.bytes?.let { decodeImageBytes(it) }
                }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Preview image",
                    modifier =
                        modifier.graphicsLayer {
                            this.renderEffect = renderEffect
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Unable to load image",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
