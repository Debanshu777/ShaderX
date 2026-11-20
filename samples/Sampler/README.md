# ShaderX Sampler

One app, four playable tabs. Open the short `BasicTab.kt`, `CustomTab.kt`, `AnimationTab.kt`
and `ChainTab.kt` files under
[`composeApp/src/commonMain/kotlin/com/debanshu/shaderx/sampler`](composeApp/src/commonMain/kotlin/com/debanshu/shaderx/sampler).
All use public ShaderX APIs; there is no ShaderLab UI, registry, picker or audio dependency.

- **Basic:** adjust grayscale and compare original/result.
- **Custom:** change tint strength/temperature, try Warm/Cool, expand the actual source.
- **Animation:** play/pause, strength, speed and seek; the clock survives tab switches.
- **Chain:** inspect actual original/first/final renders, swap order and tune both effects.

Values survive tab switches. Each Reset restores that tab's controls; animation also pauses,
returns to time zero and restores speed/strength. Its seek control follows the current ten-second
window, so playback beyond ten seconds remains scrubbable. Details and source are optional.

From the repository root using JDK 21 and the configured Android SDK:

```shell
./gradlew :samples:Sampler:composeApp:run
./gradlew :samples:Sampler:composeApp:wasmJsBrowserDevelopmentRun
./gradlew :samples:Sampler:androidApp:installDebug
./gradlew :samples:Sampler:composeApp:wasmJsBrowserDistribution
./gradlew :samples:Sampler:composeApp:jvmTest :samples:Sampler:composeApp:wasmJsBrowserTest
./gradlew :samples:Sampler:composeApp:linkDebugFrameworkIosSimulatorArm64
```

Uses the local `:shaderx` project from the same checkout. Runtime shaders require Android API 33+; desktop,
Wasm and iOS use Skia. Android/browser/desktop feed visibility into the app-owned playback
clock; the browser also forwards reduced motion. The `Sampler` iOS framework exposes
`MainViewController(active, reducedMotion)` for an embedding Xcode host, which supplies those
flags. No standalone Xcode project is included.

Each tab scopes factories/sessions to composition. Leaving Animation closes its native session;
the app-owned clock preserves phase but stops advancing until that tab is active again.
Custom and chain tests check actual native pixels on JVM and Chrome, including intermediate
stages, neutral controls and independent values. These are not physical-device GPU claims.

The documentation artifact packages `/sampler/` with one runtime download for all tabs.
Publishing still requires a merged-main documentation deployment. The README and sample guide
include a current four-tab walkthrough; capture steps and recording provenance are in the
[sample guide](../../docs/samples.md#current-readme-recordings).
