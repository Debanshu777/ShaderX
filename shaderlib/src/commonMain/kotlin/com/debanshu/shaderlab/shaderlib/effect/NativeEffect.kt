package com.debanshu.shaderlab.shaderlib.effect

/**
 * Marker interface for effects that use platform-native implementations.
 *
 * Native effects leverage platform-specific optimizations (e.g., hardware-accelerated
 * blur on Android) rather than custom shader code. This typically results in
 * better performance for common operations.
 *
 * Implementations should NOT provide shader code; the platform factory
 * will handle the native implementation directly.
 *
 * @see BlurEffect for the built-in native blur implementation
 */
public interface NativeEffect : ShaderEffect {
    override fun withParameter(parameterId: String, value: Float): NativeEffect
}

/**
 * Native blur effect using platform-optimized blur implementations.
 *
 * On Android, this uses `RenderEffect.createBlurEffect()`.
 * On iOS/Desktop, this uses Skia's `ImageFilter.makeBlur()`.
 *
 * Both implementations are hardware-accelerated and more efficient
 * than a custom blur shader.
 */
public interface BlurEffect : NativeEffect {
    /**
     * Blur radius in pixels.
     * Higher values create a more pronounced blur effect.
     * Must be >= 0. Values are clamped to a minimum of 0.1 for rendering.
     */
    public val radius: Float

    override fun withParameter(parameterId: String, value: Float): BlurEffect
}
