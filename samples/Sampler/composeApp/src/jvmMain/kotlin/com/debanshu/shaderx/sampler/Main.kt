package com.debanshu.shaderx.sampler

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "ShaderX Sampler", state = rememberWindowState(width = 760.dp, height = 900.dp)) {
        SamplerApp(active = LocalWindowInfo.current.isWindowFocused)
    }
}
