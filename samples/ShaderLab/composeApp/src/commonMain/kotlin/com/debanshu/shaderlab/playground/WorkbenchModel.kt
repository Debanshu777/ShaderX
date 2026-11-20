package com.debanshu.shaderlab.playground

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*

object WorkbenchLimits {
    const val SOURCE_BYTES = 32768
    const val PROJECT_BYTES = 131072
    const val PARAMETERS = 32
    const val IMAGE_BYTES = 1024 * 1024
    const val IMAGE_DIMENSION = 256
    const val PREVIEW_DIMENSION = 256
    const val CACHE_SIZE = 4
    fun image(bytes: Int, width: Int, height: Int) {
        require(bytes in 1..IMAGE_BYTES && width in 1..IMAGE_DIMENSION && height in 1..IMAGE_DIMENSION) {
            "Image exceeds 1 MiB or 256×256 pixels; resize it before loading"
        }
    }
}

enum class WorkbenchOutput { CONTENT, BACKGROUND }

/** Rebinding descriptions only. Files never contain application data, native handles or callbacks. */
enum class WorkbenchInputKind { IMAGE, DATA, NESTED, FRAME_FLOAT, FRAME_IMAGE, FRAME_DATA, FRAME_NESTED }
data class WorkbenchInputRequirement(
    val name: String,
    val kind: WorkbenchInputKind,
    val sampling: DataSampling = DataSampling.NEAREST,
    val tileModeX: ShaderTileMode = ShaderTileMode.CLAMP,
    val tileModeY: ShaderTileMode = ShaderTileMode.CLAMP,
) {
    val hint: String get() = when (kind) {
        WorkbenchInputKind.DATA, WorkbenchInputKind.FRAME_DATA -> "Rebind immutable RGBA8 UNORM data; alpha is data. Raw backend support required; unavailable on Skia. Sample texel centers at x + 0.5, y + 0.5."
        WorkbenchInputKind.NESTED, WorkbenchInputKind.FRAME_NESTED -> "Rebind a source-free ShaderNode snapshot with explicit independent time and inputs; parent clock is not inherited."
        WorkbenchInputKind.IMAGE, WorkbenchInputKind.FRAME_IMAGE -> "Rebind a caller-owned color-managed image; retain it while rendering uses it."
        WorkbenchInputKind.FRAME_FLOAT -> "Rebind a finite scalar through the retained frame provider."
    }
}

enum class WorkbenchParameterType { FLOAT, COLOR, BOOLEAN }
data class WorkbenchParameter(
    val type: WorkbenchParameterType = WorkbenchParameterType.FLOAT,
    val id: String = "intensity", val label: String = "Intensity",
    val defaultValue: String = "0.5", val minimum: String = "0", val maximum: String = "1",
    val uniformName: String = "intensity",
)

/** Editable text is intentionally retained even when it cannot form a valid SDK definition. */
data class WorkbenchDraft(
    val source: String,
    val parameters: List<WorkbenchParameter> = emptyList(),
    val animated: Boolean = false,
    val resolutionName: String? = null,
    val timeName: String? = null,
    val imageName: String? = null,
    val currentValues: Map<String, ParameterValue> = emptyMap(),
    val output: WorkbenchOutput = WorkbenchOutput.CONTENT,
    val inputs: List<WorkbenchInputRequirement> = emptyList(),
) {
    fun checkBounds() {
        require(source.length <= WorkbenchLimits.SOURCE_BYTES && source.encodeToByteArray().size <= WorkbenchLimits.SOURCE_BYTES) { "Source exceeds 32 KiB" }
        require(inputs.size <= WorkbenchLimits.PARAMETERS && inputs.map { it.name }.distinct().size == inputs.size) { "At most 32 distinct input requirements are supported" }
        require(currentValues.size <= WorkbenchLimits.PARAMETERS) { "At most 32 current values are supported" }
        require(parameters.size <= WorkbenchLimits.PARAMETERS) { "At most 32 parameters are supported" }
        val fields = parameters.flatMap { listOf(it.id, it.label, it.defaultValue, it.minimum, it.maximum, it.uniformName) } +
            listOfNotNull(resolutionName, timeName, imageName) + currentValues.keys + inputs.map { it.name }
        require(fields.all { it.length <= 256 }) { "Form fields must be at most 256 characters" }
    }

    /** ShaderX builds the exact definition later rendered and exported. No Kotlin execution. */
    fun build(
        image: ImageBitmap? = null,
        reboundInputs: Map<String, Uniform> = emptyMap(),
        frameProvider: ShaderFrameProvider? = null,
    ): PlaygroundProject {
        checkBounds()
        val id = "workbench"
        val declarations = parameters.map { parameter ->
            val metadata = ParameterMetadata(parameter.label)
            when (parameter.type) {
                WorkbenchParameterType.FLOAT -> {
                    fun number(value: String): Float = value.toFloatOrNull()?.takeIf { it.isFinite() }
                        ?: throw IllegalArgumentException("Parameter '${parameter.id}' needs finite numeric values")
                    require(parameter.minimum.isBlank() == parameter.maximum.isBlank()) { "Parameter '${parameter.id}' needs both range bounds or neither" }
                    FloatParameterDefinition(id, parameter.id, number(parameter.defaultValue),
                        if (parameter.minimum.isBlank()) null else number(parameter.minimum)..number(parameter.maximum), metadata)
                }
                WorkbenchParameterType.COLOR -> {
                    require(parameter.defaultValue.matches(Regex("[0-9a-fA-F]{8}"))) { "Color '${parameter.id}' needs eight ARGB hexadecimal digits" }
                    ColorParameterDefinition(id, parameter.id, parameter.defaultValue.toLong(16), metadata)
                }
                WorkbenchParameterType.BOOLEAN -> BooleanParameterDefinition(id, parameter.id,
                    when (parameter.defaultValue) { "true" -> true; "false" -> false; else -> error("Boolean '${parameter.id}' needs true or false") }, metadata)
            }
        }
        val expected = linkedMapOf<String, String>().apply { if (output == WorkbenchOutput.CONTENT) put("content", "shader") }
        fun expect(name: String, kind: String) { require(expected.put(name, kind) == null) { "Duplicate or reserved binding '$name'" } }
        val bindings = declarations.mapIndexed { index, declaration ->
            val name = parameters[index].uniformName
            when (declaration) {
                is FloatParameterDefinition -> { expect(name, "float"); ShaderBinding.float(name, declaration.key) }
                is ColorParameterDefinition -> { expect(name, "color"); ShaderBinding.color(name, declaration.key) }
                is BooleanParameterDefinition -> { expect(name, "int"); ShaderBinding.boolean(name, declaration.key) }
            }
        }.toMutableList()
        resolutionName?.let { expect(it, "float2"); bindings += ShaderBinding.resolution(it) }
        timeName?.let { require(animated) { "Time requires an animated draft" }; expect(it, "float"); bindings += ShaderBinding.time(it) }
        imageName?.let {
            expect(it, "shader")
            requireNotNull(image) { "Image input '$it' requires a local image; image handles are never stored in project files" }
            WorkbenchLimits.image(1, image.width, image.height)
            bindings += ShaderBinding.image(it, image, ShaderTileMode.REPEAT, ShaderTileMode.MIRROR)
        }
        val structuralNames = inputs.filter { !it.kind.name.startsWith("FRAME_") }.map { it.name }.toSet()
        require(reboundInputs.keys == structuralNames) { "Rebind exactly these explicit input requirements: $structuralNames" }
        for (input in inputs) {
            expect(input.name, if (input.kind == WorkbenchInputKind.FRAME_FLOAT) "float" else "shader")
            val uniform = reboundInputs[input.name]
            if (uniform != null) require(uniform.name == input.name) { "Rebound input name must match '${input.name}'" }
            bindings += when (input.kind) {
                WorkbenchInputKind.IMAGE -> ShaderBinding.image(input.name,
                    (uniform as? TextureUniform)?.image ?: error(input.hint), input.tileModeX, input.tileModeY)
                WorkbenchInputKind.DATA -> ShaderBinding.data(input.name,
                    (uniform as? DataUniform)?.data ?: error(input.hint), input.sampling, input.tileModeX, input.tileModeY)
                WorkbenchInputKind.NESTED -> ShaderBinding.nested(input.name,
                    (uniform as? NestedUniform)?.input ?: error(input.hint))
                WorkbenchInputKind.FRAME_FLOAT -> ShaderBinding.frameFloat(input.name)
                WorkbenchInputKind.FRAME_IMAGE -> ShaderBinding.frameImage(input.name)
                WorkbenchInputKind.FRAME_DATA -> ShaderBinding.frameData(input.name)
                WorkbenchInputKind.FRAME_NESTED -> ShaderBinding.frameNested(input.name)
            }
        }
        require(inputs.none { it.kind.name.startsWith("FRAME_") } || animated) { "Live inputs require an animated draft" }
        WorkbenchDeclarations.validate(source, expected)
        val definition = ShaderDefinition(id, "Workbench effect", ShaderProgram(source, bindings), ParameterSchema(id, declarations))
        if (output == WorkbenchOutput.BACKGROUND) definition.proceduralBackground()
        var values = definition.schema.defaults()
        for ((key, value) in currentValues) values = values.withValue(key, value)
        val effect = if (animated) definition.animatedEffect(values, frameProvider = frameProvider) else definition.effect(values)
        return PlaygroundProject(PlaygroundEntry(effect, "Workbench"), effect)
    }
}

/** A run captures all draft/configuration state. Late input/render callbacks must retain this token. */
class WorkbenchRun internal constructor(val revision: Long, val draft: WorkbenchDraft) {
    internal var candidate: PlaygroundProject? = null; private set
    fun build(image: ImageBitmap? = null, reboundInputs: Map<String, Uniform> = emptyMap(), frameProvider: ShaderFrameProvider? = null): PlaygroundProject =
        draft.build(image, reboundInputs, frameProvider).also { candidate = it }
}
data class WorkbenchGood(val revision: Long, val draft: WorkbenchDraft, val project: PlaygroundProject)
class WorkbenchState(initial: WorkbenchDraft = WorkbenchStarters.tint) {
    var draft: WorkbenchDraft = initial.copy(parameters = initial.parameters.toList(), currentValues = initial.currentValues.toMap(), inputs = initial.inputs.toList()); private set
    var revision: Long = 0; private set
    var lastGood: WorkbenchGood? = null; private set
    var error: String? = null; private set
    val stale: Boolean get() = lastGood?.revision != revision
    fun edit(next: WorkbenchDraft) { next.checkBounds(); draft = next.copy(parameters = next.parameters.toList(), currentValues = next.currentValues.toMap(), inputs = next.inputs.toList()); revision++; error = null }
    /** A structural form edit drops only that declaration's overridden value when its identity,
     * kind or default changes. Unrelated exact values survive; source/binding validation still
     * occurs on Run and an invalid range does not silently clamp a retained value. */
    fun editParameter(index: Int, next: WorkbenchParameter) {
        val previous = draft.parameters[index]
        val changedValue = previous.id != next.id || previous.type != next.type || previous.defaultValue != next.defaultValue
        edit(draft.copy(parameters = draft.parameters.toMutableList().also { it[index] = next },
            currentValues = if (changedValue) draft.currentValues - previous.id else draft.currentValues))
    }
    fun removeParameter(index: Int) {
        val previous = draft.parameters[index]
        edit(draft.copy(parameters = draft.parameters.filterIndexed { i, _ -> i != index }, currentValues = draft.currentValues - previous.id))
    }
    fun setValue(id: String, value: ParameterValue) { edit(draft.copy(currentValues = draft.currentValues + (id to value))) }
    fun beginRun(): WorkbenchRun = WorkbenchRun(revision, draft)
    /** Call only after actual backend creation AND rendered preview success. */
    fun promote(run: WorkbenchRun, project: PlaygroundProject): Boolean {
        if (run.revision != revision || run.draft != draft || run.candidate !== project) return false
        lastGood = WorkbenchGood(run.revision, run.draft, project); error = null
        return true
    }
    fun fail(run: WorkbenchRun, raw: String): Boolean {
        if (run.revision != revision) return false
        error = raw; return true
    }
    /** Current values are a new revision: they must pass render success before replacing export. */
    fun valuesChanged(): WorkbenchRun { revision++; error = null; return beginRun() }
    fun reset(next: WorkbenchDraft = WorkbenchStarters.tint) { edit(next); lastGood = null }
    fun accepts(run: WorkbenchRun): Boolean = run.revision == revision && run.draft == draft
}

object WorkbenchStarters {
    val background = WorkbenchDraft("""
        uniform float2 resolution;
        uniform float intensity;
        half4 main(float2 p) {
            float x = p.x / resolution.x;
            half alpha = half(intensity);
            return half4(half3(x, 0.2, 1.0 - x) * alpha, alpha);
        }
    """.trimIndent(), listOf(WorkbenchParameter(defaultValue = "0.65")),
        resolutionName = "resolution", output = WorkbenchOutput.BACKGROUND)

    val tint = WorkbenchDraft("""
        uniform shader content;
        uniform float intensity;
        half4 main(float2 p) {
            half4 sampled = content.eval(p);
            half3 warm = sampled.rgb * half3(1.0, 0.72, 0.45);
            return half4(mix(sampled.rgb, warm, intensity), sampled.a);
        }
    """.trimIndent(), listOf(WorkbenchParameter()))
    val distortion = WorkbenchDraft("""
        uniform shader content;
        uniform float2 resolution;
        uniform float amplitude;
        half4 main(float2 p) {
            float offset = sin(p.y / resolution.y * 12.0) * amplitude;
            return content.eval(p + float2(offset, 0.0));
        }
    """.trimIndent(), listOf(WorkbenchParameter(id = "amplitude", label = "Amplitude (pixels)", defaultValue = "8", maximum = "32", uniformName = "amplitude")), resolutionName = "resolution")
    val animated = distortion.copy(source = distortion.source.replace("uniform float amplitude;", "uniform float amplitude;\nuniform float time;").replace("* 12.0)", "* 12.0 + time)"), animated = true, timeName = "time")
    val image = tint.copy(source = """
        uniform shader content;
        uniform shader imageInput;
        uniform float intensity;
        half4 main(float2 p) {
            half4 sampled = content.eval(p);
            return mix(sampled, imageInput.eval(p), intensity);
        }
    """.trimIndent(), imageName = "imageInput")
}
