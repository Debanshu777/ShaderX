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

## 0.1.4 — 2026-11-08

Published on Maven Central with 15 built-in effects. Library changes:

- `ShaderSession` and automatic animated-uniform refresh in `Modifier.shaderEffect`.
- `AnimatedGradientEffect`, `PlasmaEffect`, `RippleEffect`, and `VortexEffect`.
- `TextureUniform`, `AudioVisualizerEffect`, and `AudioDistortionEffect`.
- Live texture sources and uniform refresh while time animation is paused.
- Deterministic Skia cache/session cleanup, corrected animated Compose invalidation, and shared immutable metadata for animation frame copies.
- Preserved 0.1.3 modifier callback argument order and `WaveEffect` data-class/concrete-return APIs; custom factories can report unsupported animation through a default session method. Regression tests cover these source and behavior contracts.
- Unsupported Android offline export fails before decoding, shader creation, or destination allocation; on-screen Compose rendering remains supported.

Sample and documentation updates in the repository:

- ShaderLab microphone/FFT examples in the sample-only `audiolib` module.
- Bounded sample-image previews, reusable FFT workspaces, and asynchronous Android/desktop microphone lifecycle handling.
- Focused guides, a searchable documentation build, and contributor templates.
- A production browser playground bundled with the documentation, plus lighter on-demand sample recordings.

[Release notes](https://github.com/Debanshu777/ShaderX/releases/tag/v0.1.4) · [Changes from 0.1.3](https://github.com/Debanshu777/ShaderX/compare/0.1.3...v0.1.4)

## 0.1.3 — 2026-05-02

Published on Maven Central. Includes nine built-in effects and targets Android, iOS arm64, JVM, and Wasm.

[Release notes](https://github.com/Debanshu777/ShaderX/releases/tag/0.1.3) · [Changes from 0.1.2](https://github.com/Debanshu777/ShaderX/compare/0.1.2...0.1.3)

## 0.1.2 — 2026-03-08

[Release notes](https://github.com/Debanshu777/ShaderX/releases/tag/0.1.2)
