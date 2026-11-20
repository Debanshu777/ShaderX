package shaderx.binary

import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.factory.BaseShaderFactory
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import java.nio.file.Path
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.coroutines.EmptyCoroutineContext

fun main(args: Array<String>) {
    val expectedJar = Path.of(args.first()).toRealPath()
    val baseline = args.getOrElse(1) { "0.1.3" }
    val origin = ShaderFactory::class.java.protectionDomain.codeSource.location.toURI()
    check(Path.of(origin).toRealPath() == expectedJar) { "Wrong ShaderFactory CodeSource: $origin" }
    for (type in listOf(ShaderFactory::class.java, BaseShaderFactory::class.java,
                        GradientEffect::class.java, WaveEffect::class.java,
                        ShaderResult::class.java, Uniform::class.java)) {
        val location = type.protectionDomain.codeSource.location.toURI()
        check(Path.of(location).toRealPath() == expectedJar) { "Wrong CodeSource for $type: $location" }
        println("${type.simpleName}-CodeSource=$location")
    }
    val resources = ShaderFactory::class.java.classLoader.getResources(
        "com/debanshu/shaderlab/shaderx/factory/ShaderFactory.class").toList()
    check(resources.size == 1) { "Multiple library resources: $resources" }
    println("ShaderFactory-CodeSource=$origin")
    println("ShaderFactory-resources=$resources")
    for (factory in listOf<ShaderFactory>(FrozenDirectFactory(), FrozenBaseFactory())) {
        val outcome = factory.createAnimatedSession(WaveEffect(), 32f, 24f)
        check(outcome is ShaderResult.Failure && outcome.error is ShaderError.UnsupportedEffect) {
            "Expected graceful unsupported default from ${factory.javaClass.name}: $outcome"
        }
        println("${factory.javaClass.simpleName}-new-session-default=PASS")
        factory.close()
    }
    FrozenJavaCaller.exercise()
    if (baseline == "0.1.4") {
        Class.forName("shaderx.binary.FrozenPublishedFeatures").getMethod("exercise").invoke(null)
    }
    val recomposer = Recomposer(EmptyCoroutineContext)
    val composition = Composition(object : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun onClear() {}
    }, recomposer)
    var composed = false
    try {
        composition.setContent {
            frozenComposeConsumer()
            check(currentSourceFixture(null, FrozenDirectFactory()).size == 10)
            composed = true
        }
        check(composed)
        println("frozen-compose-old-modifier-descriptors=PASS (composition only; no UI pixels)")
    } finally {
        composition.dispose()
        recomposer.cancel()
    }
    println("JVM-BINARY-COMPATIBILITY=PASS")
}
