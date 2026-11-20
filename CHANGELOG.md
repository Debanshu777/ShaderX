# Changelog

Published artifacts: [Maven Central](https://central.sonatype.com/artifact/io.github.debanshu777/shaderx). Tagged release notes: [GitHub Releases](https://github.com/Debanshu777/ShaderX/releases).

## 0.2.0

### Added

- Custom `ShaderProgram`, `ShaderDefinition` and `ShaderBinding` authoring: reusable source,
  constant/editable/image bindings and immutable effect updates without handwritten dispatch,
  reflection, KSP or a required editor.
- Strict `ParameterSchema`, typed keys and immutable `ParameterValues`, with explicit reject/clamp
  policies, presentation metadata and adapters around existing parameter APIs.
- Controlled playback with pause, seek, speed, shared clocks and explicit live-input revisions.
  Hosts supply visibility, lifecycle and reduced-motion policy.
- Source-free procedural backgrounds with independent ownership and explicit fallback; numerical
  RGBA8 inputs and bounded source-free child snapshots remain distinct from color-managed images.
- `EffectCollection` color adjustment, color matrix, ordered dither and halftone content filters.
- Ordered Skia on-screen chains and optional transactional shared-clock pipeline sessions.
- Operation-specific capability reports and observable static fail/preserve/substitute fallback.
- Bounded Skia PNG export and serialized suspend export, with admission limits, cancellation
  delivery rules and deterministic resource ownership.
- Optional Coil image and Haze backdrop recipes in separate consumers, without core dependencies.

### Changed

- Android native blur and blur-only chains work from API 31; AGSL effects, sessions, inputs and
  procedural backgrounds require API 33. The library AAR targets Java 17 and minCompileSdk 35;
  contributors continue to use JDK 21.
- ShaderLab separates Gallery, Source and Pipeline, keeps advanced tools in disclosures, retains
  editor drafts across navigation and invalidates stale generated Kotlin after edits.
- The source workbench supports explicit Run, typed forms/bindings, atomic last-good previews,
  bounded saved drafts, source-free backgrounds and caller-rebound Kotlin templates.
- Typed presets, stable-key pipeline editing and complete Kotlin export preserve exact values and
  identify application-owned image/live inputs. Microphone/FFT capture stays in sample modules.

### Fixed

- Deterministic Skia cache/session cleanup and animated Compose invalidation; shared immutable
  metadata reduces repeated animation-frame setup.
- Authored content definitions reject missing or ambiguous reserved `content` declarations before
  providers, native creation or export decoding. Manual legacy declarations retain their handling.
- Restore published Wave and Compose modifier JVM entry points; old factory implementations can
  decline the additive animated-session operation without new abstract-method dispatch failures.
- Bounded sample-image previews, reusable FFT workspaces and asynchronous microphone lifecycle handling.

### Examples and documentation

- One playable Sampler with Basic, Custom, Animation and Chain tabs, alongside the focused ShaderLab.
- Browser playground and Sampler distributions packaged with searchable documentation.
- Nineteen documented applied UI recipes with a visual README showcase, including static stipple
  and animated layered noise; the flower banner and all four original sample recording slots remain.
- Guides explain the release API; a central release-status notice owns publication availability. Version history stays here, with upgrade actions in the migration guide.

### Validation and release notes

- Expanded public/generated consumer, raster, browser, simulator, Android HWUI/window/lifecycle,
  AAR metadata and release/R8 validation, plus unsigned local publication staging. The
  [0.2.0 validation record](validation/evidence/release-0.2.0-validation.json) separates fresh
  release checks from prior device evidence and remaining limits.
- Frozen JVM compatibility checks pass against published 0.1.4 (108 classes, 963 members) and
  the retained 0.1.3 baseline, with zero unresolved supported descriptors. See the
  [release evidence](validation/evidence/release-0.2.0-jvm-compatibility.json). These checks do not
  prove Android/Klib binary compatibility.
- Android image export and offline composite export remain unsupported. General shader brushes,
  native Skia raw-image sampling, physical-device coverage and long-running GPU/memory budgets
  are not established by this release preparation.
- Stable release line without a prerelease suffix. See the
  [release plan](docs/release-plan.md) and [migration guide](docs/migration.md).

## 0.1.4 — publication verified 2026-11-20

Official [Central metadata](https://repo.maven.apache.org/maven2/io/github/debanshu777/shaderx/maven-metadata.xml)
and [source artifact](https://repo.maven.apache.org/maven2/io/github/debanshu777/shaderx/0.1.4/shaderx-0.1.4-sources.jar)
confirm this historical release. The verification date is not its publication date.

- Includes `ShaderSession` and automatic animated-uniform refresh in ordinary `Modifier.shaderEffect`.
- Includes `AnimatedGradientEffect`, `PlasmaEffect`, `RippleEffect`, `VortexEffect`, `TextureUniform`, `AudioVisualizerEffect`, and `AudioDistortionEffect`, with live texture refresh.
- Android artifact requires minSdk 33, minCompileSdk 37 and Java 21 classfiles. Skia multi-effect chains remain unsupported; offline composites are rejected and Android image export returns a processing failure.
- Does **not** include the checkout's `ShaderDefinition`, strict schema API, `EffectCollection`, controlled playback, capability/fallback, background, raw/nested-input, pipeline or bounded-export additions.

## 0.1.3 — 2026-05-02

Historical published baseline retained by the frozen JVM compatibility checks. Includes nine built-in effects and targets Android, iOS arm64, JVM, and Wasm.

[Release notes](https://github.com/Debanshu777/ShaderX/releases/tag/0.1.3) · [Changes from 0.1.2](https://github.com/Debanshu777/ShaderX/compare/0.1.2...0.1.3)

## 0.1.2 — 2026-03-08

[Release notes](https://github.com/Debanshu777/ShaderX/releases/tag/0.1.2)
