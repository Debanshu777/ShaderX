package com.debanshu.shaderlab.viewmodel

import androidx.lifecycle.ViewModel
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.imagelib.PickResult
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterRangePolicy
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.effect.withColorParameter
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class ImageSource {
    data class Bundled(
        val resourceName: String,
    ) : ImageSource()

    data class Picked(
        val path: String,
        val bytes: ByteArray?,
    ) : ImageSource() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Picked) return false
            return path == other.path && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + (bytes?.contentHashCode() ?: 0)
            return result
        }
    }
}

data class ShaderLabUiState(
    val selectedImage: ImageSource = ImageSource.Bundled("sample_landscape"),
    val activeEffect: ShaderEffect? = GrayscaleEffect(),
    val selectedTab: ShaderLabTab = ShaderLabTab.STATIC,
    val showBeforeAfter: Boolean = false,
    val isDarkTheme: Boolean = true,
    val sampleImages: List<String> =
        listOf(
            "sample_landscape",
            "sample_portrait",
            "sample_nature",
            "sample_abstract",
        ),
    val pickedImages: List<ImageSource.Picked> = emptyList(),
    val shadersSupported: Boolean = true,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
)

class ShaderLabViewModel : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            ShaderLabUiState(
                shadersSupported = ShaderFactory.create().let { factory ->
                    try { factory.isSupported() } finally { factory.close() }
                },
            ),
        )
    val uiState: StateFlow<ShaderLabUiState> = _uiState.asStateFlow()

    fun selectImage(source: ImageSource) {
        _uiState.update { it.copy(selectedImage = source) }
    }

    fun setActiveEffect(effect: ShaderEffect?) {
        if (effect != null && effect.id == _uiState.value.activeEffect?.id) {
            return
        }
        val resolved =
            when (effect) {
                is AnimatedShaderEffect -> effect.withTime(0f) as ShaderEffect
                else -> effect
            }
        _uiState.update { it.copy(activeEffect = resolved) }
    }

    fun loadPreset(effect: ShaderEffect) {
        _uiState.update { it.copy(activeEffect = effect, selectedTab = if (effect is AnimatedShaderEffect) ShaderLabTab.ANIMATED else ShaderLabTab.STATIC) }
    }

    fun selectTab(
        tab: ShaderLabTab,
        availableEffects: List<ShaderEffect>,
    ) {
        _uiState.update { state ->
            val filtered = filterEffectsForTab(availableEffects, tab)
            val retainedActive =
                state.activeEffect?.takeIf { active ->
                    filtered.any { it.id == active.id }
                }
            val nextEffect = retainedActive ?: filtered.firstOrNull()
            val resolvedNext =
                when (nextEffect) {
                    is AnimatedShaderEffect -> nextEffect.withTime(0f) as ShaderEffect
                    else -> nextEffect
                }
            state.copy(
                selectedTab = tab,
                activeEffect = resolvedNext,
            )
        }
    }

    fun effectsForTab(
        availableEffects: List<ShaderEffect>,
        tab: ShaderLabTab = _uiState.value.selectedTab,
    ): List<ShaderEffect> = filterEffectsForTab(availableEffects, tab)

    private fun filterEffectsForTab(
        availableEffects: List<ShaderEffect>,
        tab: ShaderLabTab,
    ): List<ShaderEffect> =
        when (tab) {
            ShaderLabTab.STATIC -> {
                availableEffects.filter { it !is AnimatedShaderEffect && it !is CompositeEffect }
            }

            ShaderLabTab.ANIMATED -> {
                // Spectrum graph stays on the Audio tab; distortion is an animated image effect.
                availableEffects.filterIsInstance<AnimatedShaderEffect>().filter {
                    it !is AudioVisualizerEffect
                }
            }

            ShaderLabTab.COMPOSITE -> {
                emptyList()
            }

            ShaderLabTab.AUDIO -> {
                availableEffects.filterIsInstance<AudioVisualizerEffect>()
            }
        }

    fun toggleAnimation() {
        _uiState.update { state ->
            val current = state.activeEffect as? AnimatedShaderEffect ?: return@update state
            val updated =
                if (current is DefinedAnimatedShaderEffect) {
                    current.withPlayback(!current.isAnimating)
                } else if (current is WaveEffect) {
                    WaveEffect.SCHEMA.valuesFrom(current)
                        .withValue(WaveEffect.ANIMATE.key, !current.animate)
                        .applyTo(current)
                } else {
                    current.withParameter("animate", if (current.isAnimating) 0f else 1f)
                }
            state.copy(activeEffect = updated)
        }
    }

    fun updateTypedParameter(parameterId: String, value: ParameterValue) {
        _uiState.update { state ->
            val effect = state.activeEffect ?: return@update state
            val schema = com.debanshu.shaderlab.playground.parameterSchema(effect)
            state.copy(activeEffect = schema.valuesFrom(effect).withValue(parameterId, value).applyTo(effect))
        }
    }

    // Existing sample callers retain their numeric convenience entry point.
    fun updateEffectParameter(parameterId: String, value: Float): Unit =
        updateTypedParameter(parameterId, ParameterValue.FloatValue(value.let { number ->
            require(number.isFinite()) { "Numeric edit requires a finite value" }
            val schema = com.debanshu.shaderlab.playground.parameterSchema(_uiState.value.activeEffect ?: return)
            val definition = schema.definition(parameterId) as? com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
            definition?.range?.let { number.coerceIn(it) } ?: number
        }))

    fun updateEffectColorParameter(
        parameterId: String,
        colorArgb: Long,
    ) {
        _uiState.update { state ->
            val currentEffect = state.activeEffect ?: return@update state
            val schema = currentEffect.pilotParameterSchema()
            val updatedEffect =
                if (schema != null) {
                    schema.valuesFrom(currentEffect)
                        .withValue(parameterId, ParameterValue.ColorValue(colorArgb))
                        .applyTo(currentEffect)
                } else {
                    currentEffect.withColorParameter(parameterId, colorArgb)
                }
            state.copy(activeEffect = updatedEffect)
        }
    }

    fun resetEffectParameters() {
        _uiState.update { state ->
            val current = state.activeEffect ?: return@update state
            val schema = com.debanshu.shaderlab.playground.parameterSchema(current)
            // Reapply defaults to the current instance, preserving playback and current frame time.
            state.copy(activeEffect = schema.defaults().applyTo(current))
        }
    }

    fun toggleBeforeAfter() {
        _uiState.update { it.copy(showBeforeAfter = !it.showBeforeAfter) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun onImagePicked(result: PickResult) {
        when (result) {
            is PickResult.Success -> {
                val picked = ImageSource.Picked(result.uri, result.bytes)
                _uiState.update { state ->
                    state.copy(
                        pickedImages = state.pickedImages + picked,
                        selectedImage = picked,
                    )
                }
            }

            is PickResult.Error -> {
            }

            is PickResult.Cancelled -> {
            }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    fun setShadersSupported(supported: Boolean) {
        _uiState.update { it.copy(shadersSupported = supported) }
    }
}

private fun ShaderEffect.pilotParameterSchema(): ParameterSchema? =
    when (this) {
        is GradientEffect -> GradientEffect.SCHEMA
        is WaveEffect -> WaveEffect.SCHEMA
        is DefinedShaderEffect -> definition.schema
        is DefinedAnimatedShaderEffect -> definition.schema
        else -> null
    }
