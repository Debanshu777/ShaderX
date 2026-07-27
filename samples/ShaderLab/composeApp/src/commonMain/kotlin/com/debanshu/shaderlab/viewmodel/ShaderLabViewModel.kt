package com.debanshu.shaderlab.viewmodel

import androidx.lifecycle.ViewModel
import com.debanshu.shaderlab.imagelib.PickResult
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
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
    val activeEffect: ShaderEffect? = null,
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
                shadersSupported = ShaderFactory.create().isSupported(),
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
            val updated = current.withParameter("animate", if (current.isAnimating) 0f else 1f)
            state.copy(activeEffect = updated)
        }
    }

    fun updateEffectParameter(
        parameterId: String,
        value: Float,
    ) {
        _uiState.update { state ->
            val currentEffect = state.activeEffect ?: return@update state
            val updatedEffect = currentEffect.withParameter(parameterId, value)
            state.copy(activeEffect = updatedEffect)
        }
    }

    fun updateEffectColorParameter(
        parameterId: String,
        colorArgb: Long,
    ) {
        _uiState.update { state ->
            val currentEffect = state.activeEffect ?: return@update state
            state.copy(activeEffect = currentEffect.withColorParameter(parameterId, colorArgb))
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
