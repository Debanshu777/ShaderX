package shaderx.binary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.compose.shaderEffectWithResult
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult

// Fresh source must preserve positional/trailing callbacks, as well as the old JVM symbols.
@Composable
fun currentSourceFixture(effect: ShaderEffect?, factory: ShaderFactory): List<Modifier> {
    val size = IntSize(32, 24)
    val error: (ShaderError) -> Unit = {}
    val result: (ShaderResult<RenderEffect>) -> Unit = {}
    return listOf(
        Modifier.shaderEffect(effect),
        Modifier.shaderEffect(effect, factory, size, onError = error),
        Modifier.shaderEffect(effect, factory, size, error),
        Modifier.shaderEffect(effect, factory, size) {},
        Modifier.shaderEffectWithResult(effect),
        Modifier.shaderEffectWithResult(effect, factory, size, onResult = result),
        Modifier.shaderEffectWithResult(effect, factory, size, result),
        Modifier.shaderEffectWithResult(effect, factory, size) {},
        Modifier.shaderEffect(effect, factory, size, frameDataKey = 1, onError = error),
        Modifier.shaderEffectWithResult(effect, factory, size, frameDataKey = 1, onResult = result),
    )
}
