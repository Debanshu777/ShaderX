package io.github.debanshu.shaderx.validation

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.compose.*
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.result.*
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform

/** External consumer screen for window-pixel/lifetime validation; AdoptionActivity remains the R8 probe. */
class ComposeValidationActivity : ComponentActivity() {
    var lifecycleActive by mutableStateOf(false)
        private set
    val tracking = CountingFactory()
    lateinit var playback: ShaderPlayback
    var revision by mutableLongStateOf(0L)
    var shown by mutableStateOf(true)
    var boxSize by mutableIntStateOf(96)
    var knownSize by mutableStateOf<IntSize?>(null)
    var backgroundBroken by mutableStateOf(false)
    var errors = 0
    var backgroundReady = 0
    var providerCalls = 0
    var live = .25f
    var providerFails = false
    var controlledBounds = Rect()
    var backgroundBounds = Rect()
    var foregroundBounds = Rect()
    val tint = ColorParameterDefinition("window-probe", "tint", 0xFF004000L)
    private val provider = ShaderFrameProvider {
        providerCalls++
        check(!providerFails) { "Deliberate live-provider failure" }
        listOf(FloatUniform("live", live))
    }
    private fun definition(extra: String = "", redOffset: Float = 0f) = ShaderDefinition("window-probe",
        program = ShaderProgram("""
            uniform shader content;
            uniform float live;
            uniform float time;
            uniform float2 resolution;
            layout(color) uniform half4 tint;
            half4 main(float2 p) {
                return half4(live + $redOffset, tint.g, time + p.x / resolution.x * 0.2, 1.0) * content.eval(p).a;
            }
            // $extra
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live"), ShaderBinding.time(),
            ShaderBinding.resolution(), ShaderBinding.color("tint", tint.key))),
        schema = ParameterSchema("window-probe", listOf(tint)))
    var effect by mutableStateOf(definition().animatedEffect(frameProvider = provider))
    fun replaceProvider() {
        effect = definition().animatedEffect(frameProvider = ShaderFrameProvider {
            providerCalls++
            listOf(FloatUniform("live", .125f))
        })
    }
    fun replaceSource() { effect = definition("replacement", .125f).animatedEffect(frameProvider = provider) }
    fun replaceWithBrokenSource() {
        effect = ShaderDefinition("broken-window-probe", program = ShaderProgram("uniform shader content; invalid shader")).animatedEffect()
    }
    fun replaceColor() { effect = effect.withValue(tint.key, 0xFF00C000L) }
    private val background = ShaderDefinition("window-background", program = ShaderProgram(
        "half4 main(float2 p) { return half4(1,0,0,1); }")).proceduralBackground()
    private val brokenBackground = ShaderDefinition("broken-background", program = ShaderProgram("invalid background")).proceduralBackground()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            playback = rememberShaderPlayback(initialPositionSeconds = .125, initiallyPlaying = false, active = lifecycleActive)
            Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp)) {
                BasicText("ShaderX Compose window validation API ${android.os.Build.VERSION.SDK_INT}",
                    style = androidx.compose.ui.text.TextStyle(color = Color.White))
                if (shown) {
                    Box(Modifier.size(boxSize.dp).onGloballyPositioned { controlledBounds = it.boundsInWindow().toRect() }
                        .controlledShaderEffect(effect, playback, revision, tracking, knownSize,
                            onError = { errors++; println("SX12 controlled error=$it") }).background(Color.White))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(96.dp).onGloballyPositioned { backgroundBounds = it.boundsInWindow().toRect() }
                    .shaderBackground(if (backgroundBroken) brokenBackground else background,
                        factory = tracking.delegate, fallback = SolidColor(Color.Blue),
                        onError = { errors++ }, onReady = { backgroundReady++ })) {
                    Box(Modifier.size(20.dp).onGloballyPositioned { foregroundBounds = it.boundsInWindow().toRect() }
                        .background(Color.Green))
                }
            }
        }
    }

    override fun onResume() { super.onResume(); lifecycleActive = true; println("SX12 Activity.onResume") }
    override fun onPause() { lifecycleActive = false; super.onPause(); println("SX12 Activity.onPause") }

    override fun onDestroy() { super.onDestroy(); tracking.close(); println("SX12 Activity.onDestroy") }

    private fun androidx.compose.ui.geometry.Rect.toRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    /** Counts public factory/session boundaries without changing backend behavior. */
    class CountingFactory : ShaderFactory {
        val delegate = ShaderFactory.create()
        var created = 0
        var closed = 0
        var refreshes = 0
        var factoryCloses = 0
        override val cacheSize get() = delegate.cacheSize
        override fun isSupported() = delegate.isSupported()
        override fun clearCache() = delegate.clearCache()
        override fun close() { factoryCloses++; delegate.close() }
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float) = delegate.createRenderEffect(effect, width, height)
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
            delegate.createAnimatedSession(effect, width, height).map { session ->
                created++
                object : ShaderSession {
                    private var disposed = false
                    override val currentRenderEffect get() = session.currentRenderEffect
                    override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
                        refreshes++
                        return session.refresh(effect, width, height)
                    }
                    override fun close() { check(!disposed); disposed = true; closed++; session.close() }
                }
            }
    }
}
