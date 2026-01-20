package com.debanshu.shaderlab.viewmodel

import androidx.lifecycle.ViewModel
import com.debanshu.shaderlab.imagelib.PickResult
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
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
    val animationTime: Float = 0f,
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
        _uiState.update { it.copy(activeEffect = effect) }
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

    fun updateAnimationTime(time: Float) {
        _uiState.update { state ->
            val currentEffect = state.activeEffect
            if (currentEffect is AnimatedShaderEffect && currentEffect.isAnimating) {
                state.copy(
                    animationTime = time,
                    activeEffect = currentEffect.withTime(time),
                )
            } else {
                state.copy(animationTime = time)
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
