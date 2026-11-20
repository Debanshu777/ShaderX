package com.debanshu.shaderx.sampler

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.plus

internal fun orderedEffects(grayscaleFirst: Boolean, gray: Float = 1f, tint: Float = 0.85f,
    color1: Long = 0xFFFF6644, color2: Long = 0xFF4466FF): CompositeEffect {
    val grayscale = GrayscaleEffect(gray)
    val gradient = GradientEffect(color1, color2, tint)
    return if (grayscaleFirst) grayscale + gradient else gradient + grayscale
}

@Composable
internal fun ChainTab() {
    var grayFirst by rememberSaveable { mutableStateOf(true) }
    var gray by rememberSaveable { mutableFloatStateOf(1f) }
    var tint by rememberSaveable { mutableFloatStateOf(0.85f) }
    var warm by rememberSaveable { mutableStateOf(true) }
    val factory = rememberShaderFactory()
    val chain = remember(grayFirst, gray, tint, warm) {
        orderedEffects(grayFirst, gray, tint, if (warm) 0xFFFF6644 else 0xFF44FFAA, 0xFF4466FF)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Order changes color", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { grayFirst = !grayFirst }) { Text("Swap order") }
        }
        // Three real renders of the same image: no screenshot or simulated intermediate stage.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewCard("Original", null, factory, Modifier.weight(1f))
            PreviewCard(if (grayFirst) "1. Grayscale" else "1. Gradient", chain.effects[0], factory, Modifier.weight(1f))
            PreviewCard(if (grayFirst) "2. Gradient" else "2. Grayscale", chain, factory, Modifier.weight(1f))
        }
        SamplerSlider("Grayscale amount", gray, { gray = it })
        SamplerSlider("Gradient amount", tint, { tint = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = warm, onClick = { warm = !warm }, label = { Text(if (warm) "Warm gradient" else "Cool gradient") })
            TextButton(onClick = { grayFirst = true; gray = 1f; tint = 0.85f; warm = true }) { Text("Reset") }
        }
        Details {
            Text("Each card renders with ShaderX. The middle card stops after the first effect; the final card applies both in order.")
            Text("val chain = GrayscaleEffect(gray) + GradientEffect(intensity = tint)\nModifier.shaderEffect(chain, factory)",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
