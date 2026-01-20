package com.debanshu.shaderlab.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.imagelib.ExportConfig
import com.debanshu.shaderlab.imagelib.ExportResult
import com.debanshu.shaderlab.imagelib.ImagePermission
import com.debanshu.shaderlab.imagelib.PermissionStatus
import com.debanshu.shaderlab.imagelib.PickResult
import com.debanshu.shaderlab.imagelib.createImageExporter
import com.debanshu.shaderlab.imagelib.rememberImagePickerLauncher
import com.debanshu.shaderlab.imagelib.rememberPermissionHandler
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.viewmodel.ImageSource
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderLabContent(
    viewModel: ShaderLabViewModel,
    availableEffects: List<ShaderEffect>,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionHandler = rememberPermissionHandler()
    val shaderFactory = remember { ShaderFactory.create() }
    val imageProcessor = remember { ImageProcessor.create() }

    val imagePicker =
        rememberImagePickerLauncher { result ->
            when (result) {
                is PickResult.Success -> {
                    viewModel.onImagePicked(result)
                }

                is PickResult.Error -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("Failed to pick image: ${result.message}")
                    }
                }

                is PickResult.Cancelled -> {
                }
            }
        }

    LaunchedEffect(uiState.exportMessage) {
        uiState.exportMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearExportMessage()
        }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "ShaderLab",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!uiState.shadersSupported) {
                            Box(
                                modifier =
                                    Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(4.dp),
                                        ).padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Limited",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleBeforeAfter() },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor =
                                    if (uiState.showBeforeAfter) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "Toggle before/after comparison",
                            tint =
                                if (uiState.showBeforeAfter) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                // Check write permission before export
                                val permissionStatus = permissionHandler.checkPermission(ImagePermission.WRITE_IMAGES)
                                val hasPermission = when (permissionStatus) {
                                    PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> true
                                    PermissionStatus.NOT_REQUESTED -> {
                                        val result = permissionHandler.requestPermission(ImagePermission.WRITE_IMAGES)
                                        result == PermissionStatus.GRANTED || result == PermissionStatus.NOT_REQUIRED
                                    }
                                    PermissionStatus.DENIED -> false
                                }

                                if (!hasPermission) {
                                    snackbarHostState.showSnackbar("Storage access denied. Please grant permission in settings.")
                                    return@launch
                                }

                                val selectedImage = uiState.selectedImage
                                val activeEffect = uiState.activeEffect
                                when (selectedImage) {
                                    is ImageSource.Picked -> {
                                        val originalBytes = selectedImage.bytes
                                        if (originalBytes != null) {
                                            val exporter = createImageExporter()
                                            if (exporter.isSupported) {
                                                // Apply shader effect if active and supported
                                                val bytesToExport =
                                                    if (activeEffect != null && shaderFactory.isSupported()) {
                                                        withContext(Dispatchers.Default) {
                                                            imageProcessor.process(originalBytes, activeEffect).getOrNull()
                                                        } ?: originalBytes
                                                    } else {
                                                        originalBytes
                                                    }

                                                val config = ExportConfig()
                                                val fileName = "shaderlab_${Random.nextInt(100000, 999999)}"
                                                when (val result = exporter.exportImage(bytesToExport, fileName, config)) {
                                                    is ExportResult.Success -> {
                                                        snackbarHostState.showSnackbar("Image saved successfully!")
                                                    }

                                                    is ExportResult.Error -> {
                                                        snackbarHostState.showSnackbar("Export failed: ${result.message}")
                                                    }

                                                    is ExportResult.NotSupported -> {
                                                        snackbarHostState.showSnackbar("Export not supported on this platform")
                                                    }
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("Export not available")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Cannot export - image data not available")
                                        }
                                    }

                                    is ImageSource.Bundled -> {
                                        snackbarHostState.showSnackbar("Please select a picked image to export")
                                    }
                                }
                            }
                        },
                        enabled = uiState.selectedImage is ImageSource.Picked && !uiState.isExporting,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Export image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
        ) {
            ImageGallery(
                sampleImages = uiState.sampleImages,
                pickedImages = uiState.pickedImages,
                selectedImage = uiState.selectedImage,
                onSelectImage = { viewModel.selectImage(it) },
                onAddImage = {
                    scope.launch {
                        val status = permissionHandler.checkPermission(ImagePermission.READ_IMAGES)
                        when (status) {
                            PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> {
                                imagePicker.launch()
                            }
                            PermissionStatus.NOT_REQUESTED -> {
                                val result = permissionHandler.requestPermission(ImagePermission.READ_IMAGES)
                                if (result == PermissionStatus.GRANTED || result == PermissionStatus.NOT_REQUIRED) {
                                    imagePicker.launch()
                                } else {
                                    snackbarHostState.showSnackbar("Photo access denied. Please grant permission in settings.")
                                }
                            }
                            PermissionStatus.DENIED -> {
                                snackbarHostState.showSnackbar("Photo access denied. Please grant permission in settings.")
                            }
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = uiState.showBeforeAfter,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "previewMode",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) { showComparison ->
                if (showComparison) {
                    BeforeAfterView(
                        imageSource = uiState.selectedImage,
                        effect = uiState.activeEffect,
                        onWaveTimeUpdate = { viewModel.updateAnimationTime(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    ShaderPreview(
                        imageSource = uiState.selectedImage,
                        effect = uiState.activeEffect,
                        onWaveTimeUpdate = { viewModel.updateAnimationTime(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ShaderControls(
                availableEffects = availableEffects,
                activeEffect = uiState.activeEffect,
                onEffectSelected = { viewModel.setActiveEffect(it) },
                onParameterChanged = { parameterId, value -> viewModel.updateEffectParameter(parameterId, value) },
                onClearEffect = { viewModel.setActiveEffect(null) },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
