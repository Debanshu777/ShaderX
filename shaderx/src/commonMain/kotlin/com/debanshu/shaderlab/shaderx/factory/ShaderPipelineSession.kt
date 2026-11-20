package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.authoring.definedContentError
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.*

/** Optional pipeline capability. Existing third-party factories need no additional members. */
public interface ShaderPipelineProvider {
    public fun createPipelineSession(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession>
}

/**
 * A whole ordered chain, refreshed at one caller-owned timestamp. No child advances its own clock.
 * Replacement is transactional: failure retains [currentRenderEffect]. Native filters are immutable
 * snapshots; fresh animated native state avoids mutating filters the renderer may still be sampling.
 * Child time/isAnimating flags are overridden by the caller timestamp. All runtime uniforms are
 * resolved before native creation; numeric arrays are copied, image handles remain borrowed.
 * Skia reuses compiled programs. Android non-color snapshots use the source cache, while color
 * snapshots (including static children) construct fresh shaders to preserve structural color safety.
 * This prioritizes correctness over allocation cost and makes no performance guarantee.
 * Close the session when its host is disposed; returned Compose wrappers retain their native filters.
 */
public interface ShaderPipelineSession : AutoCloseable {
    public val currentRenderEffect: RenderEffect
    public fun refresh(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<RenderEffect>
    override fun close()
}

/** Creates an optional pipeline session. Unsupported factories fail without attempting child rendering. */
public fun ShaderFactory.createPipelineSession(
    effect: CompositeEffect,
    width: Float,
    height: Float,
    timeSeconds: Float = 0f,
): ShaderResult<ShaderPipelineSession> = (this as? ShaderPipelineProvider)?.createPipelineSession(effect, width, height, timeSeconds)
    ?: ShaderResult.failure(ShaderError.UnsupportedEffect("This factory does not provide pipeline sessions", effect.id))

internal class SnapshotPipelineSession private constructor(
    private val factory: ShaderFactory,
    initial: RenderEffect,
) : ShaderPipelineSession {
    private var current: RenderEffect? = initial
    override val currentRenderEffect: RenderEffect get() = checkNotNull(current) { "Pipeline session is closed" }

    override fun refresh(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<RenderEffect> {
        if (current == null) return ShaderResult.failure(ShaderError.Unknown("Pipeline session is closed"))
        return snapshot(factory, effect, width, height, timeSeconds).map { current = it; it }
    }

    override fun close() { current = null }

    internal companion object {
        fun create(factory: ShaderFactory, effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession> =
            snapshot(factory, effect, width, height, timeSeconds).map { SnapshotPipelineSession(factory, it) }

        private fun copyUniform(uniform: Uniform): Uniform = when (uniform) {
            is FloatUniform -> uniform.copy(values = uniform.values.copyOf())
            is IntUniform -> uniform.copy(values = uniform.values.copyOf())
            is MatrixUniform -> uniform.copy(values = uniform.values.copyOf())
            is com.debanshu.shaderlab.shaderx.uniform.DataUniform, is com.debanshu.shaderlab.shaderx.uniform.NestedUniform, is ColorUniform, is TextureUniform -> uniform // immutable values / borrowed image handle
        }

        private fun snapshot(factory: ShaderFactory, effect: CompositeEffect, width: Float, height: Float, time: Float): ShaderResult<RenderEffect> {
            definedContentError(effect)?.let { return ShaderResult.failure(it) }
            val support = factory.querySupport(ShaderOperation.ANIMATED_SESSION, effect)
            if (support.status == ShaderSupportStatus.UNSUPPORTED) return ShaderResult.failure(support.asError(ShaderOperation.ANIMATED_SESSION, effect))
            return try {
                require(time.isFinite() && width.isFinite() && height.isFinite() && width > 0 && height > 0) {
                    "Pipeline time must be finite and dimensions positive and finite"
                }
                // Resolve every provider before creating any native child. Defined adapters already
                // merge their frame snapshot into buildUniforms; manual effects need the legacy merge.
                fun freeze(child: ShaderEffect): ShaderEffect = when (child) {
                    is CompositeEffect -> CompositeEffect(child.effects.map(::freeze))
                    is AnimatedShaderEffect -> {
                        val timed = child.withTime(time)
                        val source = timed.shaderSource
                        val structural = timed.buildUniforms(width, height).map(::copyUniform)
                        val frame = if (timed is DefinedAnimatedShaderEffect) emptyList() else timed.buildFrameUniforms().map(::copyUniform)
                        val uniforms = structural.filter { base -> frame.none { it.name == base.name } } + frame
                        PipelineSnapshotEffect(timed, source, uniforms)
                    }
                    is RuntimeShaderEffect -> {
                        val source = child.shaderSource
                        PipelineSnapshotEffect(child, source, child.buildUniforms(width, height).map(::copyUniform))
                    }
                    else -> child
                }
                factory.createRenderEffect(CompositeEffect(effect.effects.map(::freeze)), width, height)
            } catch (e: Exception) {
                ShaderResult.failure(ShaderError.Unknown("Pipeline snapshot failed: ${e.message}", e))
            }
        }
    }
}

/** Marks frozen runtime snapshots so Android gives structural colors fresh native state. */
internal class PipelineSnapshotEffect(
    original: RuntimeShaderEffect,
    override val shaderSource: String,
    private val uniforms: List<Uniform>,
) : RuntimeShaderEffect by original {
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = uniforms
}
