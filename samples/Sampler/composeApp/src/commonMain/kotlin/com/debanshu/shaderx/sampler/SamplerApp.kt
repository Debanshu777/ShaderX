package com.debanshu.shaderx.sampler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback

/** Four short examples share only a shell, presentation helpers and a retained playback clock. */
@Composable
fun SamplerApp(active: Boolean = true, reducedMotion: Boolean = false) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Basic", "Custom", "Animation", "Chain")
    val savedTabs = rememberSaveableStateHolder()
    // The clock survives tab switches. Hidden animation pauses without background catch-up.
    val playback = rememberShaderPlayback(initiallyPlaying = false,
        active = active && selected == 2, reducedMotion = reducedMotion)
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF315C4D))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F8F3)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("ShaderX Sampler", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("PLAY WITH PIXELS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                TabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { index, label ->
                        Tab(selected = selected == index, onClick = { selected = index }, text = { Text(label) })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    savedTabs.SaveableStateProvider(selected) {
                        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (selected) {
                                0 -> BasicTab()
                                1 -> CustomTab()
                                2 -> AnimationTab(playback)
                                else -> ChainTab()
                            }
                        }
                    }
                }
            }
        }
    }
}
