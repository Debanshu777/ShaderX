package com.debanshu.shaderlab.shaderx.factory

import androidx.compose.ui.graphics.ShaderBrush
import com.debanshu.shaderlab.shaderx.authoring.validateInputTree
import com.debanshu.shaderlab.shaderx.authoring.validateInputAliases
import com.debanshu.shaderlab.shaderx.authoring.ShaderInputCompilationException
import com.debanshu.shaderlab.shaderx.result.*
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.impl.use

internal actual fun createNativeBackgroundSnapshot(factory: ShaderFactory, source: String, uniforms: List<Uniform>): ShaderResult<BackgroundSnapshot> {
    if (factory !is SkiaShaderFactory) return ShaderResult.failure(ShaderError.UnsupportedEffect("Factory has no background renderer", "background"))
    return try {
        validateInputTree(uniforms, rawSupported = false)
        validateInputAliases(source, uniforms)
        val shader = RuntimeShaderBuilder(factory.getOrCreateEffect(source)).use { builder ->
            SkiaShaderFactory.applyUniforms(builder, uniforms, factory)
            builder.makeShader()
        }
        ShaderResult.success(object : BackgroundSnapshot {
            override val brush = ShaderBrush(shader)
            private var closed = false
            override fun close() { if (!closed) { closed = true; shader.close() } }
        })
    } catch (e: ShaderInputCompilationException) {
        ShaderResult.failure(e.asError())
    } catch (e: Exception) {
        ShaderResult.failure(ShaderError.CompilationError("Background compile/bind failed: ${e.message}", source))
    }
}

internal actual fun backgroundRouteError(factory: ShaderFactory): ShaderError? = when {
    factory !is SkiaShaderFactory -> ShaderError.UnsupportedEffect("Factory has no background renderer", "background")
    else -> null
}
