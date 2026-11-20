package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError

/** Operations have independent support; content rendering is a static snapshot. */
public enum class ShaderOperation {
    CONTENT_RENDER,
    ANIMATED_SESSION,
    /** Legacy [ImageProcessor.process]; bounded PNG export uses [ImageProcessor.queryImageExportSupport]. */
    IMAGE_EXPORT,
    BACKGROUND_RENDER,
}

/** Additional input semantics declared by a caller, never inferred by invoking uniform providers. */
public enum class ShaderInput {
    /** Color-managed image textures, using the existing TextureUniform binding. */
    IMAGE_TEXTURE,
    /** RGBA8 UNORM data; Android setInputBuffer only. */
    RAW_DATA_TEXTURE,
    /** Bounded, explicit source-free ShaderNode snapshots. */
    NESTED_SHADER,
}

/** SUPPORTED means a route exists, not that source compiles, values are valid, or allocation succeeds. */
public enum class ShaderSupportStatus { SUPPORTED, UNSUPPORTED, UNKNOWN }

/** Stable diagnostic identifiers. Display [ShaderSupport.message], not the enum name, to users. */
public enum class ShaderSupportReason {
    CAPABILITIES_NOT_PROVIDED,
    ANDROID_API_33_REQUIRED,
    ANDROID_EXPORT_UNAVAILABLE,
    COMPOSITE_EXPORT_UNAVAILABLE,
    CHAINING_UNAVAILABLE,
    COMPOSITE_ANIMATION_UNAVAILABLE,
    ANIMATED_EFFECT_REQUIRED,
    NATIVE_EFFECT_UNAVAILABLE,
    BACKGROUND_UNAVAILABLE,
    RAW_DATA_UNAVAILABLE,
    NESTED_SHADER_UNAVAILABLE,
    EMPTY_COMPOSITE,
    ANDROID_API_31_REQUIRED,
}

/**
 * Metadata-only support report. [minimumRequirement] describes a known alternative when one exists;
 * null means no supported minimum is established. UNKNOWN must not be treated as unsupported.
 */
public data class ShaderSupport(
    public val status: ShaderSupportStatus,
    public val reason: ShaderSupportReason? = null,
    public val message: String,
    public val minimumRequirement: String? = null,
)

/**
 * Optional contract for factories that know their operation capabilities. Existing implementations
 * of [ShaderFactory] need no changes. Implementations must not compile, allocate graphics resources,
 * read shader source, or invoke buildUniforms/buildFrameUniforms or live data providers here.
 *
 * [effect] null asks about the operation in general. Otherwise inspect only known effect structure.
 * [inputs] declares additional requirements: omitted custom requirements have not been validated.
 * Implementations should be cheap and deterministic for the current backend/device.
 */
public interface ShaderCapabilityProvider {
    public fun querySupport(
        operation: ShaderOperation,
        effect: ShaderEffect?,
        inputs: Set<ShaderInput>,
    ): ShaderSupport
}

/**
 * Queries optional metadata. A third-party factory without [ShaderCapabilityProvider] reports UNKNOWN,
 * regardless of its legacy isSupported Boolean. No rendering or provider calls are made by this adapter.
 * Query the same factory that will perform the operation; wrapping a built-in factory loses its known
 * capabilities unless the wrapper deliberately implements and forwards the provider contract.
 */
public fun ShaderFactory.querySupport(
    operation: ShaderOperation,
    effect: ShaderEffect? = null,
    inputs: Set<ShaderInput> = emptySet(),
): ShaderSupport = (this as? ShaderCapabilityProvider)?.querySupport(operation, effect, inputs)
    ?: ShaderSupport(
        ShaderSupportStatus.UNKNOWN,
        ShaderSupportReason.CAPABILITIES_NOT_PROVIDED,
        "This factory does not provide operation capabilities; attempt the operation and handle its result.",
    )

/** Shared by built-in queries and rendering/export preflight. null API denotes the Skia backend. */
internal fun builtInSupport(
    androidApi: Int?,
    operation: ShaderOperation,
    effect: ShaderEffect?,
    inputs: Set<ShaderInput> = emptySet(),
): ShaderSupport {
    fun unsupported(reason: ShaderSupportReason, message: String, minimum: String? = null): ShaderSupport =
        ShaderSupport(ShaderSupportStatus.UNSUPPORTED, reason, message, minimum)

    // Export on Android must not even inspect the effect or device's rendering availability.
    if (androidApi != null && operation == ShaderOperation.IMAGE_EXPORT) {
        return unsupported(ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE,
            "Software bitmap export is unsupported on Android. Use on-screen effects or export on a supported Skia platform.",
            "Skia image export with ShaderFactory.create()")
    }
    if (androidApi != null && androidApi < 31) {
        return unsupported(ShaderSupportReason.ANDROID_API_31_REQUIRED,
            "ShaderX requires Android API 31+; runtime shaders require API 33+.", "Android API 31")
    }
    if (androidApi != null && androidApi < 33 &&
        (operation != ShaderOperation.CONTENT_RENDER || effect == null || effect is RuntimeShaderEffect || inputs.isNotEmpty())) {
        return unsupported(ShaderSupportReason.ANDROID_API_33_REQUIRED,
            "Runtime shaders, shader inputs, backgrounds and animated sessions require Android API 33+.", "Android API 33")
    }
    val known = when (effect) {
        is com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect -> effect.definition
        is com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect -> effect.definition
        else -> null
    }
    val requirements = inputs + known?.inputRequirements().orEmpty()
    if (androidApi == null && ShaderInput.RAW_DATA_TEXTURE in requirements) {
        return unsupported(ShaderSupportReason.RAW_DATA_UNAVAILABLE, "Raw numerical data requires Android setInputBuffer; resolved Skiko 0.9.37.4 exposes no raw image shader API.")
    }
    if (operation == ShaderOperation.BACKGROUND_RENDER && effect != null) {
        return unsupported(ShaderSupportReason.BACKGROUND_UNAVAILABLE,
            "Content effects are not source-free backgrounds. Use ShaderDefinition.proceduralBackground() and shaderBackground.")
    }
    if (operation == ShaderOperation.IMAGE_EXPORT && effect is CompositeEffect) {
        return unsupported(ShaderSupportReason.COMPOSITE_EXPORT_UNAVAILABLE,
            "Composite effects, including single-child composites, are not supported for offline image processing.")
    }
    if (operation == ShaderOperation.ANIMATED_SESSION && effect != null) {
        if (effect !is AnimatedShaderEffect && effect !is CompositeEffect) {
            return unsupported(ShaderSupportReason.ANIMATED_EFFECT_REQUIRED, "An animated session requires an AnimatedShaderEffect.")
        }
    }
    when (effect) {
        is CompositeEffect -> {
            if (effect.effects.isEmpty()) return unsupported(ShaderSupportReason.EMPTY_COMPOSITE, "Empty composite effect")
            for (child in effect.effects) {
                val childSupport = builtInSupport(androidApi,
                    if (operation == ShaderOperation.ANIMATED_SESSION) ShaderOperation.CONTENT_RENDER else operation, child, inputs)
                if (childSupport.status != ShaderSupportStatus.SUPPORTED) return childSupport
            }
        }
        is NativeEffect -> if (effect !is BlurEffect) {
            return unsupported(ShaderSupportReason.NATIVE_EFFECT_UNAVAILABLE, "This native effect has no built-in backend implementation.")
        }
        is RuntimeShaderEffect, null -> Unit
    }
    return ShaderSupport(ShaderSupportStatus.SUPPORTED,
        message = if (operation == ShaderOperation.CONTENT_RENDER && effect is CompositeEffect) {
            "Static content rendering is supported; composite children receive no automatic animation clock. Source and allocation remain unchecked."
        } else {
            "Operation is supported for known/supplied requirements. Source compilation, values and resource allocation remain unchecked."
        }, minimumRequirement = if (androidApi != null) {
            if (operation == ShaderOperation.CONTENT_RENDER && effect is BlurEffect) "Android API 31"
            else if (androidApi < 33) "Android API 31" else "Android API 33"
        } else null)
}

/** Reuse existing sealed error variants; adding capability reporting introduces no ShaderError subtype. */
internal fun ShaderSupport.asError(operation: ShaderOperation, effect: ShaderEffect?): ShaderError = when {
    reason == ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE -> ShaderError.ProcessingError(message)
    reason == ShaderSupportReason.ANDROID_API_33_REQUIRED || reason == ShaderSupportReason.ANDROID_API_31_REQUIRED -> ShaderError.PlatformNotSupported(message)
    else -> ShaderError.UnsupportedEffect(message, effect?.id ?: operation.name)
}

/**
 * Metadata-only source-free background query. The declaration already excludes reserved content;
 * explicit image bindings use IMAGE_TEXTURE. Source compilation, binding coverage and values remain
 * unchecked. Built-in factories support this route; third-party factories report UNKNOWN unless
 * they opt into capabilities (a capability claim alone does not add a renderer).
 */
public fun ShaderFactory.queryBackgroundSupport(
    background: com.debanshu.shaderlab.shaderx.authoring.ShaderBackground,
    inputs: Set<ShaderInput> = emptySet(),
): ShaderSupport = querySupport(ShaderOperation.BACKGROUND_RENDER, inputs = inputs + background.definition.inputRequirements())

internal fun com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition.inputRequirements(): Set<ShaderInput> = buildSet {
    for (binding in program.bindings) {
        val descriptor = binding.descriptor
        when (descriptor.kind) {
            com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind.DATA,
            com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind.FRAME_DATA -> add(ShaderInput.RAW_DATA_TEXTURE)
            com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind.NESTED,
            com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind.FRAME_NESTED -> add(ShaderInput.NESTED_SHADER)
            else -> Unit
        }
        if (descriptor.rawRequired) add(ShaderInput.RAW_DATA_TEXTURE)
    }
}
