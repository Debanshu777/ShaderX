package com.debanshu.shaderlab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.debanshu.shaderlab.shaderlib.ShaderLib
import com.debanshu.shaderlab.shaderlib.factory.ShaderFactory
import com.debanshu.shaderlab.shaderlib.factory.create
import com.debanshu.shaderlab.ui.ShaderLabContent
import com.debanshu.shaderlab.ui.theme.ShaderLabTheme
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel

/** Available shader effects for the app. */
private val availableEffects = ShaderLib.builtInEffects()

@Composable
fun ShaderLabApp() {
    val viewModel: ShaderLabViewModel = viewModel { ShaderLabViewModel() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setShadersSupported(ShaderFactory.create().isSupported())
    }

    ShaderLabTheme(darkTheme = uiState.isDarkTheme) {
        ShaderLabContent(
            viewModel = viewModel,
            availableEffects = availableEffects,
        )
    }
}
