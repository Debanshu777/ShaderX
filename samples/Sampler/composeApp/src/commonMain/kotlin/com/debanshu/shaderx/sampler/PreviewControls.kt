package com.debanshu.shaderx.sampler

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderx.sampler.resources.Res
import com.debanshu.shaderx.sampler.resources.sample_nature
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** Presentation only: the actual public SDK modifier stays visible here. */
@Composable
internal fun PreviewCard(title: String, effect: ShaderEffect?, factory: ShaderFactory, modifier: Modifier = Modifier) {
    var error by remember(effect) { mutableStateOf<String?>(null) }
    Card(modifier) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Image(painterResource(Res.drawable.sample_nature), title,
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp))
                    .shaderEffect(effect, factory = factory, onError = { error = it.message }))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun SamplerSlider(label: String, value: Float, onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f, displayValue: String? = null,
    onValueChangeFinished: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(displayValue ?: ((value * 100).roundToInt() / 100f).toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
internal fun Details(title: String = "How it works", content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide details" else title) }
        if (open) content()
    }
}
