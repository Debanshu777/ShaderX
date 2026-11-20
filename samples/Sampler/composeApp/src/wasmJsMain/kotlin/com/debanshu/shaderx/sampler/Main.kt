package com.debanshu.shaderx.sampler

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val app = document.getElementById("sample-app") ?: return
    ComposeViewport(app) {
        var visible by remember { mutableStateOf(pageVisible()) }
        val motion = remember { window.matchMedia("(prefers-reduced-motion: reduce)") }
        var reducedMotion by remember { mutableStateOf(motion.matches) }
        DisposableEffect(Unit) {
            val onVisibility: (Event) -> Unit = { visible = pageVisible() }
            val onMotion: (Event) -> Unit = { reducedMotion = motion.matches }
            document.addEventListener("visibilitychange", onVisibility)
            motion.addEventListener("change", onMotion)
            onDispose {
                document.removeEventListener("visibilitychange", onVisibility)
                motion.removeEventListener("change", onMotion)
            }
        }
        SamplerApp(active = visible, reducedMotion = reducedMotion)
        SideEffect { document.getElementById("sample-loading")?.setAttribute("hidden", "") }
    }
}

// kotlinx-browser does not declare Document.hidden for this Wasm target.
@JsFun("() => !document.hidden")
private external fun pageVisible(): Boolean
