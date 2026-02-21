package com.debanshu.asciicamera.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.debanshu.asciicamera.picker.rememberImagePickerLauncher
import com.debanshu.asciicamera.viewmodel.ASCIICameraViewModel
import com.debanshu.asciicamera.viewmodel.AsciiCameraTab
import com.debanshu.asciicamera.viewmodel.EffectMode
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ASCIICameraContent(
    modifier: Modifier = Modifier,
    viewModel: ASCIICameraViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val shaderFactory = remember { ShaderFactory.create() }
    val pickImageLauncher = rememberImagePickerLauncher { viewModel.setPickedImageBytes(it) }
    var contentWidth by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }

    val renderEffect =
        remember(
            uiState.activeEffect,
            contentWidth,
            contentHeight,
        ) {
            if (contentWidth <= 0 || contentHeight <= 0) {
                null
            } else {
                uiState.activeEffect?.let { effect ->
                    shaderFactory
                        .createRenderEffect(effect, contentWidth.toFloat(), contentHeight.toFloat())
                        .getOrNull()
                }
            }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("ASCII Camera") },
                actions = {
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (uiState.isDarkTheme) "Light theme" else "Dark theme",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                PrimaryTabRow(
                    selectedTabIndex =
                        when (uiState.activeTab) {
                            AsciiCameraTab.Camera -> 0
                            AsciiCameraTab.Image -> 1
                        },
                ) {
                    Tab(
                        selected = uiState.activeTab == AsciiCameraTab.Camera,
                        onClick = { viewModel.setActiveTab(AsciiCameraTab.Camera) },
                        text = { Text("Camera") },
                    )
                    Tab(
                        selected = uiState.activeTab == AsciiCameraTab.Image,
                        onClick = { viewModel.setActiveTab(AsciiCameraTab.Image) },
                        text = { Text("Image") },
                    )
                }
                EffectControls(
                    state = uiState,
                    onEffectModeChange = { viewModel.setEffectMode(it) },
                    onCellSizeChange = { viewModel.setCellSize(it) },
                    onUseFourColorChange = { viewModel.setUseFourColor(it) },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (uiState.activeTab) {
                AsciiCameraTab.Camera -> {
                    CameraTab(
                        isFrontCamera = uiState.isFrontCamera,
                        onFlipCamera = { viewModel.toggleCamera() },
                        onSizeChanged = { w, h ->
                            contentWidth = w
                            contentHeight = h
                        },
                        renderEffect = renderEffect,
                    )
                }

                AsciiCameraTab.Image -> {
                    ImageTab(
                        pickedImageBytes = uiState.pickedImageBytes,
                        onPickImage = pickImageLauncher,
                        onClearImage = { viewModel.clearPickedImage() },
                        onSizeChanged = { w, h ->
                            contentWidth = w
                            contentHeight = h
                        },
                        renderEffect = renderEffect,
                    )
                }
            }
        }
    }
}
