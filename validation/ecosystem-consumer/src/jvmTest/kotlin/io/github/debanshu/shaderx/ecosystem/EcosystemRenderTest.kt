package io.github.debanshu.shaderx.ecosystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.test.*

/** Real Compose/Skia raster + Coil pipeline. No Android HWUI or physical GPU claim. */
class EcosystemRenderTest {
    @Test
    fun loadingSuccessErrorReloadAndRemovalPreservePlaybackAndCancelRequest() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val cancelled = java.util.concurrent.atomic.AtomicInteger()
        val events = CopyOnWriteArrayList<String>()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).components {
            add(Interceptor { chain ->
                when (chain.request.data) {
                    "pending" -> { entered.complete(Unit); try { gate.await() } finally { cancelled.incrementAndGet() } }
                    "success" -> Unit
                    else -> return@Interceptor ErrorResult(ColorImage(0xFFFF0000.toInt()), chain.request, IllegalStateException("fixture error"))
                }
                SuccessResult(ColorImage(0xFF00FF00.toInt()), chain.request, DataSource.MEMORY)
            })
        }.build()
        val factory = TrackingFactory(ShaderFactory.create())
        val playback = ShaderPlayback(2.5, false)
        val model = mutableStateOf("pending")
        val show = mutableStateOf(true)
        val scene = ImageComposeScene(64, 64) {
            if (show.value) Box(Modifier.size(64.dp)) {
                ShaderImage(model.value, loader, playback, Modifier.size(64.dp), factory, animateReveal = false,
                    onLoadState = { events += it::class.simpleName!! })
                Box(Modifier.size(8.dp).background(Color.Blue))
            }
        }
        try {
            settle(scene) { entered.isCompleted && "Loading" in events }
            near(255, red(pixel(scene, 32, 32)))
            near(165, green(pixel(scene, 32, 32)))
            model.value = "success"
            settle(scene) { "Success" in events }
            settle(scene) { green(pixel(scene, 32, 32)) > 250 }
            assertEquals(0xFF0000FF.toInt(), pixel(scene, 4, 4), "Sibling status is outside image shader")
            assertEquals(1, cancelled.get(), "Replacing model cancels pending Coil request")
            model.value = "error"
            settle(scene) { "Error" in events }
            settle(scene) { red(pixel(scene, 32, 32)) > 250 && green(pixel(scene, 32, 32)) < 4 }
            assertEquals(2.5, playback.positionSeconds, "Load state must not recreate/seek the hoisted clock")
            playback.seekTo(7.0)
            model.value = "pending"
            settle(scene) { events.count { it == "Loading" } >= 2 }
            show.value = false
            frames(scene)
            assertEquals(7.0, playback.positionSeconds)
            settle(scene) { cancelled.get() == 2 }
            assertFalse(scene.hasInvalidations(), "Removed image and paused playback leave no frame awaiter")
            assertTrue(factory.createdSessions > 0)
            assertEquals(0,factory.activeSessions,"Removing the image closes every native session before factory shutdown")
        } finally { scene.close(); loader.shutdown(); factory.close() }
        assertEquals(0, factory.cacheSize, "Caller closes its owned factory after the composition")
    }

    @Test
    fun realFileDecodeUsesOneLoaderAndItsMemoryCache() = runBlocking {
        val file = Files.createTempFile("shaderx-coil-", ".png")
        Surface.makeRasterN32Premul(16,16).use { surface ->
            surface.canvas.clear(0xFF00FF00.toInt())
            surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { Files.write(file, it.bytes) } }
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val factory = ShaderFactory.create()
        val playback = ShaderPlayback(0.0, false)
        val show = mutableStateOf(true)
        val sources = CopyOnWriteArrayList<DataSource>()
        val scrollPixels = mutableIntStateOf(0)
        val scene = ImageComposeScene(64,64) {
            if(show.value) ShaderImage(file.toUri().toString(),loader,playback,
                Modifier.size(64.dp).shaderEffect(GrayscaleEffect((scrollPixels.intValue/180f).coerceIn(0f,1f)),factory),factory,false,
                onLoadState={ if(it is AsyncImagePainter.State.Success) sources += it.result.dataSource })
        }
        try {
            settle(scene) { sources.isNotEmpty() }
            settle(scene) { green(pixel(scene,32,32)) > 250 }
            show.value=false;frames(scene)
            show.value=true
            settle(scene) { sources.size >= 2 }
            save(scene,"coil-decoded-success.png")
            assertEquals(DataSource.DISK,sources.first())
            assertEquals(DataSource.MEMORY_CACHE,sources.last(), "Remount uses caller's same Coil memory cache")
            scrollPixels.intValue=180
            settle(scene) { abs(green(pixel(scene,32,32))-150)<=4 }
            near(150,red(pixel(scene,32,32)));near(150,blue(pixel(scene,32,32)))
            assertEquals(2,sources.size,"Scroll parameter updates do not reload the image")
        } finally { scene.close();loader.shutdown();factory.close();Files.deleteIfExists(file) }
    }

    @Test
    fun capturedBackdropIsFilteredWhileSourceAndForegroundKeepTheirColors() {
        val state = HazeState()
        val factory = ShaderFactory.create()
        val show = mutableStateOf(true)
        val scene = ImageComposeScene(96,96) {
            if(show.value) HazeShaderCard(Modifier.size(96.dp),factory,state,
                source = { Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                } },
                foreground = { Box(Modifier.size(12.dp).background(Color.Green)) })
        }
        try {
            frames(scene)
            val outside = pixel(scene,4,48)
            val backdrop = pixel(scene,48,48)
            val foreground = pixel(scene,16,16)
            assertEquals(0xFFFF0000.toInt(),outside,"Uncaptured visible source remains red")
            assertTrue(red(backdrop) in 33..72, "Blur mixes source colors at their boundary: ${red(backdrop)}")
            near(red(backdrop),green(backdrop));near(red(backdrop),blue(backdrop))
            val blurredLeft = pixel(scene,44,48)
            assertTrue(red(blurredLeft) in 35..72, "Nonzero spatial blur extends across the color edge")
            save(scene,"haze-source-foreground.png")
            assertEquals(0xFF00FF00.toInt(),foreground,"Separate foreground is neither captured nor grayscaled")
            assertEquals(1,state.areas.size)
            show.value=false;frames(scene)
            assertTrue(state.areas.isEmpty(),"Haze removes its captured area on detach")
            assertFalse(scene.hasInvalidations())
        } finally { scene.close();factory.close() }
    }

    @Test
    fun composeRevealAnimatesPixelsWithoutRestartingImageRequestOrPlayback() = runBlocking {
        val file = Files.createTempFile("shaderx-reveal-", ".png")
        Surface.makeRasterN32Premul(64,64).use { surface ->
            val paint = org.jetbrains.skia.Paint()
            try {
                repeat(16) { x ->
                    paint.color = if(x % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
                    surface.canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH(x*4f,0f,4f,64f),paint)
                }
                surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { Files.write(file,it.bytes) } }
            } finally { paint.close() }
        }
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        val factory = ShaderFactory.create()
        val playback = ShaderPlayback(.75,false)
        val successes = java.util.concurrent.atomic.AtomicInteger()
        val scene = ImageComposeScene(64,64) {
            ShaderImage(file.toUri().toString(),loader,playback,Modifier.size(64.dp),factory,
                onLoadState={ if(it is AsyncImagePainter.State.Success) successes.incrementAndGet() })
        }
        fun colors(time:Long):IntArray {
            Snapshot.sendApplyNotifications()
            repeat(4) { scene.render(time).close() }
            return scene.render(time).use { image -> Bitmap.makeFromImage(image).use { b ->
                IntArray(32*32) { b.getColor(16+it%32,16+it/32) }
            } }
        }
        try {
            settle(scene) { successes.get() == 1 }
            val start = colors(1_000_000_000L)
            val middle = colors(1_120_000_000L)
            val end = colors(1_400_000_000L)
            assertTrue(start.indices.count { start[it] != middle[it] } > 100,"Reveal changes rendered displacement mid-transition")
            assertTrue(end.indices.count { end[it] != middle[it] } > 100,"Transition reaches distinct final output")
            assertEquals(1,successes.get(),"Parameter animation does not reload the image")
            assertEquals(.75,playback.positionSeconds,"Reveal never seeks the retained phase")
            assertFalse(scene.hasInvalidations(),"Reveal completes while paused playback settles")
        } finally { scene.close();loader.shutdown();factory.close();Files.deleteIfExists(file) }
    }

    @Test
    fun demoRendersAtDesktopAndPhoneWidths() = runBlocking {
        val file = java.nio.file.Path.of("../../docs/assets/shaderx-banner.png").toAbsolutePath()
        assertTrue(Files.exists(file))
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            for(width in listOf(720,390)) {
                val scene = ImageComposeScene(width,844) { EcosystemDemo(loader,file.toUri().toString()) }
                try {
                    // The actual decoder runs on Coil's worker; render while it completes.
                    repeat(30) { frames(scene);delay(10) }
                    repeat(4) { scene.render(1_500_000_000L).close() }
                    save(scene,"demo-${width}.png",1_500_000_000L)
                    assertFalse(scene.hasInvalidations(),"Paused demo settles after its reveal at width $width")
                } finally { scene.close() }
            }
        } finally { loader.shutdown() }
    }

    private fun save(scene:ImageComposeScene,name:String,time:Long=1_000_000_000L) {
        val dir = java.nio.file.Path.of("build/reports/ecosystem-render")
        Files.createDirectories(dir)
        scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { Files.write(dir.resolve(name),it.bytes) } }
    }

    private class TrackingFactory(private val delegate:ShaderFactory):ShaderFactory by delegate {
        var activeSessions=0
        var createdSessions=0
        override fun createAnimatedSession(effect:AnimatedShaderEffect,width:Float,height:Float) =
            delegate.createAnimatedSession(effect,width,height).map { session ->
                activeSessions++;createdSessions++
                object:ShaderSession by session {
                    private var closed=false
                    override fun close() {
                        if(!closed) { closed=true;activeSessions--;session.close() }
                    }
                }
            }
    }

    private suspend fun settle(scene:ImageComposeScene, predicate:()->Boolean) {
        withTimeout(10_000) { while(true) { frames(scene);if(predicate())break;delay(10) } }
        frames(scene)
    }
    private fun frames(scene:ImageComposeScene) {
        Snapshot.sendApplyNotifications()
        repeat(4) { scene.render(1_000_000_000L).close() }
    }
    private fun pixel(scene:ImageComposeScene,x:Int,y:Int):Int = scene.render(1_000_000_000L).use { image ->
        Bitmap.makeFromImage(image).use { it.getColor(x,y) }
    }
    private fun red(c:Int)=org.jetbrains.skia.Color.getR(c)
    private fun green(c:Int)=org.jetbrains.skia.Color.getG(c)
    private fun blue(c:Int)=org.jetbrains.skia.Color.getB(c)
    private fun near(expected:Int,actual:Int)=assertTrue(abs(expected-actual)<=4,"Expected $expected, got $actual")
}
