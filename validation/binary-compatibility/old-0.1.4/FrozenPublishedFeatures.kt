package shaderx.binary

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AnimatedGradientEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.ShaderTileMode
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform

// Compile ONLY against the actual Central 0.1.4 jar. Execute unchanged against the release jar.
object FrozenPublishedFeatures {
    @JvmStatic fun exercise() {
        val image = ImageBitmap(2, 2)
        val texture = TextureUniform("sample", image)
        check(texture.component2() === image)
        check(texture.tileModeX == ShaderTileMode.CLAMP)
        val tiled = texture.copy(tileModeX = ShaderTileMode.REPEAT, tileModeY = ShaderTileMode.MIRROR)
        check(tiled.component3() == ShaderTileMode.REPEAT)
        check(tiled.component4() == ShaderTileMode.MIRROR)
        val effect = AnimatedGradientEffect(speed = 0.5f)
        check(effect.buildUniforms(32f, 24f).isNotEmpty())
        ShaderFactory.create().use { factory ->
            factory.createAnimatedSession(effect, 32f, 24f).getOrThrow().use { session ->
                session.currentRenderEffect
                check(session.refresh(effect.withTime(1f), 32f, 24f) is ShaderResult.Success)
            }
        }
        println("frozen-published-session-texture-and-tile-mode-calls=PASS")
    }
}
