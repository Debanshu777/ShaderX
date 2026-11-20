package io.github.debanshu.shaderx.ecosystem

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import io.github.debanshu.shaderx.ecosystem.ShaderImage

@Composable
fun ImageRecipeScreen(
    model: Any?,
    imageLoader: ImageLoader,
    contentDescription: String?,
    active: Boolean,
    reducedMotion: Boolean,
) {
    val factory = rememberShaderFactory(maxCacheSize = 8)
    val playback = rememberShaderPlayback(active = active, reducedMotion = reducedMotion)
    ShaderImage(
        model = model,
        imageLoader = imageLoader,
        playback = playback,
        modifier = Modifier.size(240.dp),
        factory = factory,
        animateReveal = !reducedMotion,
        contentDescription = contentDescription,
    )
}
