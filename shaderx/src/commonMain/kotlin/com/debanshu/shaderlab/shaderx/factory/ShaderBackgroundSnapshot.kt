package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.Brush
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterValues
import com.debanshu.shaderlab.shaderx.result.*
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/** Internal draw ownership: never expose a native shader as a reusable public brush. */
internal interface BackgroundSnapshot : AutoCloseable { val brush: Brush }

internal fun createBackgroundSnapshot(
    factory: ShaderFactory,
    background: ShaderBackground,
    values: ParameterValues,
    width: Float,
    height: Float,
    timeSeconds: Float,
    frameProvider: ShaderFrameProvider?,
): ShaderResult<BackgroundSnapshot> {
    backgroundRouteError(factory)?.let { return ShaderResult.failure(it) }
    val support = factory.queryBackgroundSupport(background)
    if (support.status == ShaderSupportStatus.UNSUPPORTED) return ShaderResult.failure(support.asError(ShaderOperation.BACKGROUND_RENDER, null))
    return try {
        require(timeSeconds.isFinite()) { "Background time must be finite" }
        val definition = background.definition
        require(values.schema === definition.schema) { "Background values must belong to the declaration schema" }
        require(definition.program.bindings.any { it.frame } == (frameProvider != null)) {
            "Background requires a frame provider exactly when frame bindings are declared"
        }
        val uniforms = definition.uniforms(values, width, height, timeSeconds) +
            definition.frameUniforms(timeSeconds, frameProvider).filter { uniform ->
                definition.program.bindings.none { it.clock && it.name == uniform.name }
            }
        validateInputTree(uniforms)
        createNativeBackgroundSnapshot(factory, definition.program.source, uniforms)
    } catch (e: Exception) {
        ShaderResult.failure(ShaderError.Unknown("Background '${background.definition.id}' binding failed: ${e.message}", e))
    }
}

internal expect fun backgroundRouteError(factory: ShaderFactory): ShaderError?

internal expect fun createNativeBackgroundSnapshot(
    factory: ShaderFactory, source: String, uniforms: List<Uniform>,
): ShaderResult<BackgroundSnapshot>
