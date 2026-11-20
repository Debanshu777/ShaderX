package com.debanshu.shaderx.sampler

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderx.sampler.resources.Res
import com.debanshu.shaderx.sampler.resources.sample_nature
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.math.floor

/** The clock stays independent when strength or playback speed changes. */
@Composable
internal fun AnimationTab(playback: ShaderPlayback) {
    var strength by rememberSaveable { mutableFloatStateOf(24f) }
    var error by remember { mutableStateOf<String?>(null) }
    val factory = rememberShaderFactory()
    var scrubWindow by remember { mutableStateOf<Double?>(null) }
    val seekWindow = scrubWindow ?: floor(playback.positionSeconds / 10.0) * 10.0
    val wave = remember(strength) { WaveEffect(amplitude = strength, frequency = 9f) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make it wobble", style = MaterialTheme.typography.headlineMedium)
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp))) {
            Image(
                painterResource(Res.drawable.sample_nature), "Flower field with animated wave distortion",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().controlledShaderEffect(
                    wave, playback, factory = factory, onError = { error = it.message },
                ),
            )
            Text(
                "WAVE / FLOW",
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
                    .background(Color(0xCC17291F), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = Color.White,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { playback.isPlaying = !playback.isPlaying }) {
                Text(if (playback.isPlaying) "Pause" else "Play")
            }
            OutlinedButton(onClick = { playback.isPlaying = false; playback.seekTo(0.0); playback.speed = 1.0; strength = 24f; scrubWindow = null }) { Text("Reset") }
            Text("${(playback.positionSeconds * 10).roundToInt() / 10.0} s", style = MaterialTheme.typography.titleMedium)
        }
        SamplerSlider("Strength · ${strength.roundToInt()} px", strength, { strength = it }, 0f..50f)
        SamplerSlider("Speed · ${(playback.speed * 10).roundToInt() / 10.0}×", playback.speed.toFloat(), { playback.speed = it.toDouble() }, 0f..3f)
        SamplerSlider("Seek · ${seekWindow.toInt()}–${seekWindow.toInt() + 10} seconds", (playback.positionSeconds - seekWindow).toFloat().coerceIn(0f, 10f), {
            // Freeze the range until the pointer is released, including at the right endpoint.
            if (scrubWindow == null) scrubWindow = seekWindow
            playback.isPlaying = false
            playback.seekTo(scrubWindow!! + it.toDouble())
        }, 0f..10f,
            displayValue = "${(playback.positionSeconds * 100).roundToInt() / 100.0} s",
            onValueChangeFinished = { scrubWindow = null })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Details(title = "How it works") {
            Text("The app owns one clock. Seeking pauses it; strength and speed edits keep its current position. Hidden tabs stop automatic progression.")
            Text(
                "val playback = rememberShaderPlayback(active = active)\n" +
                    "val wave = WaveEffect(amplitude = strength)\n" +
                    "Modifier.controlledShaderEffect(wave, playback)\n" +
                    "playback.isPlaying = false\nplayback.seekTo(2.5)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
