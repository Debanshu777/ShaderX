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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema

// Share source and declarations; each immutable effect owns its editable values.
internal val strength = FloatParameterDefinition("warm_tint", "strength", 0.65f, 0f..1f)
internal val temperature = FloatParameterDefinition("warm_tint", "temperature", 1f, 0f..1f)
internal val warmTint = ShaderDefinition(
    id = "warm_tint",
    schema = ParameterSchema("warm_tint", listOf(strength, temperature)),
    program = ShaderProgram(
        source = """
            uniform shader content;
            uniform float strength;
            uniform float temperature;
            half4 main(float2 p) {
                half4 color = content.eval(p);
                half3 tint = mix(half3(0.4, 0.65, 1.0), half3(1.0, 0.65, 0.4), temperature);
                return half4(mix(color.rgb, color.rgb * tint, strength), color.a);
            }
        """.trimIndent(),
        bindings = listOf(ShaderBinding.float("strength", strength.key),
            ShaderBinding.float("temperature", temperature.key)),
    ),
)

@Composable
internal fun CustomTab() {
    var amount by rememberSaveable { mutableStateOf(0.65f) }
    var heat by rememberSaveable { mutableStateOf(1f) }
    val effect = remember(amount, heat) { warmTint.effect().withValue(strength.key, amount).withValue(temperature.key, heat) }
    var compare by rememberSaveable { mutableStateOf(true) }
    val factory = rememberShaderFactory()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Make your own color", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compare) PreviewCard("Original", null, factory, Modifier.weight(1f))
            PreviewCard("Custom tint", effect, factory, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = effect[temperature.key] == 1f,
                onClick = { heat = 1f }, label = { Text("Warm") })
            FilterChip(selected = effect[temperature.key] == 0f,
                onClick = { heat = 0f }, label = { Text("Cool") })
            TextButton(onClick = { amount = 0.65f; heat = 1f; compare = true }) { Text("Reset") }
        }
        SamplerSlider("Tint strength", effect[strength.key], { amount = it })
        SamplerSlider("Temperature · cool to warm", effect[temperature.key], { heat = it })
        FilterChip(selected = compare, onClick = { compare = !compare }, label = { Text("Compare original") })
        Details("See the custom shader") {
            SelectionContainer {
                Text(warmTint.program.source, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("Two float bindings connect the sliders to the source. Alpha passes through unchanged.")
        }
    }
}
