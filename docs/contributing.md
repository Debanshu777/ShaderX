---
title: Contributor setup, project structure, and tests
description: Set up ShaderX with JDK 21 and the Android SDK, run targeted Gradle checks, understand Kotlin source sets, add effects, and preview the documentation locally.
---

# Contributor guide

Start with a focused issue or pull request. Documentation fixes, reproducible bug reports, effect implementations, and platform testing all help. See the repository's [contribution guidelines](https://github.com/Debanshu777/ShaderX/blob/main/CONTRIBUTING.md).

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

Release numbering and branch guards have Python regression tests alongside the documentation-policy tests; run `python -m unittest discover -s scripts/tests -v`. The [release workflow](maintaining.md#release-workflow) chooses versions from tags and passes `-PshaderxVersion=...` to Gradle. Ordinary builds use the version declared in `shaderx/build.gradle.kts`, and library tests do not need publishing secrets.

## Project structure

| Path | Responsibility |
| --- | --- |
| `shaderx/src/commonMain` | Public effects, parameters, uniforms, results, and Compose modifiers |
| `shaderx/src/androidMain` | Android AGSL factory and image-processing implementation |
| `shaderx/src/skiaMain` | Shared Skia implementation for iOS, JVM, and Wasm |
| `shaderx/src/iosMain`, `jvmMain`, `wasmJsMain` | Platform factory creation |
| `shaderx/src/commonTest` | Effect, parameter, uniform, and result contracts |
| `shaderx/src/jvmTest` | Skia factory, texture, and session integration tests |
| `samples/ShaderLab` | Gallery plus sample-only image/audio modules |
| `samples/ASCIICamera`, `samples/VerticalCarousel` | Focused Android examples |
| `docs` | Handwritten guides and documentation site assets |

## Add an effect

1. Add the implementation in `shaderx/src/commonMain/kotlin/com/debanshu/shaderlab/shaderx/effect/impl` with a unique ID and KDoc.
2. Inside the library, use `AbstractRuntimeShaderEffect` or `AbstractAnimatedShaderEffect` and the internal typed parameter handlers. External app effects use the [public interface](custom-effects.md) instead.
3. Keep effect instances immutable. For animated effects, exclude time and live textures from structural equality; include configuration parameters.
4. Declare `uniform shader content;` and a `half4 main(float2 fragCoord)` entry point. Match all Kotlin uniforms to the shader declarations.
5. Register built-in effects in `ShaderX.builtInEffects()`. For a native effect, implement it in both Android and Skia factory branches.
6. Add appropriate parameter/contract tests and a shader-compilation check. Validate the appearance on relevant sample targets.
7. Update the README effect list, [effect catalog](effects.md), and changelog. Mark unreleased changes separately and state availability accurately.

The library enables Kotlin explicit API mode. New public declarations need explicit visibility and types; keep implementation details internal. Put dependencies in `gradle/libs.versions.toml`.

## Try the library in another project

On macOS with the required target toolchains:

```shell
./gradlew :shaderx:publishToMavenLocal
```

In the consuming project's dependency repositories, temporarily add `mavenLocal()` before `mavenCentral()`, then use the local version declared in `shaderx/build.gradle.kts`. Do not add `mavenLocal()` to production setup instructions. For work in this repository, ShaderLab and ASCIICamera already depend on the local project.

## API compatibility checks

Before changing an existing public API, compare it with the published artifact as well as the current source. Preserve positional and default arguments, generated data-class methods, and concrete return types that consumer code can use.

`WaveCompatibilityTest` covers the published `WaveEffect` copy and destructuring API, concrete return types, configuration-only equality, and shared immutable metadata. `ComposeShaderLifecycleTest` invokes both modifier callbacks through positional, named, trailing-lambda, and explicit `frameDataKey` calls. It also uses a custom factory implementing only the 0.1.3 methods and verifies that the default animated-session result reports `UnsupportedEffect`.

```shell
./gradlew :shaderx:jvmTest --tests '*WaveCompatibilityTest' --tests '*ComposeShaderLifecycleTest'
```

These are focused source and behavior regression checks. They do not establish binary compatibility for every Kotlin target or consumer toolchain; verify affected published signatures and consumer builds before a release.

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

## Preview and check documentation

Guide previews use Python 3.11+. The complete checked site also includes the production Wasm playground, so install JDK 21 and the Android SDK from [local setup](#local-setup); Apple tooling is not required for that bundle:

```shell
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
.venv-docs/bin/python scripts/check_docs.py
.venv-docs/bin/python -m unittest discover -s scripts/tests -v
./gradlew :samples:ShaderLab:composeApp:wasmJsBrowserDistribution --no-daemon
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
