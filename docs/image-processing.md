---
title: Offline image processing and shader resource ownership
description: Process image bytes with ShaderX on Skia platforms, handle failures, reuse compiled shaders, and understand the current Android image-export limitation.
---

# Offline image processing

`ImageProcessor` applies an effect to encoded image bytes and returns a `ShaderResult<ByteArray>`. The processor shares a factory's compilation cache. It is synchronous, so large images or batches can block the calling thread.

## Check platform support first

The Skia implementation supports individual runtime effects and native blur. It rejects `CompositeEffect`. Android's current implementation returns `ProcessingError` because its software bitmap canvas cannot draw the hardware `RenderNode`; this API is not a working Android image-export path. On `main` / 0.1.4 development, it rejects the operation before decoding input, inspecting the effect, compiling shaders, or allocating destination bitmaps. Invalid image bytes therefore receive the same platform-limitation error. Use the [Compose modifier](compose.md) for on-screen Android effects.

## Process an image with explicit cleanup

This example uses public APIs available in 0.1.3 and `main`. Run it on a supported Skia target, and handle the result at the call site:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.result.ShaderResult

fun sepiaImage(imageBytes: ByteArray): ShaderResult<ByteArray> {
    val factory = ShaderFactory.create(maxCacheSize = 8)
    return try {
        val processor = ImageProcessor.create(factory)
        processor.process(imageBytes, SepiaEffect(intensity = 0.8f))
    } finally {
        factory.close()
    }
}
```

Use `onSuccess` to save the returned bytes and `onFailure` to display or log the error. Do not save the original input as though it were a successful processed result.

For a batch, keep one factory and processor for the batch, process images sequentially, and close the factory afterward. For background processing, create a dedicated factory inside the worker and close it there; do not pass a Compose UI factory to a background export. A Skia factory is not thread-safe. Shared access requires external serialization of every factory, processor, and session operation, including cache eviction and disposal.

On `main` / 0.1.4 development, the Skia processor closes each call's decoded image, shader builder, image filter, surface, paint, snapshot, and encoded-data wrappers on both success and failure. Compiled effects remain in the factory's bounded cache for reuse. This reduces delayed native-memory cleanup during batches; encoding and rasterization still run synchronously on the calling thread.

## Dimensions and output

The optional `width` and `height` arguments override dimensions supplied to shader uniforms. They do **not** resize the output bitmap. The current processors encode successful results as PNG. Invalid input bytes, unsupported effects, shader compilation, or encoding can produce failures.

See [platform support](platforms.md) and [troubleshooting](troubleshooting.md) for the known limitations.
