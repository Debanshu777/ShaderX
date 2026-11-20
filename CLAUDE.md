# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

Follow [AGENTS.md](AGENTS.md) for the shared documentation-update rules. Every
codebase change requires updates to both `README.md` and affected Markdown guides
in `docs/` in the same PR, including tests, internal refactors, tooling, and CI.
Preserve the inline README showcase and run the documented site checks before
completing a task. Publishing runs only after a PR merges into `main`.

## Project overview

**ShaderX** is a Kotlin Multiplatform library that exposes GPU shader effects through Compose
Multiplatform `Modifier` extensions. Targets: **Android (API 31+ native blur; API 33+ AGSL)**, **iOS**, **JVM/Desktop
**, and **Wasm/JS** (the last three via Skia's `RuntimeEffect`). Published to Maven Central as
`io.github.debanshu777:shaderx`. The repo also contains demo apps under `samples/`: `Sampler` is a four-tab visual teaching app; `ShaderLab` and
`ASCIICamera` consume the local library via `projects.shaderx`; `VerticalCarousel` also uses
the local project. ShaderLab also
includes sample-only modules `imagelib` and `audiolib` (mic capture + FFT → spectrum textures).

## Common commands

JDK 21 is required (CI uses Temurin 21; `:shaderx` Android target compiles to `JVM_17`).

**Library**

- Build all targets: `./gradlew :shaderx:build`
- Run all checks/tests: `./gradlew :shaderx:check`
- JVM-only test source set: `./gradlew :shaderx:jvmTest`
- Single test class: `./gradlew :shaderx:jvmTest --tests "FQCN"`
- Publish locally for consumers: `./gradlew :shaderx:publishToMavenLocal`

**Documentation**

- Guides and contributor setup: `docs/index.md` and `docs/contributing.md`.
- Install the pinned tools in a virtual environment: `python -m pip install -r requirements-docs.txt`.
- Validate: `python scripts/check_docs.py`, `zensical build --strict`, then `python scripts/check_site.py`.
- Preview: `zensical serve`. Publishing instructions are in `docs/maintaining.md`.
- The release coordinate is `shaderxVersion` in root `gradle.properties`; publication status is recorded in `docs/index.md`. Keep examples
  explicit about which version they require. Custom effects can use `authoring.ShaderDefinition`
  with a `ShaderProgram` and declarative bindings; editable definitions reuse `ParameterSchema`.
  `RuntimeShaderEffect`/`AnimatedShaderEffect` remain manual escape hatches. The abstract effect
  bases require internal handlers and cannot be extended from consumer modules.

**Sample apps** (module paths verified against `settings.gradle.kts`)

- Android demo: `./gradlew :samples:ShaderLab:androidApp:installDebug`
- Visual sampler: `./gradlew :samples:Sampler:composeApp:run`
- Advanced desktop playground: `./gradlew :samples:ShaderLab:composeApp:run`
- iOS framework, then open `samples/ShaderLab/iosApp/iosApp.xcodeproj` in Xcode:
  `./gradlew :samples:ShaderLab:composeApp:linkDebugFrameworkIosSimulatorArm64`
- ASCIICamera Android: `./gradlew :samples:ASCIICamera:androidApp:installDebug`

## Architecture

### Source-set hierarchy (`shaderx/build.gradle.kts`)

The KMP layout is the central thing to understand before editing anything in `:shaderx`:

- `commonMain` defines abstractions and effect implementations only — no rendering.
- A custom intermediate `skiaMain` source set is `dependsOn`'d by `iosMain`, `jvmMain`, and
  `wasmJsMain`. All three of those platforms share one Skia-based factory.
- `androidMain` does **not** depend on `skiaMain`. It uses Android's `RuntimeShader` (AGSL)
  directly.

So the working rule is: when adding/changing effect rendering, you usually touch `commonMain` (
definition) + `skiaMain` (Skia path) + `androidMain` (AGSL path). Don't duplicate logic into each
non-Android platform individually — put it in `skiaMain`.

### Effect type hierarchy (`commonMain/.../effect/`)

- `ShaderEffect` — **sealed** root interface (`@Stable`). Carries `id`, `displayName`, `parameters`,
  and `withTypedParameter` (canonical) / `withParameter` (Float convenience wrapper) for immutable
  updates.
- `RuntimeShaderEffect` — supplies AGSL/SkSL `shaderSource` plus `buildUniforms(width, height)`.
  Concrete effects live in `effect/impl/`.
- `AbstractRuntimeShaderEffect` — abstract class that implements the `withTypedParameter`/
  `getParameterValue`/`getTypedParameterValue` dispatch via a
  `parameterHandlers: Map<String, ParamHandler<*>>`. Prefer this base for new effects — it
  eliminates ~70 lines of boilerplate. Parameter range validation is automatic.
- `NativeEffect` — wraps a platform-native effect (e.g. `BlurEffect`). Routed to per-platform native
  APIs instead of compiling shader source.
- `AnimatedShaderEffect` — adds `withTime(seconds)`, `isAnimating`, and `buildFrameUniforms()`.
  Prefer `AbstractAnimatedShaderEffect` (enforces structural `equals`/`hashCode` that **exclude
  `time`** so Compose session keys stay stable across frames). `isAnimating` controls whether
  `time` advances; `Modifier.shaderEffect` still refreshes `buildFrameUniforms()` each frame when
  paused so live uniforms (e.g. FFT textures) keep updating.
- `CompositeEffect` — chains effects. Parameter IDs are namespaced as
  `"${index}\u001F${originalId}"` (U+001F delimiter, not underscore, to avoid collision with effect
  IDs containing `_`). The `+` operator on `ShaderEffect` builds composites.

### Factory layer (`factory/ShaderFactory.kt`)

- `ShaderFactory : AutoCloseable` — call `close()` to release compiled GPU resources when the
  factory is no longer needed.
- `BaseShaderFactory` (in `commonMain`) routes by effect type. Ordered composite snapshots use native composition on Android and Skia. Required child failures
  fail the whole chain. Optional pipeline sessions use one caller-owned clock and transactional snapshots.
- `createAnimatedSession(effect, w, h)` → `ShaderSession` — holds instance-scoped GPU state so
  legacy per-frame uniforms can update with retained compiled state. `refresh(effect, w, h)` applies
  `buildFrameUniforms()` (Android in-place; Skia reapplies) and returns a new `RenderEffect`
  wrapper. Always `close()` the session (Compose does this via `DisposableEffect`).
- `expect fun ShaderFactory.Companion.create(maxCacheSize: Int = DEFAULT_CACHE_SIZE)` is implemented
  by:
    - `androidMain` → guarded `AndroidShaderFactory` on API 33+ (real access-order LRU
      `RuntimeShader` cache, thread-safe), or `AndroidNativeShaderFactory` on API 31–32
      (native blur/blur-only chains; runtime requests fail before providers). Background creation
      has a separate guarded API 33 helper; keep all RuntimeShader fields/signatures behind it.
    - `skiaMain` → `SkiaShaderFactory` (`RuntimeEffect` cache, closes Skia `Managed` objects on
      eviction).
- Both factories use a real access-order `LruCache` (see `factory/LruCache.kt` expect/actual).
  Cached source compilation and frame binding have different ownership. Legacy animated sessions
  retain native state; declarative Android static color snapshots and source-free backgrounds may
  compile fresh instance-owned shaders to preserve independent color/value state. Do not route
  those mutable snapshots through a shared shader instance. Measure compilation, binding and
  allocation separately; passing raster tests proves no frame-budget or allocation claim.

### Uniforms (`uniform/`)

- Scalar/color uniforms: `FloatUniform`, `ColorUniform`, etc. in `Uniform.kt`.
- `TextureUniform` — binds an extra `ImageBitmap` as `uniform shader <name>;` (beyond the implicit
  `content` input). Maps to `RuntimeShader.setInputShader` on Android and Skia `child` shaders.
  Pair with `ShaderTileMode` for clamp/repeat/mirror.
- Live textures (FFT spectra, LUTs updated every frame): resolve the bitmap inside
  `buildFrameUniforms()` (or via a source lambda), exclude the image from structural equality, and
  let `Modifier.shaderEffect`'s session loop refresh each frame. Optional `frameDataKey` forces
  Compose invalidation when identity would otherwise skip.

### Compose surface (`compose/ShaderModifier.kt`)

Public entry points: `Modifier.shaderEffect(effect)`, `Modifier.shaderEffectWithResult`,
`rememberShaderEffect`, `rememberRenderEffect`. The default `factory` parameter is
`LocalShaderFactory.current` — a process-wide singleton. Callers who need a scoped cache use
`rememberShaderFactory()` + `CompositionLocalProvider(LocalShaderFactory provides factory)`.

For `AnimatedShaderEffect`, `Modifier.shaderEffect` owns the `ShaderSession` + per-frame loop —
do **not** also wrap with `rememberShaderEffect` for normal usage. Prefer `rememberShaderEffect`
only when applying `RenderEffect` manually via `graphicsLayer`.

### Composite chaining

ShaderX supports ordered on-screen `CompositeEffect` chains on Android API 31+ for
blur-only children, API 33+ for runtime children, and Skia. `shaderEffect` chains are static; the additive controlled composite overload supplies one
ShaderPlayback timestamp to every animated child. Optional ShaderPipelineProvider sessions freeze
runtime uniforms, copy numeric arrays and replace whole filters only after success. Images remain
borrowed. Pipeline snapshots allocate native filters; Android color children use fresh shaders while
non-color snapshots use the existing source cache. ImageProcessor still rejects composites.

### ImageProcessor

`ImageProcessor.create(factory: ShaderFactory)` — the factory is required so the processor shares
the factory's compiled-shader cache. Don't construct without a factory.

### Result type

All factory operations return `ShaderResult<T>` (`result/ShaderResult.kt`) with a `ShaderError`
sealed type. Combinators: `map`, `flatMap`, `fold`, `recover`, `onSuccess`, `onFailure`,
`getOrNull`, `getOrElse`. Reuse these variants for new failure paths instead of throwing.

### Sample audio pipeline (`samples/ShaderLab/audiolib`)

Sample-only KMP module (not published with ShaderX):

- `AudioCapture` — expect/actual mic capture (Android `AudioRecord`, iOS `AVAudioEngine`, JVM
  `TargetDataLine`, Wasm Web Audio `AnalyserNode`).
- `Fft` — pure-Kotlin spectrum; `packMagnitudesToImage` packs bins into a 1×N `ImageBitmap`.
- ShaderLab's Audio tab feeds that texture into `AudioVisualizerEffect` /
  `AudioDistortionEffect` via `withFftDataSource { ... }`.

## Conventions specific to this repo

- `:shaderx` enables `kotlin { explicitApi() }` — every new public declaration in the library needs
  an explicit `public` modifier or it won't compile. Keep helpers `internal`.
- The build calls `applyDefaultHierarchyTemplate()` plus the manually-created `skiaMain` source set.
  Shared non-Android logic goes in `skiaMain`.
- Dependencies belong in the version catalog at `gradle/libs.versions.toml`, not inline in module
  `build.gradle.kts` files.
- `settings.gradle.kts` enables `TYPESAFE_PROJECT_ACCESSORS`, so sample modules import the library
  as `implementation(projects.shaderx)`.

## Adding a new effect (the most common task)

1. Create `shaderx/src/commonMain/kotlin/com/debanshu/shaderlab/shaderx/effect/impl/MyEffect.kt`
   extending `AbstractRuntimeShaderEffect` (or `NativeEffect` if delegating to a platform API).
   For animated / live-uniform effects, extend `AbstractAnimatedShaderEffect` and override
   `buildFrameUniforms()` for per-frame data (`time`, textures). Declare `id`, `displayName`,
   `shaderSource`, `buildUniforms`, and `parameterHandlers`. See `GrayscaleEffect` for a minimal
   static example; `WaveEffect` / `AudioVisualizerEffect` for animated + live-texture patterns.
2. If it's a `NativeEffect`, review both Android factories (`AndroidShaderFactory` and
   `AndroidNativeShaderFactory`), capabilities, **and** `SkiaShaderFactory.createNativeEffect`.
3. To surface it in `ShaderX.builtInEffects()`, register it in `cachedBuiltInEffects` in
   `shaderx/src/commonMain/.../ShaderX.kt`.
4. AGSL/SkSL source must declare `uniform shader content;` and a `half4 main(float2 fragCoord)`
   entry point — see the format documented at the top of `RuntimeShaderEffect.kt`. Extra textures
   need matching `uniform shader <name>;` declarations and a `TextureUniform` in `buildUniforms` /
   `buildFrameUniforms`.
5. Add `@Immutable` to the class and annotate each parameter in `parameterHandlers` with the correct
   handler helper (`floatHandler`, `colorHandler`, `toggleHandler`).
