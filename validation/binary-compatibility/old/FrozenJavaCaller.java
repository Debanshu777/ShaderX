package shaderx.binary;

import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect;
import com.debanshu.shaderlab.shaderx.result.ShaderResult;
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform;

/** Compiled using javac against 0.1.3, never recompiled against development. */
public final class FrozenJavaCaller {
    public static void exercise() {
        WaveEffect wave = new WaveEffect(3f, 4f, false, 2f);
        if (wave.getAmplitude() != 3f || wave.getTime() != 2f) throw new AssertionError();
        FloatUniform uniform = new FloatUniform("size", 32f, 24f);
        if (uniform.getValues().length != 2) throw new AssertionError();
        ShaderResult.Success<Integer> result = new ShaderResult.Success<>(9);
        if (result.getOrNull() != 9) throw new AssertionError();
        if (!FrozenConsumer.exercise().endsWith("=PASS")) throw new AssertionError();
        System.out.println("frozen-java-caller=PASS");
    }
}
