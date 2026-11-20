---
title: Offline image processing and shader resource ownership
description: Process image bytes with ShaderX on Skia platforms, handle failures, reuse compiled shaders, and understand the current Android image-export limitation.
---

# Offline image processing

`ImageProcessor` applies an effect to encoded image bytes and returns a `ShaderResult<ByteArray>`. The processor shares a factory's compilation cache. It is synchronous, so large images or batches can block the calling thread.

Declarative [custom definitions](custom-effects.md) use the same public runtime path.
Skia export evaluates their current immutable values/time and live provider once for the image;
it does not advance a timeline. Provider execution or binding-contract failures return
`ShaderError.Unknown` with effect/binding/stage and cause. Actual source/compiler or native setter
failures retain diagnostics in `CompilationError`, including the effect ID and source.

## Check platform support first

The Skia implementation supports individual runtime effects and native blur. It rejects `CompositeEffect`. Android's current implementation returns `ProcessingError` because its software bitmap canvas cannot draw the hardware `RenderNode`; this API is not a working Android image-export path. The Android implementation rejects the operation before decoding input, inspecting the effect, compiling shaders, or allocating destination bitmaps. Invalid image bytes therefore receive the same platform-limitation error. Use the [Compose modifier](compose.md) for on-screen Android effects.

### Query the export operation { #query-the-export-operation-in-development }

Query `factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect)` before offering legacy `process` export; the additive bounded route uses its processor query below. See [imports and report semantics](platforms.md#operation-queries-in-development). `isSupported()` only describes legacy on-screen availability and is insufficient for this check. A query does not decode bytes, compile a shader, or invoke uniform/data providers. Skia rejects every composite, including a single-child composite, before decoding; Android rejects all exports before decoding or inspecting the effect. A supported runtime effect can still fail compilation or processing.

The built-in Skia processor requires the concrete factory returned by `ShaderFactory.create()`. A third-party factory or wrapper without an optional capability provider reports unknown capabilities, and capability reporting does not remove this processor-creation restriction. Keep the same factory for querying and processing.

Static render fallback policies do not turn a failed image export into a successful file. Show the processing failure; saving original bytes must be a separate, explicit user action.

## Process an image with explicit cleanup

This example uses the low-level processing API. Run it on a supported Skia target, and handle the result at the call site:

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

In the current implementation, the Skia processor closes each call's decoded image, shader builder, image filter, surface, paint, snapshot, and encoded-data wrappers on both success and failure. Compiled effects remain in the factory's bounded cache for reuse. This reduces delayed native-memory cleanup during batches; encoding and rasterization still run synchronously on the calling thread.

## Dimensions and output

The optional `width` and `height` arguments override dimensions supplied to shader uniforms. They do **not** resize the output bitmap. The current processors encode successful results as PNG. Invalid input bytes, unsupported effects, shader compilation, or encoding can produce failures.

See [platform support](platforms.md) and [troubleshooting](troubleshooting.md) for the known limitations.

## Explicit child inputs { #explicit-child-inputs-in-development }

Skia export accepts bounded source-free `ShaderNode` inputs, including the
separate 64-texel `ShaderNode.rgba8Sampler` numerical helper. Rebind caller data/children explicitly;
this does not enable native Skia raw-image data textures. Known declarative raw requirements
reject before decode without invoking providers. Manual runtime implementations that rely on
input-derived default dimensions decode before provider validation; compilation/binding follow
whole-tree validation. Child and grandchild failures retain their own source plus input path in
`CompilationError`, while parent alias errors retain the parent source. See the
[input contract](custom-effects.md#numerical-data-and-nested-inputs-in-development) for precision,
sampling, alpha, revision and lifetime rules. Android image export remains unsupported.

## Bounded PNG export { #bounded-png-export-in-development }

`ImageExportOptions` and the optional `ImageExportProvider` add a
separate bounded route. Existing `ImageProcessor` implementors need no new methods. Query
`processor.queryImageExportSupport(effect)` for this route; `factory.querySupport(IMAGE_EXPORT, effect)`
continues to describe the legacy `process` overload. Built-in Skia implements both routes for single
runtime effects and native blur. Both reject every composite before decoding, including one child.
A third-party processor without the optional provider rejects bounded export instead of silently
calling legacy processing. Android currently rejects both public routes.

```kotlin
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.ImageExportOptions
import com.debanshu.shaderlab.shaderx.factory.exportImage
import com.debanshu.shaderlab.shaderx.result.ShaderResult

suspend fun thumbnail(bytes: ByteArray): ShaderResult<ByteArray> = exportImage(
    bytes, GrayscaleEffect(), ImageExportOptions(pixelWidth = 320, pixelHeight = 180),
)
```

The top-level suspend function creates, uses and closes a private factory in one synchronous
`Dispatchers.Default` block. No UI-owned Skia factory is transferred between threads. Uniform,
image and live providers execute in that worker block; callers must make their captured state safe
there and retain borrowed images until completion. A cache is reused within one request, not across
requests. A global admission gate allows one active request and one waiting request; excess requests
return `ProcessingError` immediately. Waiting requests borrow bytes/effects without copying;
bytes are copied only after admission reaches the worker. Do not mutate them before completion.
Synchronous `processor.exportImage(bytes, effect, options)` shares the caller's existing factory;
its caller must serialize all use and disposal. It is outside the suspend admission gate.

Cancellation while waiting performs no native work. Cancellation during native work suppresses
result delivery after that work returns and owned resources close. There is no preemption or hard
timeout for a decoder, compiler, renderer or encoder. On Wasm, Default does not create a worker:
the browser event loop still performs bounded synchronous work and may block. Move expensive browser
work to an app-owned worker architecture if needed; this API does not create one.

| Contract | Bounded route |
| --- | --- |
| Output dimensions | Required positive integer `pixelWidth`/`pixelHeight`; real encoded pixels |
| Shader coordinates | Positive finite `coordinateWidth`/`coordinateHeight`, defaulting independently to output pixels |
| Fit and sampling | Stretch source to the full output rectangle with linear sampling, then apply the filter at destination coordinates; clip to the output rectangle |
| Input | Single-frame PNG or JPEG; non-normal EXIF orientation rejects explicitly. Normalize rotated/mirrored sources first |
| Output | PNG only, 8-bit sRGB with alpha, rendered on premultiplied RGBA8 raster surfaces; not GPU acceleration |
| Color/metadata | Skia converts tagged source colors to sRGB; untagged input uses its sRGB assumption. EXIF and other source metadata are not copied |
| Formats/quality | No JPEG output, quality setting or implicit alpha flattening. Save an opaque format separately only with an explicit background policy |
| Admission limits | 1 byte–8 MiB encoded input; source and output each at most 4,194,304 pixels and 4096 pixels per edge; source header checked before pixel allocation/provider evaluation |
| Failures | Explicit decode/bounds/orientation/encoding failure; compile and declared binding errors retain existing diagnostics. No success containing unprocessed source bytes |

These are admission limits, not a bound on total process memory: decoded RGBA, two output surfaces,
snapshots, PNG bytes and codec/filter scratch can coexist. An admitted allocation may still fail.
PNG animation-control chunks reject explicitly even when a codec exposes only the default frame.
Malformed images are eagerly decoded through `Codec.readPixels` so deferred decode cannot silently
turn a failure into an empty successful export. Every temporary native wrapper closes before return.
Legacy `process(bytes, effect, width, height)` keeps source-sized PNG output and its prior limits;
its Float arguments still affect uniforms only. Input byte arrays remain unchanged in both routes.

## Android hardware feasibility gate

SX13 includes an **internal**, separately tested `HardwareRenderer` → `ImageReader` →
`HardwareBuffer` → readable bitmap → PNG probe in `androidDeviceTest`, absent from the library AAR. API 31 native blur and API 33+ runtime shaders use
separate support guards; API 33 fence access stays behind a guarded helper. Software-emulator pixel
success does not establish physical GPU behavior or throughput. Cancellation checkpoints unwind
renderer, reader/surface, images, buffers and temporary bitmaps after native calls return.

Public Android export remains unavailable while the repeated-cancellation graphics-resource gate
is unresolved: the API 31 software emulator emitted `bad color buffer handle` messages despite
passing representative pixel/recovery assertions. This is an observed host lifetime signal, not
proof that output pixels failed. A targeted comparison with the official recipe cleanup order
removed those API 31 messages but API 36 isolated and combined runs crashed natively during
pre-render cancellation. The recipe-order diagnostics are opt-in; neither ordering establishes a
production failure-cleanup contract. Checking cancellation before setup and treating setup through first
submission as one non-preemptible section is an unproven follow-up; it would not establish safe
cleanup if native setup itself throws. The probe is not exposed through capability reports or the legacy
processor. Physical-driver lifetime/resource-exhaustion validation, supported source metadata policy
and production hardware admission remain future gates. See the [image export validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx13-image-export.json).


Authored content definitions must declare `uniform shader content;`. A missing/ambiguous declaration
returns `CompilationError` before Skia legacy/bounded export decodes input bytes or invokes live
providers; suspend export also preflights before creating its worker-owned factory. Use explicit
procedural backgrounds for source-free on-screen generation, not the image content-filter route.
The [bounded authored declaration rule](custom-effects.md#authored-content-input-preflight) does
not change manual legacy shader declaration handling or enable Android image export.
