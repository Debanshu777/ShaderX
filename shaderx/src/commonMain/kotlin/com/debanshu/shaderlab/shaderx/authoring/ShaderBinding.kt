package com.debanshu.shaderlab.shaderx.authoring

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.parameter.ParameterKey
import com.debanshu.shaderlab.shaderx.parameter.ParameterValues
import com.debanshu.shaderlab.shaderx.uniform.*

/** Read-only declaration kind for tooling; it never resolves providers or native resources. */
public enum class ShaderBindingKind {
    CONSTANT, RESOLUTION, TIME, FLOAT_PARAMETER, COLOR_PARAMETER, BOOLEAN_PARAMETER, IMAGE, FRAME_FLOAT, FRAME_IMAGE, DATA, NESTED, FRAME_DATA, FRAME_NESTED,
}

/**
 * Read-only introspection of the same declaration used by rendering. Parameter IDs refer
 * to the owning schema. [constant] returns a defensive scalar/vector/matrix snapshot; image
 * handles and provider callbacks are deliberately excluded and require application rebinding.
 * Structural image sampling modes are retained as metadata; live-image modes belong to the
 * provider. This describes binding intent, not proof that source compiles on any backend.
 */
public class ShaderBindingDescriptor internal constructor(
    public val name: String,
    public val kind: ShaderBindingKind,
    public val parameterId: String? = null,
    private val uniform: Uniform? = null,
    /** Borrowed image sampling modes; null for bindings without a structural image. */
    public val tileModeX: ShaderTileMode? = null,
    public val tileModeY: ShaderTileMode? = null,
) {
    public val constant: Uniform? get() = uniform?.snapshot()
    internal var samplingMetadata: DataSampling? = null
    internal var rawRequired: Boolean = false
    /** Structural raw sampling; null for caller-defined live sampling. */
    public val dataSampling: DataSampling? get() = samplingMetadata
    /** Rebinding requirement only; no application payload, callback or native handle. */
    public val inputDescription: String? get() = when (kind) {
        ShaderBindingKind.DATA -> "Caller-owned immutable RGBA8 numerical data"
        ShaderBindingKind.NESTED -> "Caller-supplied source-free ShaderNode snapshot with explicit child time"
        ShaderBindingKind.FRAME_DATA -> "Live provider must supply DataUniform; revision owned by caller"
        ShaderBindingKind.FRAME_NESTED -> "Live provider must supply NestedUniform with explicit child time"
        ShaderBindingKind.IMAGE, ShaderBindingKind.FRAME_IMAGE -> "Caller-rebound color-managed image"
        else -> null
    }
}

/** Declarative uniform assignment. Names are source aliases; `content` belongs to the backend. */
public sealed class ShaderBinding private constructor(
    public val name: String,
    internal val key: ParameterKey<*>? = null,
    internal val frame: Boolean = false,
    internal val clock: Boolean = false,
    internal val imageFrame: Boolean = false,
) {
    /** Declarative tooling snapshot; reading it never runs a frame provider or touches a backend. */
    public val descriptor: ShaderBindingDescriptor get() = when (this) { is Value -> declaration }

    internal abstract fun resolve(values: ParameterValues, width: Float, height: Float, time: Float): Uniform

    private class Value(
        name: String, key: ParameterKey<*>? = null, frame: Boolean = false, clock: Boolean = false, imageFrame: Boolean = false,
        kind: ShaderBindingKind, constant: Uniform? = null,
        tileModeX: ShaderTileMode? = null, tileModeY: ShaderTileMode? = null,
        private val build: (ParameterValues, Float, Float, Float) -> Uniform,
    ) : ShaderBinding(name, key, frame, clock, imageFrame) {
        val declaration = ShaderBindingDescriptor(name, kind, key?.id, constant, tileModeX, tileModeY)
        override fun resolve(values: ParameterValues, width: Float, height: Float, time: Float): Uniform =
            build(values, width, height, time)
    }

    public companion object {
        /**
         * Structural escape path for existing vector, integer, matrix, color and image uniforms.
         * Mutable numeric arrays are copied at declaration and on every read. Native setters
         * validate source names/types; this does not infer declarations from source.
         */
        public fun uniform(uniform: Uniform): ShaderBinding {
            val snapshot = uniform.snapshot()
            return Value(
                snapshot.name,
                kind = when(snapshot) {
                    is TextureUniform -> ShaderBindingKind.IMAGE
                    is DataUniform -> ShaderBindingKind.DATA
                    is NestedUniform -> ShaderBindingKind.NESTED
                    else -> ShaderBindingKind.CONSTANT
                },
                constant = if (snapshot is TextureUniform || snapshot is DataUniform || snapshot is NestedUniform) null else snapshot,
                tileModeX = when(snapshot) { is TextureUniform -> snapshot.tileModeX; is DataUniform -> snapshot.tileModeX; else -> null },
                tileModeY = when(snapshot) { is TextureUniform -> snapshot.tileModeY; is DataUniform -> snapshot.tileModeY; else -> null },
            ) { _, _, _, _ -> snapshot.snapshot() }.also { it.declaration.samplingMetadata = (snapshot as? DataUniform)?.sampling; it.declaration.rawRequired = snapshot is DataUniform || (snapshot is NestedUniform && snapshot.input.requiresRawData) }
        }

        /** Render target dimensions in physical pixels, bound as float2. */
        public fun resolution(name: String = "resolution"): ShaderBinding =
            Value(name, kind = ShaderBindingKind.RESOLUTION) { _, w, h, _ -> FloatUniform(name, w, h) }

        /** Session time in seconds. Only animated instances may use this binding. */
        public fun time(name: String = "time"): ShaderBinding =
            Value(name, clock = true, kind = ShaderBindingKind.TIME) { _, _, _, t -> FloatUniform(name, t) }

        /** Constant finite scalar; no editable schema or presentation metadata required. */
        public fun float(name: String, value: Float): ShaderBinding {
            require(value.isFinite()) { "Binding '$name' requires a finite float" }
            return Value(name, kind = ShaderBindingKind.CONSTANT, constant = FloatUniform(name, value)) { _, _, _, _ -> FloatUniform(name, value) }
        }

        /** Typed editable float with its definition's validation and declaration ownership. */
        public fun float(name: String, key: ParameterKey<Float>): ShaderBinding =
            Value(name, key, kind = ShaderBindingKind.FLOAT_PARAMETER) { values, _, _, _ -> FloatUniform(name, values[key]) }

        /** Packed sRGB ARGB; declare layout(color) and premultiply output in source. Structural. */
        public fun color(name: String, argb: Long): ShaderBinding {
            require(argb in 0L..0xFFFFFFFFL) { "Binding '$name' requires unsigned 32-bit sRGB ARGB" }
            return Value(name, kind = ShaderBindingKind.CONSTANT, constant = ColorUniform(name, argb)) { _, _, _, _ -> ColorUniform(name, argb) }
        }

        /** Editable color, always structural: changing it replaces native session configuration. */
        public fun color(name: String, key: ParameterKey<Long>): ShaderBinding =
            Value(name, key, kind = ShaderBindingKind.COLOR_PARAMETER) { values, _, _, _ -> ColorUniform(name, values[key]) }

        /** Boolean encoded as an int uniform (0 or 1); source must declare int. */
        public fun boolean(name: String, key: ParameterKey<Boolean>): ShaderBinding =
            Value(name, key, kind = ShaderBindingKind.BOOLEAN_PARAMETER) { values, _, _, _ -> IntUniform(name, if (values[key]) 1 else 0) }

        /**
         * Existing color-managed input, sampled in image pixels. The caller owns the bitmap and
         * must keep it valid and avoid concurrent mutation while binding/rendering uses it.
         */
        public fun image(
            name: String, image: ImageBitmap,
            tileModeX: ShaderTileMode = ShaderTileMode.CLAMP,
            tileModeY: ShaderTileMode = ShaderTileMode.CLAMP,
        ): ShaderBinding = Value(name, kind = ShaderBindingKind.IMAGE, tileModeX = tileModeX, tileModeY = tileModeY) { _, _, _, _ -> TextureUniform(name, image, tileModeX, tileModeY) }

        /** Immutable raw numerical input; capability support must be checked per backend. */
        public fun data(name: String, data: Rgba8Data, sampling: DataSampling = DataSampling.NEAREST,
            tileModeX: ShaderTileMode = ShaderTileMode.CLAMP, tileModeY: ShaderTileMode = ShaderTileMode.CLAMP): ShaderBinding =
            uniform(DataUniform(name, data, sampling, tileModeX, tileModeY))

        /** Explicit source-free child snapshot; no inherited parent clock. */
        public fun nested(name: String, input: ShaderNode): ShaderBinding = uniform(NestedUniform(name, input))

        /** Provider must supply exactly one immutable DataUniform under this name. */
        public fun frameData(name: String): ShaderBinding = Value(name, frame = true, kind = ShaderBindingKind.FRAME_DATA) { _, _, _, _ -> error("Frame data requires provider") }

        /** Provider must supply exactly one immutable NestedUniform under this name. */
        public fun frameNested(name: String): ShaderBinding = Value(name, frame = true, kind = ShaderBindingKind.FRAME_NESTED) { _, _, _, _ -> error("Frame nested requires provider") }

        /** Scalar supplied by a frame provider. Every provider call must supply it exactly once. */
        public fun frameFloat(name: String): ShaderBinding =
            Value(name, frame = true, kind = ShaderBindingKind.FRAME_FLOAT) { _, _, _, _ -> FloatUniform(name, 0f) }

        /** Image supplied by a frame provider. Every provider call must supply it exactly once. */
        public fun frameImage(name: String): ShaderBinding =
            Value(name, frame = true, imageFrame = true, kind = ShaderBindingKind.FRAME_IMAGE) { _, _, _, _ -> error("Frame image '$name' requires its provider") }
    }
}

/** Live data source separate from immutable values. Retain its identity; it owns no native handles. */
public fun interface ShaderFrameProvider {
    /**
     * Supply exactly the declared frameFloat/frameImage/frameData/frameNested bindings. Colors are never frame inputs.
     * Images are borrowed; retain them and avoid concurrent content mutation during rendering.
     */
    public fun uniforms(timeSeconds: Float): List<Uniform>
}

internal class ShaderBindingException(effectId: String, bindingName: String, stage: String, cause: Exception) :
    IllegalArgumentException("Effect '$effectId', binding '$bindingName', $stage: ${cause.message}", cause)

private fun Uniform.snapshot(): Uniform = when (this) {
    is FloatUniform -> {
        require(values.isNotEmpty() && values.all { it.isFinite() }) { "Binding '$name' requires finite float values" }
        FloatUniform(name, values.copyOf())
    }
    is IntUniform -> {
        require(values.size in 1..4) { "Binding '$name' requires 1..4 integer components; integer arrays are not supported" }
        IntUniform(name, values.copyOf())
    }
    is MatrixUniform -> {
        require(values.all { it.isFinite() }) { "Binding '$name' requires finite matrix values" }
        MatrixUniform(name, values.copyOf())
    }
    is ColorUniform -> {
        require(toFloatArray().all { it.isFinite() && it in 0f..1f }) { "Binding '$name' requires sRGB components in 0..1" }
        this
    }
    is TextureUniform, is DataUniform, is NestedUniform -> this
}
