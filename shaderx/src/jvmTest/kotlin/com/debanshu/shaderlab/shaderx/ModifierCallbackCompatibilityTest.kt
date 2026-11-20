package com.debanshu.shaderlab.shaderx

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.compose.shaderEffectWithResult
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ModifierCallbackCompatibilityTest {
    @Test
    fun oldPositionalAndNewNamedCallbacksReceiveActualFailures() {
        val failure = ShaderError.UnsupportedEffect("compatibility probe", "grayscale")
        val factory = object : ShaderFactory {
            override val cacheSize = 0
            override fun isSupported() = true
            override fun clearCache() {}
            override fun close() {}
            override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
                ShaderResult.failure(failure)
        }
        val seen = mutableSetOf<String>()
        fun error(label: String, value: ShaderError) { assertEquals(failure, value); seen += label }
        val typedError: (ShaderError) -> Unit = { error("typed positional", it) }
        val typedResult: (ShaderResult<RenderEffect>) -> Unit = { it.onFailure { e -> error("result positional", e) } }
        val effect = GrayscaleEffect()
        val size = IntSize(32, 32)
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.shaderEffect(effect, factory, size, typedError))
            Box(Modifier.shaderEffectWithResult(effect, factory, size, typedResult))
            Box(Modifier.shaderEffect(effect, factory, size, { error("lambda positional", it) }))
            Box(Modifier.shaderEffect(effect, factory, size) { error("trailing", it) })
            Box(Modifier.shaderEffect(effect, factory, size, onError = { error("named", it) }))
            Box(Modifier.shaderEffect(effect, factory, size, frameDataKey = 1, onError = { error("live named", it) }))
            Box(Modifier.shaderEffectWithResult(effect, factory, size, frameDataKey = 1) {
                it.onFailure { e -> error("live result", e) }
            })
            Box(Modifier.shaderEffect(null)) // Ordinary source quickstart overload resolution.
        }
        try {
            repeat(8) { frame -> scene.render(frame * 16_000_000L).close() }
            assertEquals(setOf("typed positional", "result positional", "lambda positional", "trailing", "named", "live named", "live result"), seen)
        } finally { scene.close() }
    }
}
