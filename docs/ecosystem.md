---
title: Use ShaderX with Coil images and Haze backdrops
description: Compose optional Coil image loading and Haze source-backed blur with ShaderX while preserving image state, playback, clipping, foreground content, and resource ownership.
---

# Use ShaderX with images and backdrops

Keep your image loader and backdrop library in charge of their own inputs. Apply ShaderX to the
Compose layer whose pixels you want to transform. The development recipes in
[`validation/ecosystem-consumer`](https://github.com/Debanshu777/ShaderX/tree/main/validation/ecosystem-consumer)
use only public APIs; there is no ShaderX adapter, image cache, or capture service to initialize.
Build the recipes against the library from the same checkout; see [release status](index.md#releases-and-development-docs) before substituting a published dependency.

## Use the demonstrated dependency versions

The example pins a single tested version of each optional dependency, not a supported range:

```kotlin
commonMain.dependencies {
    implementation(project(":shaderx")) // This repository's library
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
}
```

In a separate application, replace the project dependency with your staged development artifact;
see [consumer setup](contributing.md#consumer-and-abi-validation). Use a distinct local validation version so Gradle cannot substitute an older published artifact. Add Coil and Haze only to the consumer that needs
them. Neither dependency is added to the ShaderX library artifact or authoring API.

[Coil 3.4.0's tagged catalog](https://raw.githubusercontent.com/coil-kt/coil/3.4.0/gradle/libs.versions.toml)
uses Kotlin 2.3.10, Compose Multiplatform 1.9.3 and Skiko 0.9.22.2.
[Haze 1.7.2's tagged catalog](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/gradle/libs.versions.toml)
uses Kotlin 2.2.21 and Compose Multiplatform 1.10.0. The checkout retains Kotlin 2.3.21,
Compose Multiplatform 1.10.3 and Skiko 0.9.37.4. Resolved consumer graphs and actual target results
belong in the [ecosystem validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx14-ecosystem-integration.json).
An upstream version requirement below these values is a compatibility candidate; compilation
and rendered output remain separate checks.

These pins deliberately differ from the newest releases reviewed on 20 November 2026.
[Coil's changelog](https://coil-kt.github.io/coil/changelog/) records the later Kotlin/Compose/Skiko
upgrades. [Haze 2.1.0](https://chrisbanes.github.io/haze/2.1.0/changelog/) has a newer toolchain
and additional specialist blur/glass infrastructure. Its
[migration guide](https://raw.githubusercontent.com/chrisbanes/haze/2.1.0/docs/migrating-2.0.md)
uses `hazeBlur` and explicit `HazeInput` values with the `haze-blur` module. The `hazeEffect`
recipe below is specifically for **Haze 1.7.2**; it is not a Haze 2.x example or a claim of glass
feature parity.

## Keep asynchronous image state separate from playback

`ShaderImage` accepts an existing `ImageLoader`, an arbitrary Coil model and a caller-owned
`ShaderPlayback`. Keep that loader at the application or navigation-owner scope so all images
share its configured caches. Creating a loader per image duplicates resources. The recipe never
decodes the result again or sends it through `ImageProcessor`.

After copying the recipe source into your consumer, call it with owners retained above the
request. This host fragment uses the recipe's package, not an additional ShaderX SDK API:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import io.github.debanshu.shaderx.ecosystem.ShaderImage

@Composable
fun ImageRecipeScreen(
    model: Any?,
    imageLoader: ImageLoader,
    contentDescription: String?,
    active: Boolean,
    reducedMotion: Boolean,
) {
    val factory = rememberShaderFactory(maxCacheSize = 8)
    val playback = rememberShaderPlayback(active = active, reducedMotion = reducedMotion)
    ShaderImage(
        model = model,
        imageLoader = imageLoader,
        playback = playback,
        modifier = Modifier.size(240.dp),
        factory = factory,
        animateReveal = !reducedMotion,
        contentDescription = contentDescription,
    )
}
```

The image stays at the same composition position while
[`AsyncImage`](https://raw.githubusercontent.com/coil-kt/coil/3.4.0/coil-compose-core/src/commonMain/kotlin/coil3/compose/AsyncImage.kt)
runs its request. Its placeholder, success and error painters are drawn inside the same shader
layer. `onLoadState` reports loading, success and error (not Coil's Empty state) and can update status
text outside that layer. Supply a meaningful `contentDescription`, or null for a decorative image. Keep playback above the image
and outside `key(model)` or loading/success/error branches: a loader state change must not
construct a new clock. Parameter values can change while the controlled modifier retains phase.
Removing the image disposes its modifier session; removing the clock owner stops scheduling.

The demonstrated transition uses ordinary Compose animation to change effect parameters after
loading succeeds. A scroll offset can likewise map to a bounded effect parameter. Neither path
needs a shader-specific transition API. Pass actual lifecycle/visibility and reduced-motion policy
to `rememberShaderPlayback`; loading state alone is not lifecycle state. An error should remain
visible and retryable, and loading/error UI must not be mistaken for a successfully loaded image.
See the [controlled playback contract](compose.md#controlled-playback-in-development).

The recipe calls Coil's explicit-loader `AsyncImage` overload. It makes no network-engine
choice. Supply your application's existing configured loader; if you need HTTP, configure Coil's
platform-appropriate network integration at that same owner. The validation fixture decodes a real local PNG and verifies Coil memory-cache reuse, as well as
using controlled loader inputs so success, failure, cancellation and disposal can be checked without
assuming network access. It is a loader integration test, not an HTTP transport benchmark.

Clip outside the shader modifier when its spatial displacement must remain inside a shape.
A modifier on a parent filters that parent's children, including placeholders, badges and labels.
Place status text and controls in a sibling when they should stay unchanged. A zero-sized target
has no renderable pixels; assign the image an explicit size or constraints.

## Transform a captured backdrop and keep foreground clear

`HazeShaderCard` separates three jobs:

1. A source child contains ordinary Compose content and registers that drawn result with
   Haze's `hazeSource`.
2. A separate clipped overlay uses Haze's `hazeEffect(hazeState)` to draw blurred captured pixels
   inside a ShaderX grayscale layer.
3. Foreground content draws above that overlay outside source capture and the backdrop filter.

Haze 1.7.2's
[source modifier](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt)
and [effect modifier](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeChild.kt)
are the public integration seams. On the empty overlay, `clip(...).shaderEffect(...).hazeEffect(state)`
clips the filtered output and lets ShaderX transform Haze's blurred backdrop. The original visible
source remains in color. Reversing the shader and Haze modifiers changes the drawing boundary;
putting a ShaderX filter on the outer card also filters its foreground. Keep the source and effect
nodes distinct and the foreground out of the registered source to avoid self-capture.

Remember one `HazeState` for the participating source/effect pair. Let Haze own its recorded
layers and ShaderX own its sessions; do not extract Haze's native layer or transfer a UI-owned
factory into an export worker. Haze's
[tagged source lifecycle](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt)
releases its recorded layer and removes the area when the source detaches. A composition-scoped
`rememberShaderFactory` closes the ShaderX cache when its owner leaves composition. The caller
continues to own any loader or manually supplied factory.

This recipe uses Haze's source-backed blur, tint and clipping. ShaderX's `shaderBackground`
independently generates source-free pixels; it does not capture another composable or replace
Haze. Read [Haze 1.7.2 usage](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/docs/usage.md)
for its source selection and styling, and its
[platform guide](https://raw.githubusercontent.com/chrisbanes/haze/1.7.2/docs/platforms.md)
for blur fallbacks. ShaderX runtime filters still require Android API 33+, even when Haze can run
on older Android versions. Compile success does not verify the rendered source/foreground boundary
on that target; use the evidence record for the environments actually exercised.

## Put camera or video pixels in a compatible layer

A camera or player surface that appears behind Compose is not necessarily part of a Compose
recording. `SurfaceView` and external platform surfaces are excluded from this source-capture
recipe. A parent `shaderEffect` or `hazeSource` does not import their pixels into its layer.
The same restriction applies to platform video, protected content and native views whose pixels
are composed separately. This integration adds no camera/video capture engine or generic FPS
guarantee.

For Android CameraX, the existing
[`CameraPreview`](https://github.com/Debanshu777/ShaderX/blob/main/samples/ASCIICamera/composeApp/src/androidMain/kotlin/com/debanshu/asciicamera/camera/CameraPreview.kt)
uses `CameraXViewfinder` with `ImplementationMode.EMBEDDED` and CameraX 1.6.0.
The [official viewfinder API](https://developer.android.com/reference/kotlin/androidx/camera/viewfinder/core/ImplementationMode)
distinguishes its TextureView/embedded path from the SurfaceView/external path. Apply the filter
to this compatible preview layer and keep permission prompts and camera controls in siblings.
The sample binds the preview to the lifecycle and unbinds it in `finally`; changing a shader
must not rebind the camera. Test the actual device, crop, rotation and preview path before
claiming filtered live output. An ordinary Compose placeholder proves only the layer recipe,
not camera hardware or video playback performance.

## Run and verify the recipes

The recorded development validation covers these distinct scopes:

| Target | Recipe result |
| --- | --- |
| JVM / Skia CPU raster | Five passing integration tests: actual PNG decode/cache reuse, loading/success/error, retained playback, animated and scroll-driven pixels, request/session disposal, Haze spatial blur/source/foreground and source detach; demo rendered at 720 and 390 pixel widths |
| Android API 33+ recipe | Public consumer source compiles with compileSdk 35 / JVM 17; no rendered Android integration claim |
| iOS arm64 and simulator arm64 | Public consumer source compiles; no rendered Coil/Haze integration claim |
| Wasm | Public consumer source compiles; the separately rebuilt ShaderLab runs in the browser, which does not prove Coil/Haze browser rendering |

Run `./gradlew :validation:ecosystem-consumer:run` from the checkout root for the desktop demo.
It loads the repository flower banner through Coil and includes a missing-file toggle. Its
scroll state desaturates the image with a public parameter independently of the image's reveal
and retained playback. The fixture does not install an HTTP engine. Native desktop interaction
was unavailable during validation because the Mac was locked; responsive demo previews are
actual Skia scene renders, not screenshots of an unlocked native window.

Use the [ecosystem validation commands](contributing.md#ecosystem-integration-validation) for
compilation, actual loader states, rendering and disposal. The checked source in the separate
consumer is the complete example, including imports and explicit dependency pins. The
[ecosystem validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx14-ecosystem-integration.json) identifies
remaining target and hardware gaps; no broad dependency range or unmeasured performance claim
is inferred from a passing build.
