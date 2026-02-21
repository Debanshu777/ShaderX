package com.debanshu.asciicamera.viewmodel

import androidx.lifecycle.ViewModel
import com.debanshu.asciicamera.shader.AsciiArtEffect
import com.debanshu.asciicamera.shader.ColorAsciiArtEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ASCIICameraUiState(
    val activeTab: AsciiCameraTab = AsciiCameraTab.Camera,
    val effectMode: EffectMode = EffectMode.None,
    val cellSize: Float = 12f,
    val isFrontCamera: Boolean = false,
    val useFourColor: Boolean = true,
    val pickedImageBytes: ByteArray? = null,
    val isDarkTheme: Boolean = false,
) {
    val activeEffect: ShaderEffect? =
        when (effectMode) {
            EffectMode.None -> {
                null
            }

            EffectMode.AsciiArt -> {
                AsciiArtEffect(cellSize = cellSize)
            }

            EffectMode.ColorAsciiArt -> {
                ColorAsciiArtEffect(
                    cellSize = cellSize,
                    useFourColor = useFourColor,
                )
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ASCIICameraUiState) return false
        if (activeTab != other.activeTab) return false
        if (effectMode != other.effectMode) return false
        if (cellSize != other.cellSize) return false
        if (isFrontCamera != other.isFrontCamera) return false
        if (useFourColor != other.useFourColor) return false
        if (pickedImageBytes != null) {
            if (other.pickedImageBytes == null) return false
            if (!pickedImageBytes.contentEquals(other.pickedImageBytes)) return false
        } else if (other.pickedImageBytes != null) {
            return false
        }
        if (isDarkTheme != other.isDarkTheme) return false
        return true
    }

    override fun hashCode(): Int {
        var result = activeTab.hashCode()
        result = 31 * result + effectMode.hashCode()
        result = 31 * result + cellSize.hashCode()
        result = 31 * result + isFrontCamera.hashCode()
        result = 31 * result + useFourColor.hashCode()
        result = 31 * result + (pickedImageBytes?.contentHashCode() ?: 0)
        result = 31 * result + isDarkTheme.hashCode()
        return result
    }
}

class ASCIICameraViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ASCIICameraUiState())
    val uiState: StateFlow<ASCIICameraUiState> = _uiState.asStateFlow()

    fun setActiveTab(tab: AsciiCameraTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setEffectMode(mode: EffectMode) {
        _uiState.update { it.copy(effectMode = mode) }
    }

    fun setCellSize(size: Float) {
        _uiState.update { it.copy(cellSize = size.coerceIn(4f, 32f)) }
    }

    fun toggleCamera() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun setUseFourColor(use: Boolean) {
        _uiState.update { it.copy(useFourColor = use) }
    }

    fun setPickedImageBytes(bytes: ByteArray?) {
        _uiState.update { it.copy(pickedImageBytes = bytes) }
    }

    fun clearPickedImage() {
        _uiState.update { it.copy(pickedImageBytes = null) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }
}
