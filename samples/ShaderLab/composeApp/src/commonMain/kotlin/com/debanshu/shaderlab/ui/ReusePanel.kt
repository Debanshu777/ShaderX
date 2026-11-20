package com.debanshu.shaderlab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback

internal val LocalPlaygroundRegistry = staticCompositionLocalOf { PlaygroundRegistry.standard() }

internal val LocalPlaygroundPlayback = staticCompositionLocalOf<ShaderPlayback?> { null }

@Composable
internal fun ReusePanel(effect: ShaderEffect?, onLoad: (ShaderEffect) -> Unit) {
    val storage = rememberPresetStorage()
    val registry = LocalPlaygroundRegistry.current
    fun encode(effect: ShaderEffect) = PresetCodec.encode(effect, registry.entry(effect.id))
    fun decode(text: String) = PresetCodec.decode(text, registry.entries)
    val clipboard = LocalClipboardManager.current
    var status by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf("") }
    var codeStatus by remember(effect) { mutableStateOf("") }
    var code by remember(effect) { mutableStateOf<String?>(null) }
    fun attempt(action: () -> Unit) {
        try { action() } catch (error: Exception) { status = error.message ?: "Operation failed; selectable text remains available" }
    }
    val playback = LocalPlaygroundPlayback.current
    if (effect is AnimatedShaderEffect && effect !is com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect && effect !is com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect && playback != null) {
        PlaygroundDisclosure("Playback details") {
            PlaybackNumber("Seek seconds", 0.0, live = true) { playback.isPlaying = false; playback.seekTo(it) }
            PlaybackNumber("Playback multiplier", playback.speed) { require(it >= 0.0); playback.speed = it }
            Text("Seek pauses playback. Audio inputs remain live; presets contain scalar settings only.", style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
    PlaygroundDisclosure("Kotlin") {
        Text("Android runtime shaders require API 33+. Preview support is shown above.", style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = effect != null, onClick = { code = null; attempt { code = KotlinExport.generate(effect!!); status = ""; codeStatus = "Kotlin ready for selection or copy" } }) { Text("Generate Kotlin") }
        code?.let { text ->
            TextButton(onClick = { attempt { clipboard.setText(AnnotatedString(text)); codeStatus = "Copy requested; code stays selectable below" } }) { Text("Copy Kotlin") }
            SelectionContainer {
                Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    PlaygroundDisclosure("Presets") {
        Text("One local slot on this device/browser", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = effect != null, onClick = { attempt { preset = encode(effect!!); storage.save(preset); status = "Saved locally" } }) { Text("Save local") }
            TextButton(onClick = { attempt { val text = storage.load() ?: error("No local preset yet"); val loaded = decode(text); onLoad(loaded); preset = text; status = "Loaded local preset" } }) { Text("Load local") }
        }
        TextButton(enabled = effect != null, onClick = { attempt { preset = encode(effect!!); status = "Preset text ready" } }) { Text("Create preset text") }
        OutlinedTextField(value = preset, onValueChange = { if (it.length <= PresetCodec.MAX_BYTES) preset = it else status = "Preset exceeds size limit" }, label = { Text("Preset text (paste or select)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
        TextButton(onClick = { attempt { onLoad(decode(preset)); status = "Applied validated preset" } }) { Text("Apply preset text") }
        if (storage.supportsFiles) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { attempt { decode(preset); storage.exportFile(preset); status = "File export requested" } }) { Text("Save file") }
                TextButton(onClick = { attempt { storage.importFile { result -> attempt { val text = result.getOrThrow(); val loaded = decode(text); onLoad(loaded); preset = text; status = "Imported preset file" } } } }) { Text("Open file") }
            }
        } else Text("Native file dialogs are not supplied on Android/iOS. Use selectable preset text with your Files app; local save/load is available.", style = MaterialTheme.typography.bodySmall)
        Text("Images, microphone data and callbacks are excluded. Rebind inputs in the destination app. Kotlin is generated as text and never executed here.", style = MaterialTheme.typography.bodySmall)
    }
    if (codeStatus.isNotEmpty()) Text(codeStatus, style = MaterialTheme.typography.bodySmall)
    if (status.isNotEmpty()) SelectionContainer { Text(status, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun PlaybackNumber(label: String, value: Double, live: Boolean = false, onApply: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (live) "0.0" else value.toString()) }
    var invalid by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value = text, onValueChange = { text = it; invalid = false }, label = { Text(label) }, isError = invalid, modifier = Modifier.weight(1f), singleLine = true)
        TextButton(onClick = {
            val parsed = text.toDoubleOrNull()
            invalid = parsed == null || !parsed.isFinite() || (!live && parsed < 0.0)
            if (!invalid) {
                onApply(parsed!!)
            }
        }) { Text("Apply") }
    }
}
