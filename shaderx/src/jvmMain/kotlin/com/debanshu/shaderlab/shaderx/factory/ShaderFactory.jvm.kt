package com.debanshu.shaderlab.shaderx.factory

/**
 * JVM/Desktop implementation delegates to the shared Skia factory.
 */
public actual fun ShaderFactory.Companion.create(maxCacheSize: Int): ShaderFactory =
    SkiaShaderFactory(maxCacheSize)

/**
 * JVM/Desktop implementation delegates to the shared Skia image processor.
 */
public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessor()
