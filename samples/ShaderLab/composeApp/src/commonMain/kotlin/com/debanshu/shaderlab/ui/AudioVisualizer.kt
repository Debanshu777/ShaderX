package com.debanshu.shaderlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.audiolib.AudioCaptureState
import com.debanshu.shaderlab.audiolib.Fft
import com.debanshu.shaderlab.audiolib.packMagnitudesToImage
import com.debanshu.shaderlab.audiolib.rememberAudioCapture
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.ui.components.ShaderLabImage
import com.debanshu.shaderlab.viewmodel.ImageSource
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Audio tab: live mic → FFT → spectrum **bar graph** on a dark canvas.
 */
@Composable
fun AudioVisualizer(
    baseEffect: AudioVisualizerEffect,
    factory: ShaderFactory,
    modifier: Modifier = Modifier,
) {
    val displayBins = baseEffect.fftSize.toInt().coerceIn(8, 512)
    val capture = rememberAudioCapture(CAPTURE_BINS)
    val state by capture.state.collectAsState()
    val scope = rememberCoroutineScope()
    val currentDisplayBins by rememberUpdatedState(displayBins)
    val fftSlot = remember { FftImageSlot() }

    val effect =
        remember(baseEffect.fftSize, baseEffect.barColorIntensity, baseEffect.animate) {
            AudioVisualizerEffect(
                fftSize = baseEffect.fftSize,
                barColorIntensity = baseEffect.barColorIntensity,
                animate = baseEffect.animate,
            ).withFftDataSource { fftSlot.image }
        }

    MicFftLoop(
        capture = capture,
        state = state,
        displayBins = currentDisplayBins,
        fftSlot = fftSlot,
    )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .shaderEffect(effect = effect, factory = factory),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
            )
        }

        MicOverlay(
            state = state,
            liveLabel = "Live · spectrum",
            onStart = { scope.launch { capture.start() } },
        )
    }
}

/**
 * Animated-tab companion: live mic → FFT → **image distortion** on [imageSource].
 */
@Composable
fun AudioDistortionPreview(
    baseEffect: AudioDistortionEffect,
    imageSource: ImageSource,
    factory: ShaderFactory,
    modifier: Modifier = Modifier,
) {
    val displayBins = baseEffect.fftSize.toInt().coerceIn(8, 512)
    val capture = rememberAudioCapture(CAPTURE_BINS)
    val state by capture.state.collectAsState()
    val scope = rememberCoroutineScope()
    val currentDisplayBins by rememberUpdatedState(displayBins)
    val fftSlot = remember { FftImageSlot() }

    val effect =
        remember(baseEffect.fftSize, baseEffect.intensity, baseEffect.animate) {
            AudioDistortionEffect(
                fftSize = baseEffect.fftSize,
                intensity = baseEffect.intensity,
                animate = baseEffect.animate,
            ).withFftDataSource { fftSlot.image }
        }

    MicFftLoop(
        capture = capture,
        state = state,
        displayBins = currentDisplayBins,
        fftSlot = fftSlot,
    )

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        ShaderLabImage(
            imageSource = imageSource,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .shaderEffect(effect = effect, factory = factory),
        )

        MicOverlay(
            state = state,
            liveLabel = "Live mic · image distortion",
            onStart = { scope.launch { capture.start() } },
        )
    }
}

@Composable
private fun MicFftLoop(
    capture: com.debanshu.shaderlab.audiolib.AudioCapture,
    state: AudioCaptureState,
    displayBins: Int,
    fftSlot: FftImageSlot,
) {
    val currentBins by rememberUpdatedState(displayBins)
    LaunchedEffect(capture, state) {
        if (state != AudioCaptureState.RUNNING) return@LaunchedEffect
        while (isActive) {
            withFrameMillis {
                val resampled = Fft.resample(capture.currentSpectrum(), currentBins)
                fftSlot.image = packMagnitudesToImage(resampled)
            }
        }
    }
}

@Composable
private fun BoxScope.MicOverlay(
    state: AudioCaptureState,
    liveLabel: String,
    onStart: () -> Unit,
) {
    when (state) {
        AudioCaptureState.RUNNING -> {
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
                    text = liveLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AudioPrompt(state = state, onStart = onStart)
            }
        }
    }
}

private class FftImageSlot(
    var image: ImageBitmap = ImageBitmap(1, 1),
)

@Composable
private fun AudioPrompt(
    state: AudioCaptureState,
    onStart: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val message =
            when (state) {
                AudioCaptureState.PERMISSION_DENIED -> "Microphone permission denied. Grant it in settings to visualize audio."
                AudioCaptureState.ERROR -> "Could not start the microphone on this device."
                AudioCaptureState.UNSUPPORTED -> "Microphone capture is not supported on this platform."
                else -> "Enable the microphone to drive this effect with live audio."
            }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (state != AudioCaptureState.UNSUPPORTED) {
            Button(onClick = onStart) {
                Text(if (state == AudioCaptureState.PERMISSION_DENIED) "Retry" else "Enable microphone")
            }
        }
    }
}

private const val CAPTURE_BINS: Int = 256
