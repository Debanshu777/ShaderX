package com.debanshu.shaderlab.shaderx.compose

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterValues
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.result.*

/**
 * Generates pixels behind unchanged content, including in an empty sized Box.
 * Coordinates and resolution are physical pixels in this modifier's bounds. Padding before this
 * modifier excludes padding from those bounds; padding after it includes padding. Output is clipped
 * to the rectangular bounds; place Compose clip before this modifier for shaped clipping.
 * Transparent shader output stays transparent. No opaque content placeholder or backdrop capture.
 *
 * Time bindings require externally owned [playback]; live bindings require [frameProvider]. Paused
 * unchanged instances schedule no frames. Increment [inputRevision] for a complete live snapshot.
 * Images are borrowed and must not be mutated concurrently. Each instance owns its native snapshot;
 * source/values/size/time/revision changes replace it. Skia reuses the factory's compiled cache;
 * Android creates a fresh shader and sets structural colors once per snapshot, which can compile
 * each frame. No native shader/brush is shared across instances or exposed for arbitrary text use.
 *
 * A failed whole replacement clears prior background output, draws [fallback] (or transparent),
 * and reports [onError], preserving foreground content. Third-party factories without this route
 * fail explicitly. Nonpositive dimensions draw no background and do not call providers.
 * Disposal closes owned Skia snapshots, never the caller's factory/playback. Use on the composition
 * thread. [onReady] signals native snapshot creation for the current candidate, not GPU presentation.
 */
@Composable
public fun Modifier.shaderBackground(
    background: ShaderBackground,
    values: ParameterValues = background.definition.schema.defaults(),
    playback: ShaderPlayback? = null,
    inputRevision: Long = 0L,
    frameProvider: ShaderFrameProvider? = null,
    fallback: Brush? = null,
    factory: ShaderFactory = LocalShaderFactory.current,
    onError: ((ShaderError) -> Unit)? = null,
    onReady: (() -> Unit)? = null,
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var snapshot by remember { mutableStateOf<BackgroundSnapshot?>(null) }
    val error by rememberUpdatedState(onError)
    val ready by rememberUpdatedState(onReady)
    val position = playback?.positionSeconds ?: 0.0
    DisposableEffect(Unit) { onDispose { snapshot?.close(); snapshot = null } }
    LaunchedEffect(background, values, BackgroundReference(factory), size, position, BackgroundReference(playback), inputRevision, BackgroundReference(frameProvider)) {
        val old = snapshot
        snapshot = null
        if (size.width <= 0 || size.height <= 0) { old?.close(); return@LaunchedEffect }
        val result = if (playback == null && background.definition.program.bindings.any { it.clock }) {
            ShaderResult.failure(ShaderError.UnsupportedEffect("Time bindings require caller-owned ShaderPlayback", background.definition.id))
        } else createBackgroundSnapshot(factory, background, values, size.width.toFloat(), size.height.toFloat(), position.toFloat(), frameProvider)
        old?.close()
        result.onSuccess { snapshot = it; ready?.invoke() }.onFailure { error?.invoke(it) }
    }
    return onSizeChanged { size = it }.drawWithContent {
        val brush = snapshot?.brush ?: fallback
        if (brush != null) clipRect { drawRect(brush) }
        drawContent()
    }
}

// User callbacks may override equals/hashCode. Replacement is defined by reference identity.
private class BackgroundReference(private val value: Any?) {
    override fun equals(other: Any?): Boolean = other is BackgroundReference && value === other.value
    override fun hashCode(): Int = 0
}
