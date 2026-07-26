package com.debanshu.shaderlab.ui

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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.ui.components.ShaderLabImage
import com.debanshu.shaderlab.viewmodel.ImageSource
import kotlinx.coroutines.isActive

@Composable
fun ShaderPreview(
    imageSource: ImageSource,
    effect: ShaderEffect?,
    factory: ShaderFactory,
    showFps: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var fps by remember { mutableIntStateOf(0) }
    var shaderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(effect?.id, effect?.hashCode()) {
        shaderError = null
    }

    val showFpsOverlay = showFps && effect is AnimatedShaderEffect && effect.isAnimating

    if (showFpsOverlay) {
        LaunchedEffect(effect.id, effect.hashCode()) {
            var frameCount = 0
            var lastTimestamp = 0L
            while (isActive) {
                withFrameMillis { timestamp ->
                    frameCount++
                    if (lastTimestamp == 0L) {
                        lastTimestamp = timestamp
                    } else if (timestamp - lastTimestamp >= 1000L) {
                        fps = frameCount
                        frameCount = 0
                        lastTimestamp = timestamp
                    }
                }
            }
        }
    } else {
        SideEffect {
            if (fps != 0) fps = 0
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ImageWithEffect(
            imageSource = imageSource,
            effect = effect,
            factory = factory,
            onShaderError = { shaderError = it },
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

        if (showFpsOverlay && fps > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                        ).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "$fps FPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (!factory.isSupported() && effect != null) {
            ShaderBanner(
                text = "Shaders not supported on this device",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        shaderError?.let { message ->
            ShaderBanner(
                text = message,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ShaderBanner(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(12.dp)
                .background(
                    color = containerColor.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun ImageWithEffect(
    imageSource: ImageSource,
    effect: ShaderEffect?,
    factory: ShaderFactory,
    onShaderError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shaderModifier =
        if (effect != null && factory.isSupported()) {
            Modifier.shaderEffect(
                effect = effect,
                factory = factory,
                onError = { error -> onShaderError(error.message) },
            )
        } else {
            Modifier
        }

    ShaderLabImage(
        imageSource = imageSource,
        modifier = modifier.then(shaderModifier),
        contentScale = ContentScale.Fit,
    )
}
