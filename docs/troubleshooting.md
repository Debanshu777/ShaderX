---
title: Troubleshoot ShaderX setup and rendering
description: Resolve ShaderX dependency, import, Android API, shader compilation, animation, Skia composition, and image-export problems with targeted checks.
---

# Troubleshooting

Development declarative authoring rejects duplicate/reserved aliases, foreign schema keys and
missing declared bindings before rendering. Frame providers must return exactly the declared
scalar/image names; colors stay structural. Provider failures report `Unknown` with effect,
binding, stage and cause. Source/native setter diagnostics remain `CompilationError`; resolved
Skiko can silently ignore unmatched scalar setter names, so successful creation does not prove
all source bindings match. The sample workbench adds a narrow declaration/form check before
native compilation; this does not change manual SDK setters. See [binding validation](custom-effects.md#binding-validation-and-failure-stages).

## Gradle cannot find ShaderX { #gradle-cannot-find-shaderx-014 }

Confirm `mavenCentral()` is in dependency repositories, not just plugin repositories, and use the coordinate in [installation](getting-started.md). For next-release APIs, [build a uniquely versioned local artifact](contributing.md#try-the-library-in-another-project). A local build with the same version as an older published artifact can resolve the wrong API surface.

## Unresolved import or missing effect

The package prefix is `com.debanshu.shaderlab.shaderx`, even though the Maven group is `io.github.debanshu777`. Import the `shaderEffect` extension from `.compose` and effects from `.effect.impl`.

Check [release status](index.md#releases-and-development-docs) and [migration](migration.md) if an API in the guides is absent from your installed artifact. For custom effects in external modules, use [declarative `ShaderDefinition` authoring](custom-effects.md#minimal-source-and-image-bindings-in-development) or implement `RuntimeShaderEffect` manually. The abstract base classes require internal handlers.

## Unsupported Android SDK or JVM target

ShaderX requires Android API 31+ for native blur and API 33+ for AGSL effects. Android consumers need compileSdk 35+ and Java 17 bytecode support; use JDK 21 for this repository's Gradle builds. The resolved AndroidX AARs declare AGP 8.6.0, whose independent minimum build is not yet verified. Query the requested effect and choose an explicit fallback. Do not use `overrideLibrary` to bypass manifest requirements. If the build selects another JDK, check your IDE's Gradle JDK, `JAVA_HOME`, and `gradle/gradle-daemon-jvm.properties`.

The contributor checkout also requires the Android compile SDK specified in `gradle/libs.versions.toml`, even for some desktop tasks, because Android modules are configured in the same build.

## No visible effect

- Confirm the composable draws content and has a nonzero size.
- Attach an `onError` callback; inspect shader compilation errors.
- Confirm the effect's intensity is not zero and the effect itself is not `null`.
- For custom source, match uniform names/types and use pixel coordinates.
- If you supply `knownSize`, keep it aligned with the actual size in pixels.

## A support query succeeds but rendering fails

In ShaderX, `querySupport` checks an operation's known effect structure and explicitly supplied input requirements. It does not compile source, validate uniforms, allocate graphics resources, or call live providers. `SUPPORTED` therefore still requires handling the operation's `ShaderResult`. Choose `CONTENT_RENDER`, `ANIMATED_SESSION`, or `IMAGE_EXPORT` for the path you will actually use; a static content report does not promise automatic composite animation or export.

`UNKNOWN` with `CAPABILITIES_NOT_PROVIDED` means a third-party factory has not opted into `ShaderCapabilityProvider`; it is neither a support promise nor a rejection. Attempt the operation and handle its result. The legacy `isSupported()` Boolean cannot establish per-operation support. See [query semantics and stable reasons](platforms.md#operation-queries-in-development).

For a static preview, the default [fallback helper](compose.md#choose-an-explicit-static-fallback) can return successful `null` to preserve content after failure. Inspect `fallbackUsed` and `cause`; a visible original image is not proof that the requested effect rendered. A failed explicit substitute retains both its result error and the requested effect's cause.

## Composite effects fail on iOS, desktop, or web

ShaderX supports on-screen ordered chains; handle a required child's compilation/binding failure as a whole-chain failure. Offline export remains unsupported for composites. See the [platform matrix](platforms.md).

## Animation or audio does not update

For ordinary animation, pass a standalone `AnimatedShaderEffect` directly to `shaderEffect`. Use `animate = true` to advance time. For controlled composite animation use `controlledShaderEffect(chain, playback)` with an explicit shared playback owner; `shaderEffect(chain)` stays a static snapshot.

For audio, supply a live bitmap source using `withFftDataSource`. Verify microphone permission and that the bitmap contains data with the expected number of bins. Browser microphone capture requires HTTPS or localhost. Pausing time does not pause live uniform refresh in the ordinary modifier.

If you replace an effect's bitmap instead of using a live source, change `frameDataKey` with the bitmap. Live images are deliberately excluded from structural equality; the key lets composition deliver the new instance to the existing session. Manually created sessions must be closed when finished and must not be refreshed afterward.

## Image export fails on Android

This is a known implementation limitation: Android's offline processor cannot render its hardware `RenderNode` into a software bitmap. It returns `ProcessingError`; on Android, this happens before image decoding or shader creation, including for malformed input bytes. On-screen Compose rendering is a separate path. See [image processing](image-processing.md) for supported uses.

## Workbench draft errors and recovery

The development workbench preserves failed source/form edits. **STALE** means the preview and
SDK output belong to the last successfully rendered revision; they do not validate the current
draft. Fix the named declaration/form error and **Run shader**. Match all uniform names and types;
for example, a renamed `intensity` uniform also needs a matching **Uniform binding** form value.
Color uniforms require `layout(color)` and `half4`/`float4`; boolean controls require `int`.
Arrays, structs, preprocessor declarations and other unsupported declarations must use the manual
SDK authoring path. Native compiler messages stay available as raw logs; unknown formats are
not given guessed line/column offsets.

The workbench uses ShaderX image processing for its validated snapshot. In the browser this is
Wasm/Skia, which does not prove Android AGSL compilation. Android offline processing is currently
unsupported, so an Android workbench Run cannot promote a successful snapshot. Use the browser
workbench and separately test the exported shader on the actual destination backend.

Source is bounded to 32 KiB UTF-8, schemas to 32 parameters and draft project text to 128 KiB.
Browser image input accepts PNGs up to 1 MiB and 256×256; resize externally before choosing
larger input. Native workbench hosts use the bundled image. Imported projects contain source,
form and scalar values; images and last-good previews/code do not survive reload. A malformed
file does not replace the current draft. A structurally valid broken source file remains editable
and requires Run. Restore never executes Kotlin, uploads data or restores a validation claim.

Use **Save draft** before leaving and **Restore draft** after reload. **Reset workbench** clears
the successful preview, pauses playback and replaces the local recovery slot with Warm tint.
For a stuck shader, reload the page, reset, and restore a known draft only when ready to inspect
it. Synchronous Wasm compilation/GPU execution cannot be reliably interrupted by a timeout;
source/cache/viewport limits are not an isolation guarantee. Copy failure leaves complete code
selectable; use normal keyboard or mobile text selection. Broken draft download is separate
from the last-good SDK export. See the [guided authoring workflow](custom-effects.md#guided-source-authoring-in-development).

## Report a reproducible problem

Include the library version or commit, Kotlin/Compose versions, platform and OS/device, a minimal code sample, and the full error message. Use the [bug report form](https://github.com/Debanshu777/ShaderX/issues/new?template=bug_report.yml). Remove tokens and personal data before attaching logs or recordings.

## Numerical data versus image inputs

For development numerical inputs use `Rgba8Data` and `DataUniform`, not a picture bitmap. A
color-managed image may transform RGB or premultiply alpha. Preserve nonzero RGB at alpha zero
by using the numerical path and explicitly converting sampled data into valid output colors.
Use pixel centers and explicit sampling; check [platform limits](platforms.md) before allocation.
Missing nested inputs and depth/node-limit failures indicate an invalid snapshot; every child
must be source-free and explicitly bound. Parent playback does not supply child time.

## Bounded PNG export fails or is busy

In development, query `processor.queryImageExportSupport(effect)` for `exportImage`, rather than
assuming a legacy or on-screen capability grants the optional route. `Image exporter busy` means
one suspend export is active and one is queued; retry after one finishes. Do not launch an unbounded
retry loop. Normalize EXIF orientation before export, supply PNG/JPEG input within the byte/pixel
limits, and use separate `pixelWidth`/`pixelHeight` for output size. Legacy Float size arguments do
not resize. An Android capability failure is expected even when the internal hardware probe passes
pixels: cancellation/resource signals still block production availability. Cancellation cannot
interrupt a blocked native driver call; it suppresses late delivery and closes resources afterward.
