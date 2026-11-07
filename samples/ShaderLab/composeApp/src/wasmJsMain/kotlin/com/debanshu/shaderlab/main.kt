package com.debanshu.shaderlab

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val app = document.getElementById("shaderlab-app") ?: return
    ComposeViewport(app) {
        ShaderLabApp()
        SideEffect {
            document.getElementById("shaderlab-loading")?.setAttribute("hidden", "")
        }
    }
}
