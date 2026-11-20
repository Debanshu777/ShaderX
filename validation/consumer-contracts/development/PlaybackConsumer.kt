package shaderx.consumer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback

// Compiled only against development APIs, in both independent consumer modules.
// The host supplies lifecycle/accessibility policy and can build controls using the state.
@Composable
fun ConsumerPlaybackPreview(
    amplitude: Float = 4f,
    active: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable (ShaderPlayback, Modifier) -> Unit,
) {
    val playback = rememberShaderPlayback(
        initialPositionSeconds = 2.0,
        active = active,
        reducedMotion = reducedMotion,
    )
    // Callers can pause/resume via playback.isPlaying, change speed, or seekTo(seconds).
    // Pass this same playback to other controlled modifiers for a shared phase.
    val modifier = Modifier.controlledShaderEffect(ConsumerDistortion.effect(amplitude), playback)
    content(playback, modifier)
}
