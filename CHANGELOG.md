# Changelog

Published artifacts: [Maven Central](https://central.sonatype.com/artifact/io.github.debanshu777/shaderx). Tagged release notes: [GitHub Releases](https://github.com/Debanshu777/ShaderX/releases).

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
