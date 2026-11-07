---
title: Run the ShaderLab, ASCII camera, and carousel samples
description: Build ShaderX examples for Android, desktop, iOS, and the browser, including ShaderLab controls, CameraX ASCII shaders, and a TMDB-powered vertical carousel.
---

# Sample applications

Clone the repository and complete [local setup](contributing.md#local-setup) before running the commands below. On Windows, use `gradlew.bat` instead of `./gradlew`.

## ShaderLab

The [ShaderLab gallery](https://github.com/Debanshu777/ShaderX/tree/main/samples/ShaderLab) targets Android, iOS, JVM desktop, and Wasm. Explore effect parameters and image previews; on `main`, the Audio tab demonstrates microphone capture and spectrum textures.

[Watch the browser recording](https://github.com/Debanshu777/ShaderX/blob/main/assets/shader_lab_web.gif) · [Watch the Android recording](https://github.com/Debanshu777/ShaderX/blob/main/assets/shader_lab.gif)

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

## ASCIICamera

The [ASCIICamera sample](https://github.com/Debanshu777/ShaderX/tree/main/samples/ASCIICamera) applies custom ASCII shaders to an embedded CameraX preview using Compose rendering. It also includes an image tab. Grant camera permission and use an Android API 33+ device or emulator.

```shell
./gradlew :samples:ASCIICamera:androidApp:installDebug
```

[Watch the camera recording](https://github.com/Debanshu777/ShaderX/blob/main/assets/ASCIICamera.gif). This demonstrates on-screen rendering, not offline `ImageProcessor` export. Performance depends on device, preview size, and effect; the recording is not a frame-rate guarantee.

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

[Watch the carousel recording](https://github.com/Debanshu777/ShaderX/blob/main/assets/vertical_carousal.gif).
