package io.github.debanshu.shaderx.ecosystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.debanshu.shaderlab.shaderx.compose.LocalShaderFactory
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Coil 3.4.0 consumer recipe, not a ShaderX API. The caller shares/owns [imageLoader], [factory]
 * and [playback] above model changes. Coil owns requests, decoding and caches; this function
 * never decodes or retains an additional bitmap. [contentDescription] is supplied by the caller
 * (null for decorative content). [onLoadState] reports Loading, Success and Error, not Empty. Compose owns the 240 ms reveal animation.
 * Loading and failure painters share the same clipped shader layer; status belongs in a sibling.
 * Paused playback can still receive reveal parameter updates. Supply host lifecycle/reduced-motion
 * preferences when creating playback; [animateReveal] should be false for reduced motion.
 */
@Composable
fun ShaderImage(
    model: Any?,
    imageLoader: ImageLoader,
    playback: ShaderPlayback,
    modifier: Modifier = Modifier,
    factory: ShaderFactory = LocalShaderFactory.current,
    animateReveal: Boolean = true,
    contentDescription: String? = null,
    onLoadState: (AsyncImagePainter.State) -> Unit = {},
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        if (loaded) 1f else 0f,
        animationSpec = tween(if (animateReveal) 240 else 0),
    )
    AsyncImage(
        model = model,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(Color(0xFFFFA500)),
        error = ColorPainter(Color.Red),
        fallback = ColorPainter(Color.Red),
        onLoading = { loaded = false; onLoadState(it) },
        onSuccess = { loaded = true; onLoadState(it) },
        onError = { loaded = false; onLoadState(it) },
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .controlledShaderEffect(WaveEffect(amplitude = 4f * (1f - reveal)), playback, factory = factory),
    )
}

/**
 * Haze 1.7.2 consumer recipe. Only [source] is captured by Haze. The empty centered surface
 * receives grayscale after Haze draws its blurred backdrop. [foreground] is a separate sibling,
 * outside both capture and ShaderX; moving the modifier onto the parent would filter it too.
 * Haze owns its capture layers; detaching the source releases its registered area.
 * Neither this function nor ShaderX captures SurfaceView/platform video surfaces.
 */
@Composable
fun HazeShaderCard(
    modifier: Modifier = Modifier,
    factory: ShaderFactory = LocalShaderFactory.current,
    state: HazeState = remember { HazeState() },
    source: @Composable BoxScope.() -> Unit,
    foreground: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(Modifier.matchParentSize().hazeSource(state), content = source)
        Box(Modifier.fillMaxSize(.75f).align(Alignment.Center)) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))
                .shaderEffect(GrayscaleEffect(), factory = factory)
                .hazeEffect(state) {
                    backgroundColor = Color.Black
                    blurRadius = 8.dp
                    noiseFactor = 0f
                    tints = emptyList()
                })
            Box(Modifier.matchParentSize(), content = foreground)
        }
    }
}
