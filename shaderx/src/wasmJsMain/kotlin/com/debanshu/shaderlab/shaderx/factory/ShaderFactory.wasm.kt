package com.debanshu.shaderlab.shaderx.factory

public actual fun ShaderFactory.Companion.create(): ShaderFactory = SkiaShaderFactory()

public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessor()
