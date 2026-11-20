package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.*

/** Sample-local scalar file format. No source, bitmap, callback, native state or secrets. */
object PresetCodec {
    const val MAX_BYTES = 65536
    const val MAX_PARAMETERS = 128
    private const val HEADER = "ShaderLab-Preset 1"

    fun encode(effect: ShaderEffect, entry: PlaygroundEntry = PlaygroundRegistry.standard().entry(effect.id)): String {
        require(effect.id == entry.id) { "Preset definition does not match selection" }
        val values = entry.schema.valuesFrom(effect)
        require(entry.schema.definitions.size <= MAX_PARAMETERS) { "Too many preset parameters" }
        return buildString {
            appendLine(HEADER)
            appendLine(hex(entry.id))
            appendLine(entry.schemaVersion)
            for (definition in entry.schema.definitions) {
                append(hex(definition.key.id)); append(' ')
                when (val value = values.value(definition.key.id)) {
                    is ParameterValue.FloatValue -> append("f ${value.value}")
                    is ParameterValue.ColorValue -> append("c ${value.color.toString(16).padStart(8, '0')}")
                    is ParameterValue.BooleanValue -> append("b ${value.enabled}")
                }
                append('\n')
            }
        }.also { require(it.encodeToByteArray().size <= MAX_BYTES) { "Preset exceeds $MAX_BYTES bytes" } }
    }

    /** Validate everything before returning a replacement. Failure never partially edits an effect. */
    fun decode(text: String, entries: List<PlaygroundEntry> = PlaygroundRegistry.standard().entries): ShaderEffect {
        require(text.length <= MAX_BYTES && text.encodeToByteArray().size <= MAX_BYTES) { "Preset exceeds $MAX_BYTES bytes" }
        val lines = text.removeSuffix("\n").split('\n')
        require(lines.size in 3..(MAX_PARAMETERS + 3)) { "Malformed preset or too many parameters" }
        require(lines[0] == HEADER) { "Unknown preset format/version; use a version 1 ShaderLab preset" }
        val id = unhex(lines[1])
        val entry = entries.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown definition '$id'; register it before loading this preset")
        require(lines[2] == entry.schemaVersion.toString()) { "Schema version changed; reset or migrate this preset" }
        val values = linkedMapOf<String, ParameterValue>()
        for (line in lines.drop(3)) {
            val parts = line.split(' ')
            require(parts.size == 3) { "Malformed parameter row" }
            val key = unhex(parts[0])
            val value = when (parts[1]) {
                "f" -> ParameterValue.FloatValue(parts[2].toFloatOrNull()?.takeIf { it.isFinite() }
                    ?: throw IllegalArgumentException("Parameter '$key' requires a finite float"))
                "c" -> {
                    require(parts[2].length == 8 && parts[2].all { it in "0123456789abcdefABCDEF" }) { "Color '$key' requires 8 ARGB hex digits" }
                    ParameterValue.ColorValue(parts[2].toLong(16))
                }
                "b" -> ParameterValue.BooleanValue(when (parts[2]) {
                    "true" -> true
                    "false" -> false
                    else -> throw IllegalArgumentException("Boolean '$key' requires true or false")
                })
                else -> throw IllegalArgumentException("Unknown parameter type '${parts[1]}'")
            }
            require(values.put(key, value) == null) { "Duplicate parameter '$key'" }
        }
        require(values.keys == entry.schema.definitions.map { it.key.id }.toSet()) { "Preset parameters differ from definition; reset or migrate the preset" }
        return entry.restore(values)
    }

    private fun hex(value: String): String {
        require(value.length <= 2048) { "Preset identifier too long" }
        return value.encodeToByteArray().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
    private fun unhex(value: String): String {
        require(value.length in 2..8192 && value.length % 2 == 0 && value.all { it in "0123456789abcdef" }) { "Malformed preset identifier" }
        val bytes = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return try { bytes.decodeToString(throwOnInvalidSequence = true).also { require(it.length <= 2048) { "Preset identifier too long" } } }
        catch (error: Exception) { throw IllegalArgumentException("Malformed UTF-8 preset identifier", error) }
    }
}
