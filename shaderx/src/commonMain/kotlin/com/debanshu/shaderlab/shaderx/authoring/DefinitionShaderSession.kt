package com.debanshu.shaderlab.shaderx.authoring

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/** Owns one native session; structural edits replace it transactionally, never mutate colors. */
internal class DefinitionShaderSession(
    initialEffect: DefinedAnimatedShaderEffect,
    width: Float,
    height: Float,
    initialSession: ShaderSession,
    private val create: (DefinedAnimatedShaderEffect, Float, Float) -> ShaderResult<ShaderSession>,
) : ShaderSession {
    private var configuration: DefinedAnimatedShaderEffect? = initialEffect
    private var targetWidth = width
    private var targetHeight = height
    private var delegate: ShaderSession? = initialSession
    private var closed = false
    private var current: RenderEffect? = initialSession.currentRenderEffect
    override val currentRenderEffect: RenderEffect get() = checkNotNull(current) { "Definition session is closed" }

    override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
        if (closed) return ShaderResult.failure(ShaderError.Unknown("Effect '${effect.id}' session is closed"))
        if (effect !is DefinedAnimatedShaderEffect) return ShaderResult.failure(
            ShaderError.UnsupportedEffect("Definition session requires a defined animated effect", effect.id),
        )
        if (configuration != effect || targetWidth != width || targetHeight != height) {
            return create(effect, width, height).map { replacement ->
                val previous = delegate
                delegate = replacement
                configuration = effect
                targetWidth = width
                targetHeight = height
                val output = replacement.currentRenderEffect
                current = output
                previous?.close()
                output
            }
        }
        return checkNotNull(delegate).refresh(effect, width, height).map { current = it; it }
    }

    override fun close() {
        if (!closed) {
            closed = true
            val previous = delegate
            delegate = null
            current = null
            configuration = null
            previous?.close()
        }
    }
}
