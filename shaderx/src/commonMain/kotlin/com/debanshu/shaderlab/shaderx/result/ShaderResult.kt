package com.debanshu.shaderlab.shaderx.result

/**
 * Represents the result of a shader operation that may fail.
 *
 * Use this instead of nullable returns to provide meaningful error information
 * when shader compilation or processing fails.
 *
 * ## Usage
 * ```kotlin
 * when (val result = factory.createRenderEffect(effect, width, height)) {
 *     is ShaderResult.Success -> applyEffect(result.value)
 *     is ShaderResult.Failure -> showError(result.error.message)
 * }
 * ```
 */
public sealed class ShaderResult<out T> {
    /**
     * Represents a successful shader operation.
     *
     * @property value The result of the operation
     */
    public data class Success<T>(public val value: T) : ShaderResult<T>()

    /**
     * Represents a failed shader operation.
     *
     * @property error Detailed error information
     */
    public data class Failure<T>(public val error: ShaderError) : ShaderResult<T>()

    /**
     * Returns the value if successful, null otherwise.
     */
    public fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /**
     * Returns the value if successful, or the result of [defaultValue] otherwise.
     */
    public inline fun <R : @UnsafeVariance T> getOrElse(defaultValue: (ShaderError) -> R): T = when (this) {
        is Success -> value
        is Failure -> defaultValue(error)
    }

    /**
     * Returns the value if successful, or throws the error as an exception.
     */
    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw ShaderException(error)
    }

    /**
     * Maps the success value to a new type.
     */
    public inline fun <R> map(transform: (T) -> R): ShaderResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> Failure(error)
    }

    /**
     * Executes the given block if this is a success.
     */
    public inline fun onSuccess(action: (T) -> Unit): ShaderResult<T> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Executes the given block if this is a failure.
     */
    public inline fun onFailure(action: (ShaderError) -> Unit): ShaderResult<T> {
        if (this is Failure) action(error)
        return this
    }

    public companion object {
        /**
         * Creates a successful result.
         */
        public fun <T> success(value: T): ShaderResult<T> = Success(value)

        /**
         * Creates a failed result.
         */
        public fun <T> failure(error: ShaderError): ShaderResult<T> = Failure(error)

        /**
         * Wraps a block that may throw into a ShaderResult.
         */
        public inline fun <T> runCatching(block: () -> T): ShaderResult<T> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(ShaderError.Unknown(e.message ?: "Unknown error", e))
        }
    }
}

/**
 * Detailed error information for shader operations.
 */
public sealed class ShaderError {
    /**
     * Human-readable error message.
     */
    public abstract val message: String

    /**
     * Shader compilation failed.
     *
     * @property message Error message from the shader compiler
     * @property shaderSource The shader code that failed to compile
     */
    public data class CompilationError(
        override val message: String,
        public val shaderSource: String? = null,
    ) : ShaderError()

    /**
     * The effect type is not supported on this platform.
     *
     * @property message Description of the unsupported feature
     * @property effectId The ID of the unsupported effect
     */
    public data class UnsupportedEffect(
        override val message: String,
        public val effectId: String,
    ) : ShaderError()

    /**
     * Image processing failed.
     *
     * @property message Description of the failure
     * @property cause The underlying exception, if any
     */
    public data class ProcessingError(
        override val message: String,
        public val cause: Throwable? = null,
    ) : ShaderError()

    /**
     * Platform does not support shaders.
     *
     * @property message Description of the limitation
     */
    public data class PlatformNotSupported(
        override val message: String,
    ) : ShaderError()

    /**
     * Unknown error occurred.
     *
     * @property message Error description
     * @property cause The underlying exception
     */
    public data class Unknown(
        override val message: String,
        public val cause: Throwable? = null,
    ) : ShaderError()
}

/**
 * Exception wrapper for [ShaderError].
 *
 * Used when [ShaderResult.getOrThrow] is called on a failure.
 */
public class ShaderException(public val error: ShaderError) : Exception(error.message)
