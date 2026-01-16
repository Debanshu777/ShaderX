package com.debanshu.shaderlab.shaderlib.factory

/**
 * JVM/Desktop implementation delegates to the shared Skia factory.
 */
public actual fun ShaderFactory.Companion.create(): ShaderFactory = SkiaShaderFactoryImpl()

/**
 * JVM/Desktop implementation delegates to the shared Skia image processor.
 */
public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessorImpl()
