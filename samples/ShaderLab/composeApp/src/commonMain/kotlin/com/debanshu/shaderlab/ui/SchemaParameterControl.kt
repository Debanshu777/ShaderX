package com.debanshu.shaderlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/** A huge finite interval is a valid schema bound, but cannot form a useful slider. */
internal fun FloatParameterDefinition.hasUsableSlider(): Boolean = range?.let {
    val extent = it.endInclusive - it.start
    extent.isFinite() && extent > 0f
} ?: false

internal fun parseControlFloat(text: String, definition: FloatParameterDefinition): Float {
    val value = text.trim().toFloatOrNull()
    require(value != null && value.isFinite()) { "Enter a finite number using a decimal point (for example 0.125)." }
    require(definition.range?.contains(value) != false) {
        "Enter a value from ${definition.range!!.start} to ${definition.range!!.endInclusive}."
    }
    return value
}

internal fun parseControlArgb(text: String): Long {
    val digits = text.trim().removePrefix("#")
    require(digits.length == 8 && digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "Enter eight hexadecimal digits: AARRGGBB, including alpha."
    }
    return digits.toLong(16)
}

@Composable
internal fun SchemaParameterControl(
    effectId: String,
    parameter: ParameterDefinition<*>,
    value: ParameterValue,
    onValueChange: (ParameterValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = parameter.metadata.label
    var expanded by remember(effectId, parameter.key.id) { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (parameter) {
            is BooleanParameterDefinition -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = (value as ParameterValue.BooleanValue).enabled,
                        onCheckedChange = { onValueChange(ParameterValue.BooleanValue(it)) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            is FloatParameterDefinition -> {
                val number = (value as ParameterValue.FloatValue).value
                val slider = parameter.hasUsableSlider()
                if (slider) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label + (parameter.unit?.let { " ($it)" } ?: ""), modifier = Modifier.weight(1f))
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide exact" else "Exact · $number") }
                    }
                    Slider(value = number.coerceIn(parameter.range!!),
                        onValueChange = { onValueChange(ParameterValue.FloatValue(it)) }, valueRange = parameter.range!!,
                        modifier = Modifier.semantics { contentDescription = "$label slider" })
                }
                // Keep the text draft alive while exact entry is collapsed.
                ScalarTextEntry(
                    key = "$effectId/${parameter.key.id}", label = label + (parameter.unit?.let { " ($it)" } ?: ""),
                    exactValue = number.toString(),
                    hint = "${parameter.decimalPlaces} display decimals" +
                        (parameter.step?.let { " · suggested step $it" } ?: "") +
                        (parameter.range?.let { " · ${it.start} to ${it.endInclusive}" } ?: " · no bounds"),
                    onApply = { onValueChange(ParameterValue.FloatValue(parseControlFloat(it, parameter))) },
                    visible = expanded || !slider,
                )
            }
            is ColorParameterDefinition -> {
                val color = (value as ParameterValue.ColorValue).color
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(28.dp).background(Color(color)).semantics {
                        contentDescription = "$label color preview, ARGB ${color.toString(16)}"
                    })
                    Text(label, modifier = Modifier.weight(1f))
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide ARGB" else "Edit ARGB") }
                }
                ScalarTextEntry(
                    key = "$effectId/${parameter.key.id}", label = "$label (ARGB)",
                    exactValue = color.toString(16).uppercase().padStart(8, '0'),
                    hint = "AARRGGBB · alpha 00 transparent, FF opaque",
                    onApply = { onValueChange(ParameterValue.ColorValue(parseControlArgb(it))) }, visible = expanded,
                )
            }
        }
        if (parameter is BooleanParameterDefinition && (parameter.metadata.description != null || parameter.metadata.semanticRole != null)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide details" else "Control details") }
        }
        if (expanded || parameter is FloatParameterDefinition && !parameter.hasUsableSlider()) {
            parameter.metadata.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            parameter.metadata.semanticRole?.let { Text("Role: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Edits stay as text until Apply/Done, allowing intermediate '-', decimals and exponents. */
@Composable
private fun ScalarTextEntry(
    key: String,
    label: String,
    exactValue: String,
    hint: String,
    onApply: (String) -> Unit,
    visible: Boolean = true,
) {
    var draft by remember(key, exactValue) { mutableStateOf(exactValue) }
    var error by remember(key, exactValue) { mutableStateOf<String?>(null) }
    val apply = {
        try {
            onApply(draft)
            error = null
        } catch (invalid: IllegalArgumentException) {
            error = invalid.message ?: "Invalid value"
        }
    }
    if (!visible) return
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it; error = null },
        label = { Text(label) },
        supportingText = { Text(error ?: hint) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { apply() }),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = apply, enabled = draft != exactValue) { Text("Apply $label") }
}
