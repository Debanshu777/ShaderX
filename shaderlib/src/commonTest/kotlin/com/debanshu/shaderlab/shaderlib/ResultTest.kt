package com.debanshu.shaderlab.shaderlib

import com.debanshu.shaderlab.shaderlib.result.ShaderError
import com.debanshu.shaderlab.shaderlib.result.ShaderException
import com.debanshu.shaderlab.shaderlib.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultTest {

    @Test
    fun success_getOrNull_returnsValue() {
        val result = ShaderResult.success("value")
        assertEquals("value", result.getOrNull())
    }

    @Test
    fun failure_getOrNull_returnsNull() {
        val result = ShaderResult.failure<String>(
            ShaderError.CompilationError("test error")
        )
        assertNull(result.getOrNull())
    }

    @Test
    fun success_getOrElse_returnsValue() {
        val result = ShaderResult.success("value")
        val value = result.getOrElse { "default" }
        assertEquals("value", value)
    }

    @Test
    fun failure_getOrElse_returnsDefault() {
        val result = ShaderResult.failure<String>(
            ShaderError.CompilationError("test error")
        )
        val value = result.getOrElse { "default" }
        assertEquals("default", value)
    }

    @Test
    fun success_getOrThrow_returnsValue() {
        val result = ShaderResult.success("value")
        assertEquals("value", result.getOrThrow())
    }

    @Test
    fun failure_getOrThrow_throws() {
        val result = ShaderResult.failure<String>(
            ShaderError.CompilationError("test error")
        )

        val exception = assertFailsWith<ShaderException> {
            result.getOrThrow()
        }

        assertTrue(exception.error is ShaderError.CompilationError)
        assertEquals("test error", exception.error.message)
    }

    @Test
    fun success_map_transformsValue() {
        val result = ShaderResult.success(5)
        val mapped = result.map { it * 2 }

        assertEquals(10, mapped.getOrNull())
    }

    @Test
    fun failure_map_preservesError() {
        val error = ShaderError.CompilationError("error")
        val result = ShaderResult.failure<Int>(error)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is ShaderResult.Failure)
        assertEquals(error, (mapped as ShaderResult.Failure).error)
    }

    @Test
    fun success_onSuccess_executesBlock() {
        var executed = false
        val result = ShaderResult.success("value")

        result.onSuccess { executed = true }

        assertTrue(executed)
    }

    @Test
    fun failure_onSuccess_doesNotExecuteBlock() {
        var executed = false
        val result = ShaderResult.failure<String>(
            ShaderError.CompilationError("error")
        )

        result.onSuccess { executed = true }

        assertTrue(!executed)
    }

    @Test
    fun failure_onFailure_executesBlock() {
        var executed = false
        val result = ShaderResult.failure<String>(
            ShaderError.CompilationError("error")
        )

        result.onFailure { executed = true }

        assertTrue(executed)
    }

    @Test
    fun success_onFailure_doesNotExecuteBlock() {
        var executed = false
        val result = ShaderResult.success("value")

        result.onFailure { executed = true }

        assertTrue(!executed)
    }

    @Test
    fun runCatching_success_returnsSuccess() {
        val result = ShaderResult.runCatching { "computed" }
        assertEquals("computed", result.getOrNull())
    }

    @Test
    fun runCatching_exception_returnsFailure() {
        val result = ShaderResult.runCatching<String> {
            throw RuntimeException("test exception")
        }

        assertTrue(result is ShaderResult.Failure)
        assertTrue((result as ShaderResult.Failure).error is ShaderError.Unknown)
    }

    @Test
    fun shaderError_compilationError_containsSource() {
        val error = ShaderError.CompilationError(
            message = "syntax error",
            shaderSource = "invalid shader code"
        )

        assertEquals("syntax error", error.message)
        assertEquals("invalid shader code", error.shaderSource)
    }

    @Test
    fun shaderError_unsupportedEffect_containsId() {
        val error = ShaderError.UnsupportedEffect(
            message = "not supported",
            effectId = "my_effect"
        )

        assertEquals("my_effect", error.effectId)
    }

    @Test
    fun chaining_onSuccessAndOnFailure_works() {
        var successValue: String? = null
        var errorMessage: String? = null

        ShaderResult.success("value")
            .onSuccess { successValue = it }
            .onFailure { errorMessage = it.message }

        assertEquals("value", successValue)
        assertNull(errorMessage)
    }
}

