package com.debanshu.shaderlab.shaderx.factory

public actual fun ShaderFactory.Companion.create(): ShaderFactory = SkiaShaderFactoryImpl()

public actual fun ImageProcessor.Companion.create(): ImageProcessor = SkiaImageProcessorImpl()
