package com.debanshu.shaderlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.playground.parameterSchema
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.viewmodel.ShaderLabTab

/** Primary tuning is small; exact typed values and reuse stay explicitly reachable. */
@Composable
fun ShaderControls(
    tabEffects: List<ShaderEffect>, activeEffect: ShaderEffect?, selectedTab: ShaderLabTab,
    onTabSelected: (ShaderLabTab) -> Unit, onEffectSelected: (ShaderEffect) -> Unit,
    onTypedParameterChanged: (String, ParameterValue) -> Unit, onClearEffect: () -> Unit,
    onToggleAnimation: () -> Unit = {}, onResetParameters: () -> Unit = {},
    extraControls: @Composable () -> Unit = {}, sidePanel: Boolean = false,
    modifier: Modifier = Modifier, onPresetSelected: (ShaderEffect) -> Unit = onEffectSelected,
) {
    val registry = LocalPlaygroundRegistry.current
    val playback = LocalPlaygroundPlayback.current
    var search by remember { mutableStateOf("") }
    var category by remember(selectedTab) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val categories = tabEffects.map { effect -> registry.entries.firstOrNull { it.id == effect.id }?.category ?: "Other" }.distinct()
    val matching = tabEffects.filter { effect ->
        val entry = registry.entries.firstOrNull { it.id == effect.id }
        val words = listOf(effect.id, effect.displayName, entry?.category.orEmpty()) +
            parameterSchema(effect).definitions.flatMap { listOf(it.metadata.label, it.metadata.description.orEmpty(), it.metadata.semanticRole.orEmpty()) }
        (category == null || (entry?.category ?: "Other") == category) && words.any { it.contains(search.trim(), true) }
    }
    val tabs = listOf(ShaderLabTab.STATIC, ShaderLabTab.ANIMATED, ShaderLabTab.AUDIO)
    val controls = activeEffect?.let { parameterSchema(it).definitions.sortedBy { definition -> definition.metadata.order } }.orEmpty()
    val header: @Composable () -> Unit = {
        TabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
            tabs.forEach { tab -> Tab(selected = selectedTab == tab, onClick = { onTabSelected(tab) },
                text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(activeEffect?.displayName ?: "Choose an effect", modifier = Modifier.weight(1f))
                Text("Change", style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                matching.forEach { effect ->
                    DropdownMenuItem(text = { Text(effect.displayName) }, onClick = { onEffectSelected(effect); menu = false })
                }
                if (matching.isEmpty()) DropdownMenuItem(text = { Text("No matches; clear Find effects") }, onClick = { search = ""; category = null })
            }
        }
        if (activeEffect != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (activeEffect is AnimatedShaderEffect) {
                    val playing = if (selectedTab == ShaderLabTab.AUDIO) activeEffect.isAnimating else playback?.isPlaying ?: activeEffect.isAnimating
                    TextButton(onClick = onToggleAnimation) { Text(if (playing) "Pause" else "Play") }
                }
                TextButton(onClick = onResetParameters) { Text("Reset") }
                TextButton(onClick = onClearEffect) { Text("Original") }
            }
            com.debanshu.shaderlab.playground.CollectionPresets.all.filter { it.definition.id == activeEffect.id }.forEach { preset ->
                TextButton(onClick = { onPresetSelected(preset.effect()) }) { Text("Try ${preset.label}") }
            }
        }
    }
    val more: @Composable () -> Unit = {
        PlaygroundDisclosure("Find effects") {
            OutlinedTextField(search, { search = it }, label = { Text("Name, parameter or role") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                categories.forEach { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
            }
            Text("${matching.size} matching effects · open Change to choose", style = MaterialTheme.typography.bodySmall)
        }
        extraControls()
    }
    Column(modifier.then(if (sidePanel) Modifier.fillMaxHeight() else Modifier)
        .background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sidePanel) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { header() }
                items(controls, key = { it.key.id }) { parameter ->
                    SchemaParameterControl(activeEffect!!.id, parameter, activeEffect.getTypedParameterValue(parameter.key.id)!!,
                        { onTypedParameterChanged(parameter.key.id, it) })
                }
                item { more() }
            }
        } else {
            header()
            controls.forEach { parameter ->
                SchemaParameterControl(activeEffect!!.id, parameter, activeEffect.getTypedParameterValue(parameter.key.id)!!,
                    { onTypedParameterChanged(parameter.key.id, it) })
            }
            more()
        }
    }
}
// Retained for the existing pilot contract tests. Editors no longer guess animation roles
// from IDs: every numeric declaration is shown with its actual semantic metadata.
internal fun AnimatedShaderEffect.primarySpeedParameterId(): String? =
    if (this is WaveEffect || this is DefinedAnimatedShaderEffect) null
    else parameters.find { it.id == "speed" }?.id
        ?: parameters.find { it.id == "frequency" }?.id
