---
title: Contributor setup, project structure, and tests
description: Set up ShaderX with JDK 21 and the Android SDK, run targeted Gradle checks, understand Kotlin source sets, add effects, and preview the documentation locally.
---

# Contributor guide

Use distinct local validation coordinates for checkout artifacts; see [publication status](index.md#releases-and-development-docs). The [stable release checklist](release-plan.md) separates the historical frozen-consumer baseline from the latest-stable compatibility audit.

`shaderxVersion` in root `gradle.properties` is the release version source. Keep README,
installation and `llms.txt` examples aligned with that value. Routine guides explain the
release API; history belongs in `CHANGELOG.md` and upgrade actions in [migration](migration.md).
`scripts/check_docs.py` validates release-version coherence offline across the README,
installation coordinates/version catalog, `llms.txt` and the changelog using that source property.
This check does not establish Central publication. Preserve established section anchors when
shortening headings. Publication is recorded in one
[central status note](index.md#releases-and-development-docs), rather than repeated availability
labels throughout the guides.

Start with a focused issue or pull request. Documentation fixes, reproducible bug reports, effect implementations, and platform testing all help. See the repository's [contribution guidelines](https://github.com/Debanshu777/ShaderX/blob/main/CONTRIBUTING.md).

For upgrade actions, use the [migration guide](migration.md). Validation commands and their
coverage are documented below; the [stable release checklist](release-plan.md) tracks publication
requirements. Keep physical hardware, performance and Android/Klib binary checks distinct from
source compilation and raster results.

## Local setup

1. Fork and clone [ShaderX](https://github.com/Debanshu777/ShaderX).
2. Install **JDK 21** and configure your IDE's Gradle JDK or `JAVA_HOME` to use it. Run `java -version` and `./gradlew --version` to check.
3. Install the Android SDK platform matching `android-compileSdk` in `gradle/libs.versions.toml` (currently API 37). Set `ANDROID_HOME` or add `sdk.dir=/absolute/path/to/Android/sdk` to the ignored root `local.properties`.
4. Use the checked-in Gradle wrapper. The version catalog and wrapper are the sources of truth for dependency and build versions.
5. For iOS builds, use macOS with Xcode, its command-line tools, and the required simulator installed. Linux and Windows contributors can work on JVM, Android, Wasm, and documentation without running iOS tests.

No Maven publishing credentials or TMDB token are needed for library tests or ShaderLab. The [carousel](samples.md#verticalcarousel) optionally uses its own token. On Windows, replace `./gradlew` with `gradlew.bat`.

## Build and test

```shell
# Common tests and Skia integration tests on the JVM
./gradlew :shaderx:jvmTest

# Common tests in headless Chrome (install Chrome first)
./gradlew :shaderx:wasmJsBrowserTest

# A focused test class
./gradlew :shaderx:jvmTest --tests 'com.debanshu.shaderlab.shaderx.FactorySmokeTest'

# Full library build, including checks (macOS for all Apple targets)
./gradlew :shaderx:build

# Generate the API reference
./gradlew :shaderx:generateDocs
```

JVM test results are in `shaderx/build/reports/tests/jvmTest/index.html`. Dokka output is in `shaderx/build/dokka/html/index.html`. Use the [sample commands](samples.md) to test visual output on the affected platforms; unit tests cannot establish how an effect looks on every GPU.

Browser tests use a Mocha setup hook to await Skia's WebAssembly runtime before executing the common tests that allocate `ImageBitmap`s. This avoids a startup race on slower runners; it does not skip or retry failing assertions. Browser results are in `shaderx/build/reports/tests/wasmJsBrowserTest/index.html`. CI runs `:shaderx:build` (which already includes `check`) and uploads XML/HTML test reports for each runner even on failure.

Release numbering and branch guards have Python regression tests alongside the documentation-policy tests; run `python -m unittest discover -s scripts/tests -v`. The [stable release workflow](maintaining.md#release-workflow) requires `confirmVersion` to match root `gradle.properties`, verifies publication/tag state and passes `-PshaderxVersion=...` to Gradle. Ordinary builds use the same release version; library tests do not need publishing secrets.

## Project structure

| Path | Responsibility |
| --- | --- |
| `shaderx/src/commonMain` | Public effects, parameters, uniforms, results, and Compose modifiers |
| `shaderx/src/androidMain` | Android AGSL factory and image-processing implementation |
| `shaderx/src/skiaMain` | Shared Skia implementation for iOS, JVM, and Wasm |
| `shaderx/src/iosMain`, `jvmMain`, `wasmJsMain` | Platform factory creation |
| `shaderx/src/commonTest` | Effect, parameter, uniform, and result contracts |
| `shaderx/src/jvmTest` | Skia factory, texture, and session integration tests |
| `samples/Sampler` | One visual app with four small public-SDK examples, desktop/Wasm/Android hosts and an iOS framework |
| `samples/ShaderLab` | Advanced playground plus sample-only image/audio modules |
| `samples/ASCIICamera`, `samples/VerticalCarousel` | Focused Android examples |
| `docs` | Handwritten guides and documentation site assets |

## Add an effect

1. Add the implementation in `shaderx/src/commonMain/kotlin/com/debanshu/shaderlab/shaderx/effect/impl` with a unique ID and KDoc.
2. Inside the library, use `AbstractRuntimeShaderEffect` or `AbstractAnimatedShaderEffect` and the internal typed parameter handlers. External app effects use the [public interface](custom-effects.md) instead.
3. Keep effect instances immutable. For animated effects, exclude time and live textures from structural equality; include configuration parameters.
4. Declare `uniform shader content;` and a `half4 main(float2 fragCoord)` entry point. Match all Kotlin uniforms to the shader declarations.
5. Register built-in effects in `ShaderX.builtInEffects()`. For a native effect, implement it in both Android and Skia factory branches.
6. Add appropriate parameter/contract tests and a shader-compilation check. Validate the appearance on relevant sample targets.
7. Update the README effect list, [effect catalog](effects.md), and changelog. State availability accurately.

The library enables Kotlin explicit API mode. New public declarations need explicit visibility and types; keep implementation details internal. Put dependencies in `gradle/libs.versions.toml`.

### Strict parameter validation coverage

Development schemas are tested independently of legacy updates. Common tests cover definition
validity, typed/dynamic diagnostics, ownership, reset, precision, finite-only clamping, ARGB
alpha, and defensive snapshots. JVM pilot pixels compare strict Gradient/Wave edits with their
legacy equivalents. The separate development consumer compiles schema usage without internal
imports; the explicit published 0.1.3 legacy consumer does not import development APIs.

When changing a parameter, update its declaration, public KDoc, effect guide, and the reviewed
ABI additions. Keep existing legacy NaN/coercion/unknown-ID tests unless a deliberate migration
is accepted. Do not infer Android wide-gamut correctness from Skia raster or sRGB device tests.
See the [parameter validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx01-parameter-contract.json).

## Try the library in another project

On macOS with the required target toolchains:

```shell
./gradlew :shaderx:publishToMavenLocal -PshaderxVersion=0.2.0-local-validation
```

In the consuming project's dependency repositories, temporarily add `mavenLocal()` before `mavenCentral()`, then use the distinct `0.2.0-local-validation` coordinate above. Use a unique validation suffix so local changes cannot be confused with a released artifact. Do not add `mavenLocal()` to production setup instructions. For work in this repository, ShaderLab and ASCIICamera already depend on the local project.

## Performance validation

Measure a specific workload before changing shader arithmetic or caching policy. Record the platform, device/GPU, build mode, viewport size, effect parameters, and whether the shader cache is cold or warm. Compare frame times and allocations at the same settings; a JVM allocation count is not a GPU frame-rate measurement.

The built-in animated effects share immutable shader sources and parameter handlers across frame copies. Run the optional JDK 21 allocation diagnostic to measure the remaining `withTime()` object allocations:

```shell
SHADERX_ALLOCATION_BENCHMARK=1 ./gradlew :shaderx:jvmTest --tests '*AnimatedEffectAllocationBenchmark' --rerun-tasks
```

The report in `shaderx/build/test-results/jvmTest/TEST-com.debanshu.shaderlab.shaderx.AnimatedEffectAllocationBenchmark.xml` contains bytes per copy after warmup. The diagnostic keeps results alive through a volatile sink and has no timing threshold; values depend on the JVM and do not include uniform binding, native allocations, or GPU execution. It is skipped unless the environment variable is set. Existing effect contract tests cover parameter updates, time, and equality.

Cache and native-lifetime tests cover access-order eviction, failed compilation preserving cached entries, session cleanup, active sessions surviving cache eviction, and actual pixel output after temporary Skia wrappers are released. Compose lifecycle tests cover live-data replacement, colliding hashes, factory replacement, and manual animation. The sample audio cache tests verify that repeated snapshots reuse their texture while changed data or bin counts produce an update:

```shell
./gradlew :shaderx:jvmTest :samples:ShaderLab:audiolib:jvmTest :samples:ShaderLab:imagelib:jvmTest
./gradlew :shaderx:testAndroidHostTest
```

Android host tests verify that unsupported offline export fails before decoding, compiling, or allocating graphics resources; they deliberately call no Android APIs and are isolated from common bitmap tests that need a device. JVM image tests check bounded dimensions, pixels, transparency, invalid input, and preservation of original bytes.

The optional FFT diagnostic compares one-shot processing and a reused workspace in the same JVM with alternating rounds; it reports allocations and median CPU time, with no timing threshold:

```shell
SHADERX_AUDIO_BENCHMARK=1 ./gradlew :samples:ShaderLab:audiolib:jvmTest --tests '*SpectrumAllocationBenchmark' --rerun-tasks
```

Use the [sample applications](samples.md) to inspect animated output and transitions. Profile camera tab disposal, large-photo selection, microphone start/stop, and long-running animation on actual target devices. Sample image decoding bounds retained display bitmaps while keeping original bytes for export. The sample audio pipeline reuses FFT working buffers; Android/desktop device work runs on an IO worker with tests for cancellation and restart ownership. Device-specific decode peaks, audio drivers, and GPU frame times still require profiling on actual hardware. Keep Skia export factories separate from UI factories, as explained in [image processing](image-processing.md).

## Consumer and ABI validation

Separate modules under `validation/` compile minimal, editable, and animated custom effects,
Compose usage, and explicit third-party factory/session implementations using only public APIs.
They are not library friend source sets. Current development mode relies on ShaderX's transitive
Compose API dependencies; the published 0.1.3 mode adds the explicit Compose dependencies required
by that older artifact. This tests 0.1.3 with the current toolchain/Compose versions, not its historical build environment. Keep new authoring, playback, and generated-code fixtures alongside their contract tests
and record their validation outcomes. Development authoring fixtures now compile static editable tint,
animated pixel distortion, and minimal image-input definitions in fresh Android-only and KMP
modules. `PlaybackConsumer.kt` also compiles the controlled modifier and playback owner against
the public SDK, including host lifecycle/accessibility inputs. SX07 adds native-rendered workbench starters and generated image/live caller-input templates.

```shell
./gradlew :validation:kmp-consumer:jvmTest :validation:kmp-consumer:compileKotlinWasmJs :validation:android-consumer:compileDebugKotlin
./gradlew :validation:kmp-consumer:compileAndroidMain :validation:kmp-consumer:compileKotlinIosSimulatorArm64
# Published artifact, legacy factory contract, and explicit Compose dependencies:
./gradlew :validation:kmp-consumer:jvmTest :validation:android-consumer:compileDebugKotlin -PshaderxConsumerVersion=0.1.3 -PshaderxConsumerLegacy=true
```

`-PshaderxConsumerVersion=...` selects a Maven artifact instead of the project dependency;
`-PshaderxConsumerRepository=file:///absolute/staging/repository` adds a staging repository.
Use a unique staged version to avoid cached artifact confusion. Fresh compilation is a source gate,
not proof that already compiled applications link. In particular, 0.1.3's factory interface lacks
`createAnimatedSession`; a legacy third-party implementation needs migration before using that
current method. The compatibility window and a legacy binary-link harness remain SX15 work.

Kotlin 2.3.21's built-in experimental validator is enabled for JVM classfiles; `:shaderx:check`
also runs `:shaderx:checkKotlinAbi`. Keep the generated API file byte-for-byte, including its final blank separator; `.gitattributes` exempts only that generated EOF separator from whitespace checks. Review `shaderx/api/jvm/shaderx.api` alongside every public
contract change. Run `:shaderx:updateKotlinAbi` only after reviewing the deliberate change, never
automatically to make CI green. This baseline describes today's development checkout, not a
compatibility guarantee against 0.1.3. Klib validation is disabled initially, and the new Android KMP
plugin is covered by consumer compilation rather than an Android ABI dump.

### Ecosystem integration validation

`validation/ecosystem-consumer` is a separate consumer compilation with exact optional pins for
Coil 3.4.0 and Haze 1.7.2. It exercises only public ShaderX APIs; no library friend path or
core dependency is added. See the [recipe guide](ecosystem.md) for ownership and surface limits.

```shell
./gradlew :validation:ecosystem-consumer:jvmTest
./gradlew :validation:ecosystem-consumer:compileKotlinWasmJs :validation:ecosystem-consumer:compileKotlinIosSimulatorArm64 :validation:ecosystem-consumer:compileAndroidMain
./gradlew :validation:ecosystem-consumer:dependencies --configuration jvmRuntimeClasspath
```

The JVM suite also renders the demo at 720 and 390 pixel widths, verifies the ordinary Compose
reveal on patterned pixels, and checks spatial blur across a color edge. PNG previews are written
to `validation/ecosystem-consumer/build/reports/ecosystem-render/`. These are Skia CPU raster
checks, not native mobile or physical GPU evidence. The exact `ImageRecipeScreen` guide host
is compiled in the consumer alongside the runnable demo.

Check actual loading/success/failure, retained playback across model replacement, request
cancellation on removal and native-session cleanup. Render source capture and foreground
separately: a screenshot of visible content alone cannot establish what Haze recorded. Compare
resolved Compose, AndroidX and Skiko versions with core before accepting a new optional pin.
Do not upgrade the library toolchain merely to compile a current ecosystem release. Record
commands, exact resolved versions, pixels, renderer and disposal results in
`validation/evidence/sx14-ecosystem-integration.json`; distinguish compile-only targets from
rendered targets and software emulators from physical hardware.

### Declarative authoring contract coverage

`ShaderDefinitionTest` covers declaration-owned keys, missing/duplicate/reserved bindings,
strict generated updates, frame-provider coverage and kind validation. `DefinedEffectRenderTest`
uses real Skia pixels for independent color instances, alpha, source/configuration/size replacement,
failed replacement recovery and disposal. Generated sessions replace structural state while manual
runtime interfaces retain their existing update contracts. Keep these tests alongside external
`AuthoringConsumerTest` examples; do not infer Android compiler behavior from Skia results.

The ShaderLab registry is sample-local and requires no registration service in the library.
Its typed controls, reset and playback flag are covered alongside preset and Kotlin-generator
validation. Generated Kotlin must also compile in the separate consumer module; string-shape
assertions alone cannot prove a usable integration example. The source workbench also checks its explicitly supported uniform declaration subset before native compilation. See
[custom authoring](custom-effects.md) for declarations and the [authoring validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx02-custom-effect-authoring.json)
for platform evidence and remaining acceptance gates.

### Playground reuse validation

The sample's shared registry/project model, strict preset codec and Kotlin generator are the base
for SX07. Keep rich custom schemas as editor truth; never reconstruct unbounded controls from
legacy full-Float-range descriptors. Controls must retain exact numeric values, arbitrary ARGB
alpha and strict booleans. Preset tests cover malformed input, format/schema versions, definition
identity, duplicate/missing/unknown parameters, type/range validation and ordered round trips.
Kotlin export tests must generate actual consumer source with interpolation, backslashes,
newlines, embedded triple quotes, keyword/colliding IDs and non-English locales. Compile those
outputs outside the sample/library module, and inspect the reviewed additive binding descriptor
ABI. A handwritten equivalent example does not establish generator correctness.

`GeneratedSourceProducerTest` runs as part of ShaderLab's JVM tests and writes the actual
portable starter/adversarial exports to `samples/ShaderLab/composeApp/build/generated-playground-consumer`.
It generates under a German locale and checks locale-independent output. In development mode,
both consumer modules use that generated source directory, and their Kotlin compilation tasks
depend on the sample JVM tests. No manual copying or checked-in equivalent snippet is needed.
`GeneratedPlaygroundConsumerTest` then verifies the compiled adversarial export's source,
metadata, defaults, exact edited values and uniforms using only public SDK APIs.

```shell
./gradlew :samples:ShaderLab:composeApp:jvmTest :validation:kmp-consumer:jvmTest :validation:android-consumer:compileDebugKotlin
./gradlew :validation:kmp-consumer:compileKotlinWasmJs :validation:kmp-consumer:compileKotlinIosSimulatorArm64 :validation:kmp-consumer:compileAndroidMain
```

Run the relevant library/target checks as well. Build the production Wasm distribution, then inspect keyboard entry,
clipboard-denial/selectable fallback, preset exchange, playback controls and responsive layouts
in the app and guides. Keep commands and outcomes with the [playground validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx06-playground-and-reuse.json).
An iOS/Android source compile does not establish file-picker or rendered device behavior.

### Controlled playback contract coverage

`ShaderPlaybackTest` covers initial phase, pause/resume, deterministic seek, speed changes,
zero-speed settling, suspension, shared versus independent state, stale timestamps and invalid
numeric input. `ControlledPlaybackTest` uses actual Compose/Skia pixels and `hasInvalidations()`
(which includes frame-clock awaiters) to verify paused/reduced/inactive/zero-speed settling, live
revisions, phase continuity and disposal. The physical-device controlled-owner test uses AGSL
RenderNodes rather than Compose layers. Keep scheduling and rendered backend evidence distinct: an assembled Android APK or a passing Skia test cannot establish AGSL layer invalidation.
The tiny graphics-layer rotation remains until a replacement is verified on actual backends.
Run the consumer commands above and the rendered/device commands below after changes; record
backend evidence and remaining gates in the [playback validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx03-playback-and-live-inputs.json).

### Capability and fallback contract coverage

Development capability tests distinguish static content, standalone animated sessions, and image export. Keep built-in query evaluation and backend preflight aligned, including Skia's on-screen composite versus unsupported offline-export distinction and Android's fail-before-decode export path. Queries must not compile source, populate caches, or invoke uniform/live-data providers. Supported operation tests still exercise invalid-source compilation failure; metadata is not shader validation.

Third-party consumer fixtures retain an explicit factory implementation without the optional provider and exercise unknown support separately from an opt-in wrapper. Do not add abstract capability members to `ShaderFactory` or append default parameters to existing JVM methods. Static fallback tests cover preservation, failure propagation, one complete substitute attempt, and retention of the original cause. No new `ShaderError` or `ShaderResult` subtype is needed for these APIs. Review the additive JVM ABI entries along with these contracts; already-compiled consumer linkage remains a separate SX15 gate.

Run the existing library and consumer commands above for these cases. Pure backend-matrix tests can check Android API decisions without graphics hardware, but they do not establish actual AGSL rendering; retain the device gates below. See the [capability validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx04-capabilities-and-fallback.json) and the [platform operation matrix](platforms.md#operation-queries-in-development).

## Rendered and device validation

```shell
# Actual Android HWUI/AGSL, API 33+ device or emulator already connected:
./gradlew :shaderx:assembleAndroidDeviceTest
./gradlew :shaderx:connectedAndroidDeviceTest
# Repeated CPU/raster diagnostic (no GPU FPS or display deadline claim):
SHADERX_RENDER_BENCHMARK=1 ./gradlew :shaderx:jvmTest --tests '*RenderBaselineBenchmark' --rerun-tasks
```

Android tests use `HardwareRenderer`, a `RenderNode` content layer, and `ImageReader` readback;
they exercise factory-produced filters rather than compiling a separate shader in the test.
A solid-color no-filter control first establishes that readback works. The harness may resubmit
up to three additional render requests when HWUI reports a dropped frame; it never retries a failed pixel
assertion and still fails lost/stopped surfaces.
They check built-ins, chains, independent cached/session instances, translucent color updates,
textures, resize, compilation-error recovery, eviction, and repeated disposal. Android resources
are enabled for the device source set's Compose asset wiring. Inspect device results in
`shaderx/build/reports/androidTests/connected/` and record device/GPU, OS, and emulator versus
physical hardware; assembling the APK alone proves no AGSL rendering behavior.

`ColorUniformRenderTest` verifies `layout(color)` on Skia sRGB and linear-sRGB raster destinations
and premultiplied translucent output. `UniformTest` records that legacy `fromColor` copies components
without a color-space conversion: explicitly convert Compose colors to sRGB first. These tests do
not establish wide-gamut Android destination behavior by themselves. The additive
`DefinedEffectDeviceTest` uses API 34+ HardwareBufferRenderer and explicit FP16 sRGB, linear-sRGB
and Display-P3 destinations, including alpha and an explicit Compose conversion. It passed on
Pixel 9/API 37; other hardware/API levels remain separate gates. Unsupported FP16 targets skip
explicitly. See [custom effects](custom-effects.md#color-uniform-audit).

The opt-in raster diagnostic reports five rounds after warmup for cold creation, warm creation,
session refresh, and changing slider values at 64×64 using `WaveEffect`. It measures elapsed CPU-side
work and raster drawing, including wrapper allocation; it does not measure display frames, idle
recompositions, GPU execution, or native memory stabilization. Keep raw rounds and environment in
review evidence; set budgets only after product requirements and hardware measurements exist.
The [initial validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx12-baseline.json)
records its measured environment, coverage and remaining gates.

### Linux CI browser startup

Ubuntu restricts unprivileged user namespaces for downloaded Chrome binaries outside its
packaged-browser AppArmor profile. CI selects the hosted runner's `/usr/bin/google-chrome`,
which launches the normally installed browser covered by that profile. macOS uses the Chrome
setup action. The selected path is passed explicitly to every Karma test task.

Before compiling, Linux CI opens `about:blank` headlessly with a private temporary profile and
checks its rendered HTML. This catches browser startup failures before an expensive build;
browser sandboxing remains enabled. See [Chromium's AppArmor explanation](https://chromium.googlesource.com/chromium/src/+/main/docs/security/apparmor-userns-restrictions.md)
and the [runner's packaged Chrome installation](https://github.com/actions/runner-images/blob/main/images/ubuntu/scripts/build/install-google-chrome.sh).
Each OS matrix job completes independently so a failure does not hide the other platform's results.

## Preview and check documentation

Guide previews use Python 3.11+. The complete checked site also includes the production Wasm playground, and the four-tab sampler, so install JDK 21 and the Android SDK from [local setup](#local-setup); Apple tooling is not required for those bundles:

```shell
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
.venv-docs/bin/python scripts/check_docs.py
.venv-docs/bin/python -m unittest discover -s scripts/tests -v
./gradlew :samples:ShaderLab:composeApp:wasmJsBrowserDistribution :samples:Sampler:composeApp:wasmJsBrowserDistribution --no-daemon --no-parallel
.venv-docs/bin/zensical build --strict
.venv-docs/bin/python scripts/package_playground.py
.venv-docs/bin/python scripts/check_site.py
.venv-docs/bin/zensical serve
```

Build the Wasm distribution again after sample or library changes. Re-run `package_playground.py` after each site build because the site generator clears its output. `zensical serve` previews the guides; to inspect the combined artifact, serve `site/` with `python -m http.server --directory site 8000`.

On Windows, use `.venv-docs\Scripts\python` and `.venv-docs\Scripts\zensical`. Open the URL printed by Zensical. Guides should also remain readable as ordinary Markdown on GitHub. Use descriptive page titles, accurate examples, relative links between guides, and a unique `description` in each page's front matter.

The [maintainer guide](maintaining.md) covers documentation publishing, repository discovery settings, and release updates.

Read [AGENTS.md](https://github.com/Debanshu777/ShaderX/blob/main/AGENTS.md) for the change-to-guide map used by coding agents. Every codebase change requires updates to **both `README.md` and the affected Markdown guides in `docs/` in the same PR**, including tests, refactors, configuration, scripts, and workflows. The Documentation PR check fails if either update is missing; there is no PR-body opt-out. Prose-only and media-only changes do not require companion edits. See the [maintainer guide](maintaining.md#keep-documentation-current-with-coding-agents) for the policy and examples of useful internal-change documentation.

The site publishes automatically only after a PR merges into `main` and its documentation build passes. Direct pushes, preview builds, and unmerged PR closures do not deploy. The documentation URL is [debanshu777.github.io/ShaderX](https://debanshu777.github.io/ShaderX/).

### Sampler validation

The [visual sampler](samples.md#shaderx-sampler) keeps public SDK calls in four small tab files.
Its shell retains primitive tab state and owns the animation clock; image/slider presentation
helpers do not hide a registry or authoring framework. Check actual original/first/final stage
pixels as well as controls, tab persistence and paused/off-tab playback.

```shell
./gradlew :samples:Sampler:composeApp:jvmTest :samples:Sampler:composeApp:wasmJsBrowserTest --no-parallel
./gradlew :samples:Sampler:androidApp:assembleDebug :samples:Sampler:composeApp:compileKotlinIosSimulatorArm64 :samples:Sampler:composeApp:wasmJsBrowserDistribution --no-parallel
```

CI runs sampler JVM/Chrome pixel tests and Android builds on Linux, and compiles Apple source
entrypoints on macOS. Custom tests use the screen's actual definition; chain tests render both
orders and their intermediate stages. Browser Skia startup is awaited before native tests.
Use the [capture flows](samples.md#capture-the-sampler) for visual controls and tab navigation;
keep Android/iOS compilation distinct from physical-device execution.

### Focused ShaderLab navigation

Gallery starts with a visible effect and shows primary tuning before secondary image, reuse and
support tools. Exact scalar drafts remain in memory while their field disclosure is closed;
color entry still accepts all eight ARGB digits. Source/Pipeline navigation retains
workspace data and independent image snapshots (drafts, typed values, keys and scalar playback
position). Tool scopes, clocks and render sessions exist only while their panel is visible;
Workbench factories are panel-local, while Gallery/Pipeline share the composition-owned app cache. Ignore late picker/file
callbacks after detachment. Returning must not silently validate a draft or reuse stale pipeline
Kotlin. `WorkspaceExportTest` checks generated output invalidation after retained value/order edits;
existing workbench revision/pixel tests retain the source-last-good contract. Inspect a failed
source edit across navigation and an ordered pipeline edit in the browser, separately from tests.

### Workbench validation

`WorkbenchModelTest` covers bounded source/form/current-value draft files, exact-candidate atomic
promotion, stale results, reset/recovery and delayed-input tokens. `WorkbenchRenderTest` renders
Warm tint, distortion, animation and image starters through actual Skia ImageProcessor, checks
pixels, raw compiler logs, recovery and native lifetime. Browser UI uses the same definitions and
Kotlin generator. No generated preamble is added and native diagnostics are kept raw; unknown
formats receive no invented location. The narrow declaration checker rejects unsupported
uniform syntax rather than claiming universal reflection. Skiko scalar setters silently ignore
unknown names, so successful native creation alone is insufficient.

Keep actual desktop/mobile editing and stale export inspections separate from backend tests.
The browser PNG intake checks 1 MiB and 256-pixel bounds before native decode. Native workbench
hosts currently use the bundled input. Android's ImageProcessor route is unsupported, so manual
Run cannot be described as AGSL workbench validation. Library Android device suites remain
separate evidence. See the [workbench validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx07-custom-shader-workbench.json) for exact outcomes and gaps.

`WorkbenchDecodeTest` and `WorkbenchBrowserPipelineTest` check decoded input and actual ShaderX
processed output pixels on JVM and Wasm. `WorkbenchRenderTest` also reads the bundled 256px PNG
itself, guarding its RGBA encoding and expected pixels; header/dimension checks alone cannot prove
valid image content. Workbench display and texture intake share the bounded native Compose image
decoder. A fixture with mismatched PNG channel encoding caused the initial blank inspection;
valid comparison pixels also pass through the existing imagelib decoder. This scoped correction
does not establish a general picked-image defect or alter that gallery decoder.

### Portable pipeline validation

Source-order regressions verify that source is captured once before its providers, later provider mutation cannot replace it, and a throwing later source fails before any earlier runtime child compiles.

`PortableCompositionExperimentTest` is shared through `skiaTest` by JVM, Wasm and iOS simulator tests. It compares ordered filters against explicit sequential raster references, retains native filters after temporary wrappers close and active builders after cache closure, and exercises whole-child failure and independent values. `PipelinePlaybackTest` checks actual Compose pixels, shared time, live revisions while paused and transactional replacements, positive→zero→positive Compose target sizes and phase continuity. Sample regressions preserve custom GPU Booleans named `animate` while suppressing only explicitly mapped built-in playback flags. Android device checks exercise HWUI separately. Generated pipeline Kotlin is compiled in the separate consumer after the sample producer runs. Keep raster/simulator and physical GPU evidence distinct, and do not infer ImageProcessor chain support or memory stabilization. See the [pipeline validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx05-portable-composition.json) for exact results and remaining gates.

### Procedural background validation

Keep content filtering, source-free generation and captured-backdrop sampling separate in tests
and capability reports. Background pixels must be checked in an empty sized Box and behind
unchanged foreground, including alpha, clip, padding/modifier order, density and resize. Exercise
independent values/playback, paused/live revisions, whole-background fallback and disposal.
Android AGSL, Skia raster, Wasm and iOS simulator results are separate evidence; a source compile
or desktop result cannot establish device rendering. Retain the accepted constructor/JVM
declarations when adding source requirements; adding a default constructor argument can break
already compiled consumers. See the [background validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx08-background-and-brush.json) for the bounded brush
experiment, exact versioned adapter audit and residual hardware gates.


### Effect collection validation

Collection changes must retain independent reference equations for brightness/contrast/saturation
and the row-major RGBA+bias matrix, not merely compare two calls to the same implementation.
Cover neutral settings, endpoints, wrong types, NaN/infinity, translucent and alpha-zero input,
matrix alpha changes and defensive array/equality behavior. Artistic pixels must establish the
Bayer pattern/rounding and halftone coverage, with actual before/after inspection. Test static
and ordered pipeline pixels separately from source compilation.

The sample producer emits complete actual Kotlin for each registered collection definition;
compile it in the separate consumers alongside ordinary public callers. Rich schema controls and
typed preset round trips must retain exact values, units, roles and bounds. Review the JVM ABI
for additive declarations and keep prior signatures intact. Run JDK 21 library/sample/consumer,
Wasm/iOS raster and available physical AGSL device checks, rebuild the production playground,
and run the required documentation checks above. Record exact commands, environments, results,
image paths, provenance and unavailable hardware in the [collection validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx09-effect-collection.json).
Do not equate a browser viewport with a physical mobile device or claim GPU performance from
raster correctness tests.

### Data and child-input validation

`DataInputContractTest` covers defensive byte snapshots, exact byte counts, overflow/bounds,
frame-kind validation, provider identity, missing shader inputs and immutable graph limits.
`NestedInputRenderTest` exercises actual Skia child values/time, replacement, failure retention,
cache eviction and disposal. Android `ShaderInputsDeviceTest` uses HWUI and independently
remaps raw channels (including alpha-zero RGB) to opaque output, tests centers/filtering/tiling
and compares numerical inputs with color-managed images. Preserve exhaustive Uniform handling
when adding kinds; check the previous JVM ABI for every old constructor/method signature.
Generated input examples must compile in separate consumers and must accept caller rebinding,
without serializing application bytes, images, callbacks or native state. Keep exact target
results and unavailable hardware separate in the [data-input validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx10-data-and-shader-inputs.json).

### Android adoption validation

Keep runtime minSdk, AAR minCompileSdk, emitted classfile level and contributor JDK separate.
Development uses API 31 / compileSdk 35 / Java 17 output; contributors still use JDK 21 and the
catalog SDK for samples. Resolved CMP 1.10.3 Android dependencies include AndroidX UI graphics
1.10.5. Inspect actual AAR metadata rather than copying CMP version labels.

```shell
./gradlew :shaderx:bundleAndroidMainAar :shaderx:assembleAndroidDeviceTest
python scripts/check_android_artifact.py shaderx/build/outputs/aar/shaderx.aar
./gradlew :validation:android-consumer:assembleDebug :validation:android-consumer:assembleRelease :validation:kmp-consumer:compileAndroidMain
./gradlew :validation:android-app:assembleDebug :validation:android-app:assembleRelease
```

The artifact check reads the packaged manifest, metadata and every embedded classfile, including
nested jars. The small Android app uses no sample dependency; release enables R8 with optimized
default rules and debug signing solely for local install checks. Both development consumer modules compile
at SDK 35 with Java 17 output. Explicit legacy mode retains minSdk 33, catalog compileSdk and
Java 21 output for published 0.1.3; it does not claim old-runtime installability. The repository's AGP version is tested; transitive metadata's
AGP 8.6.0 declaration is not an independent minimum-plugin verification.

Run `AndroidAdoptionDeviceTest` explicitly on API 31–32; it checks HWUI blur pixels, chains,
cache/disposal and whole-runtime-graph rejection, content preservation, substitution and guarded
background helpers. Run all AGSL suites separately on 33+. The shared pixel helper skips the
API 33 fence API on 31; synchronous hardware bitmap readback still checks actual output.
These HWUI tests do not establish Compose modifier scheduling/lifecycle behavior.
Use dedicated task-owned AVDs and a verified-free private ADB endpoint; always select the
explicit private device serial. Keep exact installs, startup/shrink results, screenshots and
cleanup in `validation/evidence/sx11-android-adoption.json`. Never infer API 23 support from
AndroidX's minSdk 23. Common metadata compilation also guards portable regex use; the background
comment scanner uses `[\s\S]` rather than a platform-only DOT_MATCHES_ALL option.

### SX13 image export validation

The shared `ImageExportRasterTest` exercises actual exported PNG dimensions, linear stretch,
filtered alpha, independent uniform size, sRGB source conversion, orientation rejection, bounded
header preflight and failure/recovery on JVM, Wasm and iOS. `ImageExportWorkerTest` checks bounded
admission, cancellation without preemption, cleanup/delivery and worker provider execution.
`HardwareImageExportTest` exercises the Android HWUI readback probe kept only in device-test sources; it does not enable a
public Android exporter. Preserve legacy process ABI and optional third-party processor compatibility.
Record software-emulator graphics lifetime signals separately from pixel assertions and physical
GPU claims. The [image export validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx13-image-export.json)
identifies its remaining production gates.

`ImageExportSuspendTest` returns the coroutine test bridge result directly and executes the real
worker-owned suspend exporter on JVM, Wasm browser and iOS simulator. It verifies decoded PNG pixels,
provider execution and cancellation triggered during provider evaluation followed by recovery.
The test-only `kotlinx-coroutines-test` dependency uses the existing catalog coroutine version;
Default is left real, including browser event-loop execution. This is distinct from the synchronous
provider raster tests and does not infer a background Wasm thread or driver preemption.


## Final integration validation

Use JDK 21 and record both `java -version` and `./gradlew --version`. The wrapper's daemon
criteria may select a different installed JDK 21 than `JAVA_HOME`; record the test task's
`javaLauncher` or `--info` process command as well. Do not rewrite user criteria just to make
launcher and daemon paths match. Historical SX12 measurements are not current JDK inventory.

CI now explicitly runs `:samples:ShaderLab:composeApp:wasmJsBrowserTest` once on Linux with
Chrome, in addition to the library build. The suite asserts valid fixture pixels through both
the native workbench decoder and existing imagelib path, then checks the actual tint-processed
PNG channels. The malformed initial bundled PNG was a channel-encoding error; it is not evidence
of a general gallery decoder defect. External consumer compilation depends on the sample JVM
producer and compiles the actual workbench, pipeline, background and data-input Kotlin outputs.
Optional ecosystem JVM pixel tests and Android/Wasm compilation run separately; Apple sample
and consumer sources compile on macOS. Reports, generated inputs, AAR/publication metadata and
R8 mappings are retained by CI, including on failure.

A complete local backend pass includes the common metadata and assembly paths, not just
individual target compilation:

```shell
./gradlew :shaderx:build :shaderx:compileCommonMainKotlinMetadata --no-parallel
./gradlew :samples:ShaderLab:composeApp:jvmTest :samples:ShaderLab:composeApp:wasmJsBrowserTest :samples:ShaderLab:composeApp:iosSimulatorArm64Test --no-parallel
./gradlew :validation:kmp-consumer:jvmTest :validation:kmp-consumer:compileAndroidMain :validation:kmp-consumer:compileKotlinWasmJs :validation:kmp-consumer:compileKotlinIosSimulatorArm64 :validation:android-consumer:assembleRelease --no-parallel
./gradlew :validation:ecosystem-consumer:jvmTest :validation:ecosystem-consumer:compileAndroidMain :validation:ecosystem-consumer:compileKotlinWasmJs :validation:ecosystem-consumer:compileKotlinIosSimulatorArm64 --no-parallel
```

Apple execution requires an installed simulator. These are distinct from physical iOS GPU
and native-window validation. Use `--rerun-tasks` for a fresh evidence run and save reports before
subsequent filtered/legacy runs replace the same JUnit output directories.

### Stage current consumer artifacts without publishing

The init script below generates POM/module metadata and copies built unsigned artifacts into a
local Maven directory. It runs no Maven publication or signing task and changes no repository
version file. Use a unique local version and an empty destination for each run. The script is
for validation; it is not a release or an install recommendation. This all-publication helper
was validated on macOS with Apple tooling: the host must be able to build the native artifacts;
it does not infer binaries for unsupported targets.

```shell
./gradlew -I scripts/stage_validation_artifacts.init.gradle :shaderx:stageValidationArtifacts -PshaderxVersion=0.2.0-local-validation -PshaderxValidationRepository=/absolute/staging/path --no-configuration-cache --no-parallel
./gradlew :validation:kmp-consumer:jvmTest :validation:kmp-consumer:compileKotlinWasmJs :validation:kmp-consumer:compileKotlinIosSimulatorArm64 :validation:kmp-consumer:compileAndroidMain :validation:android-consumer:assembleDebug :validation:android-consumer:assembleRelease -PshaderxConsumerVersion=0.2.0-local-validation -PshaderxConsumerRepository=file:///absolute/staging/path --no-parallel
```

Inspect the staged AAR with `scripts/check_android_artifact.py` and resolve consumer dependencies
to prove the staged coordinate was selected. This fresh artifact compilation still does not
establish already-compiled 0.1.3 binary linking; that migration gate belongs to SX15.

### Actual Android Compose window validation

`validation/android-app` retains the classic View `AdoptionActivity` startup/R8 probe and adds
an independent `ComposeValidationActivity` plus `ComposeWindowValidationTest`. The UI fixture
uses the catalog sample compile SDK for AndroidX Activity; `:shaderx` and the external compile
consumer retain compile SDK 35 and the AAR check still enforces minCompileSdk 35/Java 17/minSdk 31.
The Activity's `onPause`/`onResume` explicitly feeds `rememberShaderPlayback(active=...)`;
this is a host responsibility, not automatic library lifecycle discovery.

```shell
./gradlew :validation:android-app:assembleDebug :validation:android-app:assembleDebugAndroidTest :validation:android-app:assembleRelease --no-parallel
```

On a task-owned explicit transport, install the debug/test APKs and instrument the app. Tests
use window `PixelCopy` to check controlled paused/live revisions, provider replacement, structural
color/source changes, known and actual layout resize, failure retention/recovery, disposal,
background foreground/fallback pixels and actual task background/resume. Provider/session
counters distinguish a settled paused state from continuing work. API 31 checks unsupported
shader requests preserve content and show the complete background fallback. Capture PNGs live
under the test app's external-files `sx12-compose` directory; pull them before uninstall/cleanup.
The tiny rotation invalidation mechanism remains in place. `AndroidSnapshotMetricsTest` is opt-in
with instrumentation argument `shaderxMetrics=true`: it samples bounded native color/background/
chain creation and numerical input uploads, elapsed CPU-side time, native heap and file descriptors.
It draws no display frames, has no timing/memory threshold, and cannot establish native resource
stabilization or GPU time. Do not infer physical GPU throughput
or native-memory stabilization from small software-emulator correctness tests.

Keep private ADB server/transport logs local and never reset shared ADB/pairing to run validation.
Ordinary final device runs exclude the entire experimental `HardwareImageExportTest` class;
its unsafe cleanup variants are not a supported exporter or a routine CI gate. Exact final
commands and separated counts are in the [integration validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx12-final-validation.json).


### Missing authored content regression

`ContentDeclarationTest` verifies the conservative declaration boundary and uses a backend that
throws on any access to prove whole-graph rejection precedes native/support work.
`ContentEffectPreflightTest` runs actual Skia routes on JVM/Wasm/iOS: static/session creation,
transactional session/pipeline replacement, earlier-provider suppression, legacy/bounded/suspend
export and successful explicit source-free backgrounds. Comments/whitespace declarations remain
valid; fake declarations in comments/local scopes and ambiguous macro/array/comma forms fail.
These are authored-adapter checks; valid manual legacy declarations retain native handling.
No Android missing-child native reproducer is needed or authorized by these tests. The AOSP API36
missing-child formatted-error route was identified by source review, not measured as a new crash.


### Frozen JVM migration check

SX15's `scripts/check_jvm_binary_compatibility.py` compiles the fixtures under
`validation/binary-compatibility/old/` against the actual Central 0.1.3 JVM jar. Freeze records
SHA-256 for the artifact, sources and every class. Run verifies those same bytes with only the
current jar plus resolved dependencies, checks CodeSource/duplicate class resources, and audits
all old public/protected classfile descriptors (including explicit internal/synthetic classifications).
The current runner also invokes the new optional session operation on old direct and base factories.
See the [frozen JVM compatibility record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx15-release-and-migration.json).
This is JVM linkage evidence; fresh Android/Wasm/iOS consumer compilation cannot establish DEX
or Klib binary compatibility. Do not recompile frozen bytecode against development to make it pass.

For the latest-stable baseline, use `--baseline-version 0.1.4` in **every** freeze,
Compose-freeze and run command, with a separate work directory. That selects the published
session/texture/tile-mode fixture automatically. The default remains `0.1.3` so the historical
fixture stays reproducible. Resolve runtime and Kotlin compiler classpath files as described
in [the binary fixture instructions](https://github.com/Debanshu777/ShaderX/blob/main/validation/binary-compatibility/README.md).
For example, after obtaining the actual published jar and building the release jar:

```shell
for check_mode in freeze freeze-compose run; do
    python scripts/check_jvm_binary_compatibility.py "$check_mode" \
        --baseline-version 0.1.4 \
        --old-jar /absolute/artifacts/shaderx-jvm-0.1.4.jar \
        --current-jar shaderx/build/libs/shaderx-jvm-0.2.0.jar \
        --dependencies /absolute/runtime-classpath.txt \
        --compiler-classpath /absolute/compiler-classpath.txt \
        --compose-compiler-plugin /absolute/kotlin-compose-compiler-plugin.jar \
        --java-home /absolute/jdk-21 \
        --work /absolute/frozen-consumer-0.1.4
done
```

The [0.2.0 release audit](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/release-0.2.0-jvm-compatibility.json)
records both baselines, artifact/frozen-class hashes, compiler/runtime inputs and commands.
The fresh 0.1.4 audit reviewed 108 public/protected classes and 963 members with zero unresolved
supported consumer descriptors, and ran the frozen Kotlin, Java, factory, Compose and published
session/texture/tile-mode calls against only the 0.2.0 library jar. The unchanged historical
0.1.3 fixture also passed again. Compose composition calls do not establish UI pixels, and
neither result is Android/Klib binary or physical-GPU evidence.

The release branch retains main's `WaveCompatibilityTest` and modifier callback/default-factory
regressions in `ComposeShaderLifecycleTest`, alongside the frozen binary consumers. Preserve
these source/behavior checks when resolving release merges; the binary audit is a separate gate.
