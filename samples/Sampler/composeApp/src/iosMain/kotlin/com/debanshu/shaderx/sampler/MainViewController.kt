package com.debanshu.shaderx.sampler

import androidx.compose.ui.window.ComposeUIViewController

// The embedding host supplies its visibility and accessibility preference.
fun MainViewController(active: Boolean = true, reducedMotion: Boolean = false) = ComposeUIViewController {
    SamplerApp(active = active, reducedMotion = reducedMotion)
}
