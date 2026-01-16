package com.debanshu.shaderlab.shaderx.factory

/**
 * iOS implementation delegates to the shared Skia factory.
 */
public actual fun ShaderFactory.Companion.create(): ShaderFactory = SkiaShaderFactoryImpl()

/**
 * iOS implementation delegates to the shared Skia image processor.
 */
public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessorImpl()
