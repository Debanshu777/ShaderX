package com.debanshu.shaderlab.shaderx.authoring

/**
 * Explicit source-free output declaration. Reuses immutable [definition],
 * never a native shader. Explicit image bindings remain required and borrowed. Content filtering
 * and captured backdrop sampling are separate operations; neither is supplied here.
 */
public class ShaderBackground internal constructor(public val definition: ShaderDefinition) {
    override fun equals(other: Any?): Boolean = other is ShaderBackground && definition === other.definition
    override fun hashCode(): Int = definition.hashCode()
}

/**
 * Declares that this program generates pixels without Compose content. Rejects the reserved
 * `content` identifier, including content declarations, rather than supplying a silent dummy input.
 * This conservative identifier check is not reflection or native compiler validation.
 */
public fun ShaderDefinition.proceduralBackground(): ShaderBackground {
    val code = program.source.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .replace(Regex("//[^\\n]*"), " ")
    require(!Regex("\\bcontent\\b").containsMatchIn(code)) {
        "Background '$id' must generate pixels without the reserved content input"
    }
    return ShaderBackground(this)
}
