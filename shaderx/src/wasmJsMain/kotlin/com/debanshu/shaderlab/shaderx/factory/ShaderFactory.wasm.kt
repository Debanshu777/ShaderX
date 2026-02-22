package com.debanshu.shaderlab.shaderx.factory

public actual fun ShaderFactory.Companion.create(maxCacheSize: Int): ShaderFactory =
    SkiaShaderFactory(maxCacheSize)

public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessor()
