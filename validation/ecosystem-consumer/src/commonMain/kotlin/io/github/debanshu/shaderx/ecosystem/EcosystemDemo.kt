package io.github.debanshu.shaderx.ecosystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback

/** Runnable consumer UI; the app owns [loader], this subtree owns its ShaderX factory/clock. */
@Composable
fun EcosystemDemo(loader: ImageLoader, imageModel: Any?) {
    val factory = rememberShaderFactory()
    val playback = rememberShaderPlayback(initiallyPlaying = false)
    val scroll = rememberScrollState()
    var fail by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Waiting") }
    // Ordinary Compose scroll state drives an existing public effect parameter.
    val desaturation = (scroll.value / 180f).coerceIn(0f,1f)
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("ShaderX · optional ecosystem recipes", style = TextStyle(fontSize = 22.sp))
        BasicText("${if(fail) "Load photograph" else "Simulate missing file"} · $status",
            Modifier.clickable { fail = !fail }.padding(8.dp), style = TextStyle(color = Color.Blue))
        ShaderImage(if(fail) "file:///shaderx-missing-image.png" else imageModel, loader, playback,
            Modifier.fillMaxWidth().height(220.dp).shaderEffect(GrayscaleEffect(desaturation),factory), factory, contentDescription = "ShaderX flower banner",
            onLoadState = { status = it::class.simpleName ?: "Image state" })
        BasicText("The label stays outside the image shader. Scroll to desaturate the photograph.")
        HazeShaderCard(Modifier.fillMaxWidth().height(260.dp), factory,
            source = {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Red,Color.Blue))))
            },
            foreground = {
                BasicText("Foreground stays green", Modifier.padding(16.dp),
                    style = TextStyle(color = Color.Green,fontSize = 20.sp))
            })
        BasicText("Haze captures the red/blue source. ShaderX grayscales the inner blurred surface only.")
        Spacer(Modifier.height(140.dp))
    }
}
