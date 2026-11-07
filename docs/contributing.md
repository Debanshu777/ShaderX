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

# A focused test class
./gradlew :shaderx:jvmTest --tests 'com.debanshu.shaderlab.shaderx.FactorySmokeTest'

# Full library build, including checks (macOS for all Apple targets)
./gradlew :shaderx:build

# Generate the API reference
./gradlew :shaderx:generateDocs
```

JVM test results are in `shaderx/build/reports/tests/jvmTest/index.html`. Dokka output is in `shaderx/build/dokka/html/index.html`. Use the [sample commands](samples.md) to test visual output on the affected platforms; unit tests cannot establish how an effect looks on every GPU.

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
7. Update the README effect list, [effect catalog](effects.md), and unreleased changelog. State availability accurately.

The library enables Kotlin explicit API mode. New public declarations need explicit visibility and types; keep implementation details internal. Put dependencies in `gradle/libs.versions.toml`.

## Try the library in another project

On macOS with the required target toolchains:

```shell
./gradlew :shaderx:publishToMavenLocal
```

In the consuming project's dependency repositories, temporarily add `mavenLocal()` before `mavenCentral()`, then use the development version declared in `shaderx/build.gradle.kts`. Do not add `mavenLocal()` to production setup instructions. For work in this repository, ShaderLab and ASCIICamera already depend on the local project.

## Preview and check documentation

Documentation builds use Python 3.11+ and do not require an Android or Apple toolchain:

```shell
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
.venv-docs/bin/python scripts/check_docs.py
.venv-docs/bin/python -m unittest discover -s scripts/tests -v
.venv-docs/bin/zensical build --strict
.venv-docs/bin/python scripts/check_site.py
.venv-docs/bin/zensical serve
```

On Windows, use `.venv-docs\Scripts\python` and `.venv-docs\Scripts\zensical`. Open the URL printed by Zensical. Guides should also remain readable as ordinary Markdown on GitHub. Use descriptive page titles, accurate examples, relative links between guides, and a unique `description` in each page's front matter.

The [maintainer guide](maintaining.md) covers documentation publishing, repository discovery settings, and release updates.

Read [AGENTS.md](https://github.com/Debanshu777/ShaderX/blob/main/AGENTS.md) for the change-to-guide map used by coding agents. Every codebase change requires updates to **both `README.md` and the affected Markdown guides in `docs/` in the same PR**, including tests, refactors, configuration, scripts, and workflows. The Documentation PR check fails if either update is missing; there is no PR-body opt-out. Prose-only and media-only changes do not require companion edits. See the [maintainer guide](maintaining.md#keep-documentation-current-with-coding-agents) for the policy and examples of useful internal-change documentation.

The site publishes automatically only after a PR merges into `main` and its documentation build passes. Direct pushes, preview builds, and unmerged PR closures do not deploy. The documentation URL is [debanshu777.github.io/ShaderX](https://debanshu777.github.io/ShaderX/).
