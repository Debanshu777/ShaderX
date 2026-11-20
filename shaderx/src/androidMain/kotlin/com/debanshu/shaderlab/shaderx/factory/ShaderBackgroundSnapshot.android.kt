package com.debanshu.shaderlab.shaderx.factory

import android.graphics.RuntimeShader
import com.debanshu.shaderlab.shaderx.authoring.ShaderInputCompilationException
import com.debanshu.shaderlab.shaderx.authoring.validateInputTree
import com.debanshu.shaderlab.shaderx.authoring.validateInputAliases
import androidx.compose.ui.graphics.ShaderBrush
import com.debanshu.shaderlab.shaderx.result.*
import com.debanshu.shaderlab.shaderx.uniform.Uniform

internal actual fun createNativeBackgroundSnapshot(factory: ShaderFactory, source: String, uniforms: List<Uniform>): ShaderResult<BackgroundSnapshot> {
    if (android.os.Build.VERSION.SDK_INT < 33) return ShaderResult.failure(ShaderError.PlatformNotSupported("Backgrounds require Android API 33"))
    return Api33Background.create(factory, source, uniforms)
}

@androidx.annotation.RequiresApi(33)
private object Api33Background {
    @androidx.annotation.DoNotInline
    fun routeError(factory: ShaderFactory): ShaderError? = when {
        factory !is AndroidShaderFactory -> ShaderError.UnsupportedEffect("Factory has no background renderer", "background")
        !factory.isSupported() -> ShaderError.PlatformNotSupported("Backgrounds require Android API 33")
        else -> null
    }

    @androidx.annotation.DoNotInline
    fun create(factory: ShaderFactory, source: String, uniforms: List<Uniform>): ShaderResult<BackgroundSnapshot> {
        if (factory !is AndroidShaderFactory) return ShaderResult.failure(ShaderError.UnsupportedEffect("Factory has no background renderer", "background"))
        if (!factory.isSupported()) return ShaderResult.failure(ShaderError.PlatformNotSupported("Backgrounds require Android API 33"))
        return try {
            validateInputTree(uniforms)
            validateInputAliases(source, uniforms)
            // RuntimeShader is mutable: every snapshot owns an independent instance. Bind colors once.
            val shader = RuntimeShader(source)
            AndroidShaderFactory.applyUniforms(shader, uniforms)
            ShaderResult.success(object : BackgroundSnapshot {
                override val brush = ShaderBrush(shader)
                override fun close() { /* Android Shader has no explicit native close API. */ }
            })
        } catch (e: ShaderInputCompilationException) {
            ShaderResult.failure(e.asError())
        } catch (e: Exception) {
            ShaderResult.failure(ShaderError.CompilationError("Background compile/bind failed: ${e.message}", source))
        }
    }

}

internal actual fun backgroundRouteError(factory: ShaderFactory): ShaderError? =
    if (android.os.Build.VERSION.SDK_INT < 33) ShaderError.PlatformNotSupported("Backgrounds require Android API 33")
    else Api33Background.routeError(factory)
