---
title: Run the ShaderLab, ASCII camera, and carousel samples
description: Build ShaderX examples for Android, desktop, iOS, and the browser, including ShaderLab controls, CameraX ASCII shaders, and a TMDB-powered vertical carousel.
---

# Sample applications

Clone the repository and complete [local setup](contributing.md#local-setup) before running the commands below. On Windows, use `gradlew.bat` instead of `./gradlew`.

## ShaderLab

The [ShaderLab gallery](https://github.com/Debanshu777/ShaderX/tree/main/samples/ShaderLab) targets Android, iOS, JVM desktop, and Wasm. Explore effect parameters and image previews; the Audio tab demonstrates microphone capture and spectrum textures.

[Open the browser playground](playground/) to try the gallery without building it. The playground builds the local library from the same commit as its documentation deployment. It can include source changes after the latest Maven release; check the [release notes](https://github.com/Debanshu777/ShaderX/releases) when choosing an effect for your installed version. Use a current browser with WebAssembly GC support. Audio capture requires HTTPS (or localhost) and microphone permission; the app requests that permission when you start capture.

The app and Skia runtime download when you open the playground, so the first visit can take longer on a slow connection. The loading screen links back to the guides and recordings if the browser cannot start it. The recordings below remain available without running WebAssembly.

<video class="shaderx-demo" width="400" height="224" controls loop muted playsinline preload="none" poster="../assets/demos/shader_lab_web-poster.jpg" aria-label="ShaderLab running in a WebAssembly browser with live effect controls">
  <source src="assets/demos/shader_lab_web.mp4" type="video/mp4">
  <a href="assets/demos/shader_lab_web.mp4">Watch ShaderLab in the browser</a>.
</video>

<video class="shaderx-demo" width="400" height="898" controls loop muted playsinline preload="none" poster="../assets/demos/shader_lab-poster.jpg" aria-label="ShaderLab Android gallery demonstrating configurable shader effects">
  <source src="assets/demos/shader_lab.mp4" type="video/mp4">
  <a href="assets/demos/shader_lab.mp4">Watch the ShaderLab Android gallery</a>.
</video>

```shell
# Android device or emulator running API 33+
./gradlew :samples:ShaderLab:androidApp:installDebug

# Desktop
./gradlew :samples:ShaderLab:composeApp:run

# Browser development server
./gradlew :samples:ShaderLab:composeApp:wasmJsBrowserDevelopmentRun

# Production browser bundle
./gradlew :samples:ShaderLab:composeApp:wasmJsBrowserDistribution

# iOS arm64 simulator framework (macOS with Xcode)
./gradlew :samples:ShaderLab:composeApp:linkDebugFrameworkIosSimulatorArm64
```

For iOS, open `samples/ShaderLab/iosApp/iosApp.xcodeproj`, select a simulator, and run. Device builds require your own signing team. The web server prints its local URL; browser audio access needs localhost or HTTPS and permission.

`imagelib` and `audiolib` are sample modules for image access/export and audio capture/FFT. They are not published with the ShaderX library. The Composite tab demonstrates Android chaining; see [limitations](platforms.md#composition-limitations) for other platforms. Export on Android is subject to the current [image-processing limitation](image-processing.md).

The audio previews reuse the spectrum texture when the captured data and display bin count are unchanged. Android, desktop, and iOS publish read-only spectrum snapshots; the browser polls Web Audio each frame. Changing the bin-count slider still updates the texture without restarting microphone capture. `:samples:ShaderLab:audiolib:jvmTest` checks texture reuse, changed audio, and bin-count changes alongside the FFT and image-packing tests.

Native captures use one `SpectrumAnalyzer` per capture worker. It reuses FFT scratch arrays, Hann coefficients, bit-reversal indices, and twiddle factors while publishing a fresh result array for every audio buffer. Short reads use only the valid prefix without copying the PCM buffer. Tests compare its output exactly with the one-shot `Fft.magnitudes` implementation across window lengths and bin counts, and check that later frames never mutate published snapshots. The opt-in JVM diagnostic `SHADERX_AUDIO_BENCHMARK=1 ./gradlew :samples:ShaderLab:audiolib:jvmTest --tests '*SpectrumAllocationBenchmark' --rerun-tasks` compares warm one-shot and workspace allocations and CPU time on the current machine; it does not measure GPU frame rate or microphone latency.

Android and desktop open, read, stop, and close blocking microphone devices on `Dispatchers.IO`. Stopping a capture returns without joining a worker on the UI thread; retries wait for earlier devices to close, including across intervening denied or cancelled permission requests. Generation checks discard late permission or startup results, and failed reads terminate capture instead of spinning. Android capture generations share an in-flight permission prompt so cancellation cannot lose another waiter's result. Fake-device tests cover blocked shutdown, stop during startup, overlapping permission requests, retry, and cleanup without requesting microphone access. iOS keeps its native audio callback lifecycle and reuses PCM/FFT workspace storage inside the tap.

Picked-image previews decode once per selected byte array and requested size using `Dispatchers.Default`, with a loading state while decoding. The large preview is capped at 2048 pixels on its longest edge; gallery thumbnails request twice their displayed pixel size. Smaller images are never upscaled. Android decodes at the requested size; Skia scales into a bounded display bitmap without retaining a full-size decoded bitmap (decoder scratch memory can still depend on the format). Original encoded bytes remain available for full-resolution export. This moves decoding off the UI thread on Android, desktop, and iOS; browser dispatch remains on its event loop. Android and desktop image-picker reads use `Dispatchers.IO`. A newer selection or leaving the composition cancels pending delivery so a stale image cannot replace the current one. Both local-library samples scope their preview shader factories to the composition and close the cache on exit. Each blocking export owns a separate factory, closed in its worker after processing, so UI cache eviction or screen disposal cannot close native resources during export. Export disables repeated saves while it is running. If applying an effect fails, the sample shows the error and does not save an unfiltered image as a successful effect export.

## ASCIICamera

The [ASCIICamera sample](https://github.com/Debanshu777/ShaderX/tree/main/samples/ASCIICamera) applies custom ASCII shaders to an embedded CameraX preview using Compose rendering. It also includes an image tab. Grant camera permission and use an Android API 33+ device or emulator.

```shell
./gradlew :samples:ASCIICamera:androidApp:installDebug
```

<video class="shaderx-demo" width="400" height="898" controls loop muted playsinline preload="none" poster="../assets/demos/ASCIICamera-poster.jpg" aria-label="Live CameraX preview rendered as ASCII characters">
  <source src="assets/demos/ASCIICamera.mp4" type="video/mp4">
  <a href="assets/demos/ASCIICamera.mp4">Watch the ASCII camera preview</a>.
</video>

This demonstrates on-screen rendering. Performance depends on device, preview size, and effect.

Switching to the Image tab releases this sample's CameraX preview; changing cameras rebinds only its own use case. Image reads and decoding run off the main thread; the display bitmap is capped at 2048 pixels on its longest edge, and moving effect controls reuses it. To verify these paths on a device, pick a large image, move the cell-size slider, switch between Camera and Image, and flip cameras. The image should remain responsive and the camera should release when its tab disappears.

## VerticalCarousel

The [VerticalCarousel sample](https://github.com/Debanshu777/ShaderX/tree/main/samples/VerticalCarousel) binds effect parameters to a vertical movie pager.

```shell
./gradlew :samples:VerticalCarousel:installDebug
```

It currently consumes the published **0.1.3** artifact, while ShaderLab and ASCIICamera use the local `:shaderx` project. This distinction matters when testing library changes.

For live movie data, add your TMDB API read access token to the ignored root `local.properties`:

```properties
TMDB_ACCESS_TOKEN=your_read_access_token
```

Without a working token or connection, the sample falls back to sample movie data. Never commit the token. See [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application) for token setup.

<video class="shaderx-demo" width="400" height="898" controls loop muted playsinline preload="none" poster="../assets/demos/vertical_carousal-poster.jpg" aria-label="Movie cards scrolling through a vertical carousel with shader effects">
  <source src="assets/demos/vertical_carousal.mp4" type="video/mp4">
  <a href="assets/demos/vertical_carousal.mp4">Watch the vertical carousel</a>.
</video>
