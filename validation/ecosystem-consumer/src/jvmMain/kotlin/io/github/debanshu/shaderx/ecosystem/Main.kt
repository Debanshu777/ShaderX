package io.github.debanshu.shaderx.ecosystem

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import java.io.File

fun main(args: Array<String>) = application {
    val loader = remember { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    Window(onCloseRequest = ::exitApplication, title = "ShaderX ecosystem",
        state = rememberWindowState(width = 720.dp,height = 840.dp)) {
        EcosystemDemo(loader, File(args.firstOrNull() ?: "docs/assets/shaderx-banner.png").absoluteFile.toURI().toString())
    }
}
