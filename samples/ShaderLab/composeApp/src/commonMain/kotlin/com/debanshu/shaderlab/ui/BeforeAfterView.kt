package com.debanshu.shaderlab.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.imagelib.decodeImageBytes
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.ui.components.SampleImage
import com.debanshu.shaderlab.viewmodel.ImageSource
import kotlin.math.roundToInt

@Composable
fun BeforeAfterView(
    imageSource: ImageSource,
    effect: ShaderEffect?,
    onWaveTimeUpdate: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var dividerPosition by remember { mutableFloatStateOf(0.5f) }
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
        remember(effect, containerWidth, containerHeight) {
            if (effect != null && containerWidth > 0 && containerHeight > 0 && factory.isSupported()) {
                factory.createRenderEffect(effect, containerWidth, containerHeight).getOrNull()
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
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                },
    ) {
        ImageContent(
            imageSource = imageSource,
            renderEffect = renderEffect,
            contentDescription = "After - with effect",
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape =
                            GenericShape { size, _ ->
                                addRect(Rect(0f, 0f, size.width * dividerPosition, size.height))
                            }
                    },
        ) {
            ImageContent(
                imageSource = imageSource,
                renderEffect = null,
                contentDescription = "Before - original",
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset((containerWidth * dividerPosition).roundToInt() - 2, 0) }
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPosition = dividerPosition + (dragAmount / containerWidth)
                            dividerPosition = newPosition.coerceIn(0.05f, 0.95f)
                        }
                    },
        )

        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            (containerWidth * dividerPosition).roundToInt() - 16,
                            (containerHeight / 2).roundToInt() - 24,
                        )
                    }.width(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ).padding(vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPosition = dividerPosition + (dragAmount / containerWidth)
                            dividerPosition = newPosition.coerceIn(0.05f, 0.95f)
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⟷",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Before",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "After",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ImageContent(
    imageSource: ImageSource,
    renderEffect: androidx.compose.ui.graphics.RenderEffect?,
    contentDescription: String,
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
                    contentDescription = contentDescription,
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
