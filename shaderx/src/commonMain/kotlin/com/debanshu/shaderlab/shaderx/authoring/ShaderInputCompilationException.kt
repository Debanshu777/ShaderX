package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.result.ShaderError

/** Carries deepest child source separately from parent alias/setter diagnostics. */
internal class ShaderInputCompilationException(
    val childSource: String,
    val inputPath: String,
    cause: Exception,
) : IllegalArgumentException("Child input $inputPath compile/bind failed: ${cause.message}", cause) {
    fun beneath(name: String): ShaderInputCompilationException =
        ShaderInputCompilationException(childSource, "$name/$inputPath", this)
    fun asError(): ShaderError.CompilationError = ShaderError.CompilationError(message ?: "Child input failed", childSource)
}
