package com.debanshu.shaderx.sampler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect

@Composable
internal fun BasicTab() {
    var amount by rememberSaveable { mutableFloatStateOf(0.65f) }
    var compare by rememberSaveable { mutableStateOf(true) }
    val factory = rememberShaderFactory()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Color to grayscale", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compare) PreviewCard("Original", null, factory, Modifier.weight(1f))
            PreviewCard("Grayscale", GrayscaleEffect(amount), factory, Modifier.weight(1f))
        }
        SamplerSlider("Grayscale amount", amount, { amount = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = compare, onClick = { compare = !compare }, label = { Text("Compare original") })
            TextButton(onClick = { amount = 0.65f; compare = true }) { Text("Reset") }
        }
        Details {
            Text("One immutable effect and one modifier. The factory follows this screen's lifetime.")
            SelectionContainer {
                Text("val factory = rememberShaderFactory()\n" +
                    "val effect = GrayscaleEffect(amount)\n" +
                    "// Apply to your Image:\n" +
                    "Modifier.shaderEffect(effect, factory)", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
