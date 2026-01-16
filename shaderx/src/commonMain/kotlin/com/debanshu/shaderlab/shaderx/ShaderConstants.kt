package com.debanshu.shaderlab.shaderx

/**
 * Shared constants used across shader factory implementations.
 */
internal object ShaderConstants {
    /**
     * The name of the uniform that represents the input content/image.
     * This is the shader uniform that receives the source pixels to be processed.
     */
    const val CONTENT_UNIFORM_NAME = "content"

    /**
     * Minimum blur radius in pixels.
     * Values below this are clamped to prevent rendering issues.
     */
    const val MIN_BLUR_RADIUS = 0.1f
}

