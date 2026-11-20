package com.debanshu.shaderlab.ui

import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.viewmodel.ImageSource

/** Draft data only. Native sessions and the playback driver exist only while the panel is shown. */
internal class PipelineWorkspace(registry: PlaygroundRegistry) {
    private var currentProject by mutableStateOf(PipelineProject(listOf(PipelineChild("child-1", PlaygroundProject(
        registry.entries.first { it.starter !is AudioVisualizerEffect && it.starter !is AudioDistortionEffect },
    )))))
    var code by mutableStateOf<String?>(null)
    var project: PipelineProject
        get() = currentProject
        set(value) { currentProject = value; code = null }
    var nextKey by mutableIntStateOf(2)
    var selected by mutableStateOf("child-1")
    var preset by mutableStateOf("")
    var status by mutableStateOf("")
    var playbackPosition = 0.0
    var playbackPlaying = false
    var playbackSpeed = 1.0
}

/** Uses the existing registry, controls and preview rather than duplicating the source workbench. */
@Composable
internal fun PipelinePanel(
    registry: PlaygroundRegistry,
    image: ImageSource,
    factory: ShaderFactory,
    workspace: PipelineWorkspace = remember(registry) { PipelineWorkspace(registry) },
) {
    val entries = registry.entries.filter { it.starter !is AudioVisualizerEffect && it.starter !is AudioDistortionEffect }
    var project by workspace::project
    var nextKey by workspace::nextKey
    var selected by workspace::selected
    var preset by workspace::preset
    var code by workspace::code
    var status by workspace::status
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val effect = remember(project) { project.effect() }
    val animated = project.children.any { it.project.effect is AnimatedShaderEffect }
    val playback = rememberShaderPlayback(workspace.playbackPosition, workspace.playbackPlaying, workspace.playbackSpeed, active = animated)
    DisposableEffect(workspace, playback) {
        onDispose {
            workspace.playbackPosition = playback.positionSeconds
            workspace.playbackPlaying = playback.isPlaying
            workspace.playbackSpeed = playback.speed
            playback.isPlaying = false
        }
    }
    fun attempt(action: () -> Unit) { try { action(); status = "" } catch (error: Exception) { status = error.message ?: "Pipeline operation failed" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ordered pipeline", style = MaterialTheme.typography.titleLarge)
        Text("Apply effects in order, then tune each step.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { project = CollectionPresets.printPipeline(registry); selected = project.children.first().key }) { Text("Color then four-tone print starter") }
        CompositionLocalProvider(LocalPlaygroundPlayback provides playback) {
            ShaderPreview(image, effect, factory, modifier = Modifier.fillMaxWidth().height(260.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = animated, onClick = { playback.isPlaying = !playback.isPlaying }) { Text(if (playback.isPlaying) "Pause pipeline" else "Play pipeline") }
            TextButton(onClick = { playback.isPlaying = false; playback.seekTo(0.0) }) { Text("Seek to start") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            project.children.forEachIndexed { index, child ->
                TextButton(onClick = { selected = child.key }) { Text("${index + 1}. ${child.project.effect.displayName}${if (selected == child.key) " (selected)" else ""}") }
            }
        }
        val child = project.children.firstOrNull { it.key == selected } ?: project.children.first()
        val index = project.children.indexOf(child)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = index > 0, onClick = { project = project.move(child.key, index - 1) }) { Text("Move earlier") }
            TextButton(enabled = index < project.children.lastIndex, onClick = { project = project.move(child.key, index + 1) }) { Text("Move later") }
            TextButton(enabled = project.children.size > 1, onClick = {
                project = PipelineProject(project.children.filter { it.key != child.key }); selected = project.children.first().key
            }) { Text("Remove") }
        }
        Box {
            TextButton(enabled = project.children.size < 16, onClick = { menu = true }) { Text("Add effect instance") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                entries.forEach { entry -> DropdownMenuItem(text = { Text(entry.starter.displayName) }, onClick = {
                    var key: String
                    do { key = "child-${nextKey++}" } while (project.children.any { it.key == key })
                    project = PipelineProject(project.children + PipelineChild(key, PlaygroundProject(entry))); selected = key; menu = false
                }) }
            }
        }
        Text("Edit ${child.project.effect.displayName} · ${child.key}", style = MaterialTheme.typography.titleMedium)
        val values = child.project.entry.schema.valuesFrom(child.project.effect)
        key(child.key) {
            pipelineControlDefinitions(child.project).forEach { parameter ->
                SchemaParameterControl(child.key, parameter, values.value(parameter.key.id), { value ->
                    attempt { project = project.update(child.key, parameter.key.id, value) }
                })
            }
        }
        HorizontalDivider()
        PlaygroundDisclosure("Pipeline preset") {
            Text("Version 1", style = MaterialTheme.typography.labelSmall)
            Text("A separate scalar format stores keys, order and each definition's schema version. Existing single-effect presets stay unchanged. Images and live inputs are excluded.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attempt { preset = PipelinePresetCodec.encode(project) } }) { Text("Create pipeline preset") }
            OutlinedTextField(value = preset, onValueChange = { if (it.length <= PipelinePresetCodec.MAX_BYTES) preset = it }, label = { Text("Pipeline preset text") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            TextButton(onClick = { attempt { val replacement = PipelinePresetCodec.decode(preset, registry); project = replacement; selected = replacement.children.first().key } }) { Text("Apply pipeline preset") }
        }
        PlaygroundDisclosure("Pipeline Kotlin") {
            TextButton(onClick = { attempt { code = PipelineKotlinExport.generate(project) } }) { Text("Generate pipeline Kotlin") }
            code?.let { text ->
                TextButton(onClick = { attempt { clipboard.setText(AnnotatedString(text)) } }) { Text("Copy pipeline Kotlin") }
                SelectionContainer { Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        PlaygroundDisclosure("Pipeline details") {
            Text("Children apply from first to last. Keys identify instances across reorder; repeated definitions keep independent values. The pipeline clock controls all animated children; child Animate flags are overridden.", style = MaterialTheme.typography.bodySmall)
            Text("Image export: ${factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect).message}", style = MaterialTheme.typography.bodySmall)
        }
        if (status.isNotEmpty()) SelectionContainer { Text(status, color = MaterialTheme.colorScheme.error) }
    }
}
