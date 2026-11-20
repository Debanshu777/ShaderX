package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.*

/** Versioned local draft files. Source, form, current values and input descriptions only; no executable Kotlin,
 * borrowed images, runtime/session state or purported validation certificates are restored.
 * A broken draft may be saved, but restore never promotes it to last-good output. */
object WorkbenchProjectCodec {
    private const val HEADER = "ShaderLab-Workbench 3"
    fun encode(draft: WorkbenchDraft): String {
        draft.checkBounds()
        return buildString {
            appendLine(HEADER)
            appendLine(draft.output.name)
            appendLine(hex(draft.source))
            appendLine(draft.animated)
            appendLine(listOf(draft.resolutionName, draft.timeName, draft.imageName).joinToString(" ") { it?.let(::hex) ?: "-" })
            appendLine(draft.inputs.size)
            for (input in draft.inputs) appendLine(listOf(hex(input.name), input.kind.name, input.sampling.name, input.tileModeX.name, input.tileModeY.name).joinToString(" "))
            appendLine(draft.parameters.size)
            for (p in draft.parameters) appendLine((listOf(p.type.name) + listOf(p.id, p.label, p.defaultValue, p.minimum, p.maximum, p.uniformName).map(::hex)).joinToString(" "))
            appendLine(draft.currentValues.size)
            for ((key, value) in draft.currentValues) {
                append(hex(key)); append(' ')
                appendLine(when (value) {
                    is ParameterValue.FloatValue -> "f ${value.value}"
                    is ParameterValue.ColorValue -> "c ${value.color.toString(16)}"
                    is ParameterValue.BooleanValue -> "b ${value.enabled}"
                })
            }
        }.also { require(it.encodeToByteArray().size <= WorkbenchLimits.PROJECT_BYTES) { "Project exceeds 128 KiB" } }
    }

    /** Bounds and format are checked before replacing the user's draft. Invalid source/form stays
     * editable by design; only Run can validate declarations, native compilation and rendering. */
    fun decode(text: String): WorkbenchDraft {
        require(text.length <= WorkbenchLimits.PROJECT_BYTES && text.encodeToByteArray().size <= WorkbenchLimits.PROJECT_BYTES) { "Project exceeds 128 KiB" }
        val lines = text.removeSuffix("\n").split('\n')
        var cursor = 0
        fun row(): String = lines.getOrNull(cursor++) ?: error("Truncated workbench file")
        val header = row()
        require(header in setOf(HEADER, "ShaderLab-Workbench 2", "ShaderLab-Workbench 1")) { "Unknown workbench format/version" }
        val output = if (header != "ShaderLab-Workbench 1") {
            val name = row()
            WorkbenchOutput.entries.singleOrNull { it.name == name } ?: error("Unknown workbench output")
        } else WorkbenchOutput.CONTENT
        val source = unhex(row())
        val animated = boolean(row())
        val inputs = row().split(' ')
        require(inputs.size == 3) { "Malformed input bindings" }
        val inputNames = inputs.map { if (it == "-") null else unhex(it) }
        fun count(): Int = row().toIntOrNull()?.takeIf { it in 0..WorkbenchLimits.PARAMETERS } ?: error("Invalid parameter/value count")
        val requirements = if (header == HEADER) List(count()) {
            val fields = row().split(' ')
            require(fields.size == 5) { "Malformed input requirement" }
            WorkbenchInputRequirement(unhex(fields[0]),
                WorkbenchInputKind.entries.singleOrNull { it.name == fields[1] } ?: error("Unknown input kind"),
                DataSampling.entries.singleOrNull { it.name == fields[2] } ?: error("Unknown data sampling"),
                ShaderTileMode.entries.singleOrNull { it.name == fields[3] } ?: error("Unknown horizontal tile mode"),
                ShaderTileMode.entries.singleOrNull { it.name == fields[4] } ?: error("Unknown vertical tile mode"))
        } else emptyList()
        val parameters = List(count()) {
            val fields = row().split(' ')
            require(fields.size == 7) { "Malformed form row" }
            val type = WorkbenchParameterType.entries.singleOrNull { it.name == fields[0] } ?: error("Unknown form type")
            val p = fields.drop(1).map(::unhex)
            WorkbenchParameter(type, p[0], p[1], p[2], p[3], p[4], p[5])
        }
        val values = linkedMapOf<String, ParameterValue>()
        repeat(count()) {
            val fields = row().split(' ')
            require(fields.size == 3) { "Malformed current value" }
            val key = unhex(fields[0])
            val value = when (fields[1]) {
                "f" -> ParameterValue.FloatValue(fields[2].toFloatOrNull()?.takeIf { it.isFinite() } ?: error("Current float must be finite"))
                "c" -> ParameterValue.ColorValue(fields[2].toLongOrNull(16)?.takeIf { it in 0L..0xFFFFFFFFL } ?: error("Invalid ARGB value"))
                "b" -> ParameterValue.BooleanValue(boolean(fields[2]))
                else -> error("Unknown current value type")
            }
            require(values.put(key, value) == null) { "Duplicate current value" }
        }
        require(cursor == lines.size) { "Unexpected trailing workbench rows" }
        return WorkbenchDraft(source, parameters, animated, inputNames[0], inputNames[1], inputNames[2], values, output, requirements).also { it.checkBounds() }
    }
    private fun boolean(value: String): Boolean = when (value) { "true" -> true; "false" -> false; else -> error("Boolean requires true or false") }
    // '_' represents an empty editable field; UTF-8 hex makes newlines and separators unambiguous.
    private fun hex(value: String): String = if (value.isEmpty()) "_" else value.encodeToByteArray().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private fun unhex(value: String): String {
        if (value == "_") return ""
        require(value.isNotEmpty() && value.length % 2 == 0 && value.all { it in "0123456789abcdef" }) { "Malformed project text encoding" }
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }.decodeToString(throwOnInvalidSequence = true)
    }
}
