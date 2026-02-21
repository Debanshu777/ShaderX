package com.debanshu.asciicamera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.debanshu.asciicamera.ui.theme.ASCIICameraTheme
import com.debanshu.asciicamera.viewmodel.ASCIICameraViewModel

@Composable
fun ASCIICameraApp() {
    val viewModel: ASCIICameraViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    ASCIICameraTheme(darkTheme = uiState.isDarkTheme) {
        ASCIICameraContent(viewModel = viewModel)
    }
}
