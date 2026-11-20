package com.debanshu.shaderlab.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.compose.shaderBackground
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import shaderxproject.samples.shaderlab.composeapp.generated.resources.Res
import kotlin.time.TimeSource

/** Retained editor data only; factories, sessions, clocks and pending runs stay panel-local. */
internal class WorkbenchWorkspace {
    val model = WorkbenchState()
    var version by mutableIntStateOf(0)
    var status by mutableStateOf("")
    var projectText by mutableStateOf("")
    var originalBytes by mutableStateOf<ByteArray?>(null)
    var originalBitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var rendered by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var input by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var playbackPosition = 0.0
    var playbackPlaying = false
    var playbackSpeed = 1.0
}

/** Sample-only authoring surface. A rendered image and its SDK snapshot are promoted together. */
@Composable
internal fun WorkbenchPanel(workspace: WorkbenchWorkspace = remember { WorkbenchWorkspace() }) {
    val state = workspace.model
    var version by workspace::version
    @Suppress("UNUSED_VARIABLE") val observedVersion = version
    var status by workspace::status
    var projectText by workspace::projectText
    var original by workspace::originalBytes
    var rendered by workspace::rendered
    var originalBitmap by workspace::originalBitmap
    var input by workspace::input
    val attached = remember { mutableStateOf(true) }
    var pendingBackground by remember { mutableStateOf<Pair<WorkbenchRun, PlaygroundProject>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var runningRevision by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.revision) {
        if (pendingBackground?.first?.revision?.let { it != state.revision } == true) {
            pendingBackground = null; busy = false; runningRevision = null
        }
    }
    val scope = rememberCoroutineScope()
    val storage = rememberPresetStorage("workbench")
    val clipboard = LocalClipboardManager.current
    val factory = rememberShaderFactory(maxCacheSize = WorkbenchLimits.CACHE_SIZE)
    val renderFactory = rememberShaderFactory(maxCacheSize = WorkbenchLimits.CACHE_SIZE)
    val playback = rememberShaderPlayback(workspace.playbackPosition, workspace.playbackPlaying, workspace.playbackSpeed)
    DisposableEffect(workspace, playback) {
        onDispose {
            attached.value = false
            workspace.playbackPosition = playback.positionSeconds
            workspace.playbackPlaying = playback.isPlaying
            workspace.playbackSpeed = playback.speed
            playback.isPlaying = false
        }
    }
    val inputRequirements = buildSet {
        if (state.draft.imageName != null) add(ShaderInput.IMAGE_TEXTURE)
        for (input in state.draft.inputs) when (input.kind) {
            WorkbenchInputKind.IMAGE, WorkbenchInputKind.FRAME_IMAGE -> add(ShaderInput.IMAGE_TEXTURE)
            WorkbenchInputKind.DATA, WorkbenchInputKind.FRAME_DATA -> add(ShaderInput.RAW_DATA_TEXTURE)
            WorkbenchInputKind.NESTED, WorkbenchInputKind.FRAME_NESTED -> add(ShaderInput.NESTED_SHADER)
            WorkbenchInputKind.FRAME_FLOAT -> Unit
        }
    }
    val runSupport = factory.querySupport(if (state.draft.output == WorkbenchOutput.BACKGROUND) ShaderOperation.BACKGROUND_RENDER else ShaderOperation.IMAGE_EXPORT,
        inputs = inputRequirements)
    fun attempt(action: () -> Unit) { try { action() } catch (e: Exception) { status = e.message ?: "Operation failed" }; version++ }
    fun edit(next: WorkbenchDraft) = attempt { pendingBackground = null; busy = false; state.edit(next); status = "Draft changed. Run to validate and render." }
    fun render(run: WorkbenchRun) {
        if (run.draft.output == WorkbenchOutput.BACKGROUND) {
            try {
                pendingBackground = run to run.build(input)
                runningRevision = run.revision
                busy = true
                status = "Creating background on the active Compose backend…"
            } catch (e: Exception) { state.fail(run, e.message ?: "Background failed"); busy = false; version++ }
            return
        }
        pendingBackground = null
        runningRevision = run.revision
        busy = true
        scope.launch {
            val start = TimeSource.Monotonic.markNow()
            try {
                val bytes = original ?: error("Preview input is loading; try Run again")
                val candidate = run.build(input)
                // A composition-owned, bounded factory is used serially on this UI thread.
                // rememberShaderFactory closes it when this workbench leaves composition.
                val output = ImageProcessor.create(renderFactory).process(bytes, candidate.effect)
                when (output) {
                    is ShaderResult.Failure -> { if (attached.value && state.fail(run, output.error.message)) status = "Run failed; editable draft retained." }
                    is ShaderResult.Success -> {
                        val decoded = decodeWorkbenchImage(output.value)
                        require(decoded.width <= 256 && decoded.height <= 256)
                        if (attached.value && state.promote(run, candidate)) {
                            rendered = decoded
                            status = "Rendered with ShaderX backend in ${start.elapsedNow().inWholeMilliseconds} ms · 256px input. Browser means Skia only."
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { if (attached.value && state.fail(run, e.message ?: "Run failed")) status = "Run failed; draft retained." }
            finally { if (runningRevision == run.revision) { busy = false; runningRevision = null }; version++ }
        }
    }
    LaunchedEffect(Unit) {
        if (original != null) return@LaunchedEffect
        try {
            val bytes = Res.readBytes("files/workbench.png")
            if (!attached.value) return@LaunchedEffect
            validateWorkbenchPng(bytes)
            originalBitmap = decodeWorkbenchImage(bytes)
            original = bytes; if (input == null) input = originalBitmap
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { if (attached.value) status = e.message ?: "Could not load preview input" }
    }
    val picker = rememberWorkbenchImagePicker()
    val editor: @Composable () -> Unit = {
        OutlinedTextField(state.draft.source, { edit(state.draft.copy(source = it)) }, label = { Text("Shader source (SkSL / AGSL)") }, modifier = Modifier.fillMaxWidth(), minLines = 8, maxLines = 18, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WorkbenchOutput.entries.forEach { output ->
                val valid = runCatching { state.draft.copy(output = output).build(input) }.isSuccess
                TextButton(enabled = valid, onClick = { edit(state.draft.copy(output = output)) }) {
                    Text(if (state.draft.output == output) "${output.name.lowercase()} · selected" else output.name.lowercase())
                }
            }
        }
        if (runSupport.status == ShaderSupportStatus.UNSUPPORTED) Text("Run unavailable on this backend: ${runSupport.message}. Use the browser/desktop Skia workbench. Android ImageProcessor is unsupported; no AGSL validation is claimed.")
        Button(enabled = !busy && (state.draft.output == WorkbenchOutput.BACKGROUND || original != null) && runSupport.status != ShaderSupportStatus.UNSUPPORTED, onClick = { render(state.beginRun()) }) { Text(if (busy) "Running…" else "Run shader") }
        if (status.isNotEmpty()) SelectionContainer { Text(status) }
        state.error?.let { SelectionContainer { Text("Diagnostics (raw, no inferred locations):\n$it", color = MaterialTheme.colorScheme.error) } }
        Text(if (state.lastGood == null) "Run shader to preview this draft." else if (state.stale) "STALE · preview and Kotlin show revision ${state.lastGood!!.revision}. Run to validate draft ${state.revision}." else "Preview and Kotlin current · revision ${state.revision}", style = MaterialTheme.typography.titleSmall)
    }
    val preview: @Composable () -> Unit = {
        if (state.lastGood?.draft?.output != WorkbenchOutput.BACKGROUND) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Original", style = MaterialTheme.typography.labelMedium)
                originalBitmap?.let { Image(it, "Original input", modifier = Modifier.fillMaxWidth().height(180.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Last successful result", style = MaterialTheme.typography.labelMedium)
                val bitmap = rendered
                if (bitmap != null) Image(bitmap, "Last successful rendered result", modifier = Modifier.fillMaxWidth().height(180.dp))
                else Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("Run to preview", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val backgroundCandidate = pendingBackground
        val backgroundGood = state.lastGood?.takeIf { it.draft.output == WorkbenchOutput.BACKGROUND }
        val backgroundProject = backgroundCandidate?.second ?: backgroundGood?.project
        backgroundProject?.let { project ->
            BackgroundWorkbenchPreview(project, factory, playback,
                onReady = {
                    if (attached.value && backgroundCandidate != null && state.promote(backgroundCandidate.first, project)) {
                        pendingBackground = null
                        busy = false
                        status = "Background drawn by active Compose backend; foreground is drawn unchanged. GPU presentation is not measured."
                        version++
                    }
                },
                onError = { error ->
                    if (attached.value && backgroundCandidate != null && state.fail(backgroundCandidate.first, error.message)) {
                        pendingBackground = null; busy = false; status = "Background failed; last-good output retained."; version++
                    } else if (attached.value && backgroundCandidate == null) status = "Background preview error: ${error.message}"
                })
        }
        if (state.lastGood?.draft?.output != WorkbenchOutput.BACKGROUND) Text("Time-zero snapshot", style = MaterialTheme.typography.labelSmall)
        state.lastGood?.let { good ->
            if (good.draft.animated) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { playback.isPlaying = !playback.isPlaying }) { Text(if (playback.isPlaying) "Pause animation" else "Play animation") }; TextButton(onClick = { playback.isPlaying = false; playback.seekTo(0.0) }) { Text("Reset time") } }
                if (good.draft.output == WorkbenchOutput.CONTENT) CompositionLocalProvider(LocalPlaygroundPlayback provides playback) {
                    originalBitmap?.let { bitmap ->
                        Image(bitmap, "Animated last-good preview", modifier = Modifier.fillMaxWidth().height(200.dp).clipToBounds().controlledShaderEffect(
                            good.project.effect as com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect,
                            playback, factory = factory,
                            onError = { error -> if (attached.value && state.revision == good.revision) status = "Animated preview error: ${error.message}" },
                        ))
                    }
                }
            }
            Text("Validated controls · edits render a new value revision")
            good.project.entry.schema.definitions.forEach { parameter ->
                SchemaParameterControl("workbench", parameter, good.project.snapshot().getValue(parameter.key.id), { value ->
                    if (!state.stale) {
                        state.setValue(parameter.key.id, value)
                        version++
                        render(state.beginRun())
                    } else status = "Run the edited source/form first, then adjust validated controls."
                })
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Custom shader workbench", style = MaterialTheme.typography.headlineSmall)
        Text("Edit source, Run, then tune the validated result.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { edit(WorkbenchStarters.tint) }) { Text("Warm tint") }
            TextButton(onClick = { edit(WorkbenchStarters.distortion) }) { Text("Distortion") }
            TextButton(onClick = { edit(WorkbenchStarters.animated) }) { Text("Animated") }
            TextButton(onClick = { edit(WorkbenchStarters.image) }) { Text("Image input") }
            TextButton(onClick = { edit(WorkbenchStarters.background) }) { Text("Background") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { editor() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { preview() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { editor(); preview() }
            }
        }
        HorizontalDivider()
        PlaygroundDisclosure("Declarations & inputs · ${state.draft.parameters.size} parameters") {
            Text("Content output requires content input; background output requires source-free code. Choices enable only when source and form match. Background pixels draw behind foreground; no surrounding UI capture.", style = MaterialTheme.typography.bodySmall)
            Text("Parameter declarations", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { edit(state.draft.copy(currentValues = emptyMap())) }) { Text("Use form defaults (clear current overrides)") }
            state.draft.parameters.forEachIndexed { index, parameter ->
                fun update(next: WorkbenchParameter) = attempt { state.editParameter(index, next); status = "Parameter changed. Run required." }
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { WorkbenchParameterType.entries.forEach { type -> TextButton(onClick = { update(parameter.copy(type = type, defaultValue = when(type) { WorkbenchParameterType.FLOAT -> "0.5"; WorkbenchParameterType.COLOR -> "FFFFAA55"; WorkbenchParameterType.BOOLEAN -> "true" })) }) { Text(type.name.lowercase()) } } }
                        WorkbenchField("Parameter ID", parameter.id) { update(parameter.copy(id = it)) }
                        WorkbenchField("Label", parameter.label) { update(parameter.copy(label = it)) }
                        WorkbenchField("Uniform binding", parameter.uniformName) { update(parameter.copy(uniformName = it)) }
                        WorkbenchField("Default (float / AARRGGBB / true or false)", parameter.defaultValue) { update(parameter.copy(defaultValue = it)) }
                        if (parameter.type == WorkbenchParameterType.FLOAT) {
                            WorkbenchField("Minimum (both bounds or neither)", parameter.minimum) { update(parameter.copy(minimum = it)) }
                            WorkbenchField("Maximum", parameter.maximum) { update(parameter.copy(maximum = it)) }
                        }
                        TextButton(onClick = { attempt { state.removeParameter(index); status = "Parameter removed. Run required." } }) { Text("Remove parameter") }
                    }
                }
            }
            TextButton(onClick = { edit(state.draft.copy(parameters = state.draft.parameters + WorkbenchParameter(id = "parameter${state.draft.parameters.size}", uniformName = "parameter${state.draft.parameters.size}"))) }) { Text("Add float parameter") }
            WorkbenchField("Resolution binding (blank = none)", state.draft.resolutionName ?: "") { edit(state.draft.copy(resolutionName = it.takeIf(String::isNotBlank))) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text("Animated"); Switch(state.draft.animated, { edit(state.draft.copy(animated = it)) }) }
            WorkbenchField("Time binding (blank = none)", state.draft.timeName ?: "") { edit(state.draft.copy(timeName = it.takeIf(String::isNotBlank))) }
            WorkbenchField("Image binding (blank = none)", state.draft.imageName ?: "") { edit(state.draft.copy(imageName = it.takeIf(String::isNotBlank))) }
            if (state.draft.imageName != null) {
                Text("Image starter uses the bundled 256px input by default. Sampling: REPEAT X / MIRROR Y. Uploaded PNGs: ≤1 MiB, ≤256×256; inputs are not saved in draft files.")
                if (picker != null) TextButton(onClick = { val request = state.valuesChanged(); version++
                    picker { result ->
                        if (attached.value && state.accepts(request)) attempt {
                            val bytes = result.getOrThrow(); validateWorkbenchPng(bytes)
                            val bitmap = decodeWorkbenchImage(bytes)
                            if (attached.value && state.accepts(request)) { input = bitmap; status = "Image loaded. Run required." }
                        }
                    } }) { Text("Choose image PNG") }
                else Text("Local image upload is available in the browser workbench; this host uses the bundled input.")
            }
            for (requirement in state.draft.inputs) {
                Text("${requirement.name}: ${requirement.hint} Draft files save descriptions only; bind application inputs in a host adapter before Run.")
            }
        }
        state.lastGood?.let { good ->
            PlaygroundDisclosure("Last-good Kotlin · revision ${good.revision}${if (state.stale) " · STALE" else ""}") {
                val code = remember(good) { runCatching { KotlinExport.generate(good.project.effect, good.draft.output) }.getOrElse { "Export unavailable: ${it.message}" } }
                Text("SDK output · LAST SUCCESSFUL rendered revision ${good.revision}. Requires ShaderX 0.2.0. Image/live arguments must be supplied by your app.")
                TextButton(onClick = { attempt { clipboard.setText(AnnotatedString(code)); status = "Copy requested; select the complete code below if clipboard access is denied." } }) { Text("Copy last-good Kotlin") }
                SelectionContainer { Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) { Text(code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) } }
            }
        }
        PlaygroundDisclosure("Draft recovery") {
            Text("Local draft recovery · source, form and current scalar values only. Last-good output and images are session-only. Restore never runs source automatically.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { attempt { projectText = WorkbenchProjectCodec.encode(state.draft); storage.save(projectText); status = "Draft saved locally" } }) { Text("Save draft") }
                TextButton(onClick = { attempt { val saved = storage.load() ?: error("No saved draft"); state.edit(WorkbenchProjectCodec.decode(saved)); projectText = saved; status = "Draft restored; Run required" } }) { Text("Restore draft") }
            }
            TextButton(onClick = { attempt { state.reset(); rendered = null; input = originalBitmap; playback.isPlaying = false; projectText = ""; storage.save(WorkbenchProjectCodec.encode(state.draft)); status = "Reset draft and local recovery slot to Warm tint" } }) { Text("Reset workbench") }
            TextButton(onClick = { attempt { projectText = WorkbenchProjectCodec.encode(state.draft); status = "Draft file text includes unvalidated source. It is separate from working Kotlin export." } }) { Text("Create draft file text (may be broken)") }
            OutlinedTextField(projectText, { if (it.length <= WorkbenchLimits.PROJECT_BYTES) projectText = it }, label = { Text("Draft project text") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
            TextButton(onClick = { attempt { state.edit(WorkbenchProjectCodec.decode(projectText)); status = "Draft imported. Run required." } }) { Text("Restore draft text") }
            if (storage.supportsFiles) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { attempt { storage.exportFile(WorkbenchProjectCodec.encode(state.draft)); status = "Unvalidated draft file download requested" } }) { Text("Download draft file") }
                TextButton(onClick = { val token = state.beginRun(); storage.importFile { result -> if (attached.value && state.accepts(token)) attempt { state.edit(WorkbenchProjectCodec.decode(result.getOrThrow())); status = "Draft file restored; Run required" } } }) { Text("Open draft file") }
            }
        }
        PlaygroundDisclosure("Authoring notes & limits") {
            Text("Manual Run on the native ShaderX backend. Browser preview validates Skia, not Android AGSL. No Kotlin execution or uploads to a server.", style = MaterialTheme.typography.bodySmall)
            Text("content.eval(p) reads content in physical pixels. resolution is float2 pixels; time is seconds. Preserve premultiplied alpha. Color parameters require layout(color) uniform half4. Images are color-managed; raw RGBA8 uses a separate input kind and requires raw backend support.", style = MaterialTheme.typography.bodySmall)
            Text("Bounds: 32 KiB source, 32 parameters, 128 KiB project, 256px preview input, four cached programs per factory (two workbench caches). Compilation/GPU work is synchronous and cannot be reliably timed out. For a stuck shader reload the page, reset the workbench, then restore a known draft. This is not an execution sandbox.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkbenchField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

/** The candidate and last-good snapshots use the same actual background modifier. */
@Composable
private fun BackgroundWorkbenchPreview(
    project: PlaygroundProject,
    factory: ShaderFactory,
    playback: com.debanshu.shaderlab.shaderx.playback.ShaderPlayback,
    onReady: () -> Unit,
    onError: (com.debanshu.shaderlab.shaderx.result.ShaderError) -> Unit,
) {
    val definition = when (val effect = project.effect) {
        is DefinedShaderEffect -> effect.definition
        is DefinedAnimatedShaderEffect -> effect.definition
        else -> error("Background requires a declared definition")
    }
    val background = remember(definition) { definition.proceduralBackground() }
    var nativeReady by remember(project) { mutableStateOf(false) }
    var drawReported by remember(project) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(200.dp).shaderBackground(background,
        values = definition.schema.valuesFrom(project.effect),
        playback = if (project.effect is DefinedAnimatedShaderEffect) playback else null,
        factory = factory, onError = onError, onReady = { nativeReady = true }).drawWithContent {
            // This inner callback runs after shaderBackground has issued its background draw.
            // It does not assert GPU presentation; the revision token still gates promotion.
            drawContent()
            if (nativeReady && !drawReported) { drawReported = true; onReady() }
        }, contentAlignment = Alignment.Center) {
        Text("Unfiltered foreground", color = androidx.compose.ui.graphics.Color.White)
    }
}
