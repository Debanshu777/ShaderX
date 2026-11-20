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
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.querySupport
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
    var shaderError by remember(effect, factory, imageSource) { mutableStateOf<String?>(null) }
    val support = remember(factory, effect) {
        factory.querySupport(
            if (effect is AnimatedShaderEffect) ShaderOperation.ANIMATED_SESSION else ShaderOperation.CONTENT_RENDER,
            effect,
        )
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

        if (support.status == ShaderSupportStatus.UNSUPPORTED && effect != null) {
            ShaderBanner(
                text = "Original content shown: ${support.message}",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        shaderError?.let { message ->
            ShaderBanner(
                text = "Effect failed: $message. Creation keeps original content; frame update failures may keep the previous filter.",
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
    // Only definitions use the new controlled seam. Legacy live audio retains its frame loop.
    val controlled = effect as? AnimatedShaderEffect
    val fallbackPlayback = rememberShaderPlayback(initiallyPlaying = false, active = false)
    val playback = LocalPlaygroundPlayback.current ?: fallbackPlayback

    val shaderModifier =
        if (controlled != null) {
            Modifier.controlledShaderEffect(
                effect = controlled,
                playback = playback,
                factory = factory,
                onError = { error -> onShaderError(error.message) },
            )
        } else if (effect is CompositeEffect && effect.effects.any { it is AnimatedShaderEffect }) {
            Modifier.controlledShaderEffect(effect, playback, factory = factory, onError = { error -> onShaderError(error.message) })
        } else if (effect != null) {
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
