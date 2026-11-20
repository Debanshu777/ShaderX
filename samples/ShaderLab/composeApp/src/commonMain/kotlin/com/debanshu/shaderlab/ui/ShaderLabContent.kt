package com.debanshu.shaderlab.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.imagelib.ExportConfig
import com.debanshu.shaderlab.imagelib.ExportResult
import com.debanshu.shaderlab.imagelib.ImagePermission
import com.debanshu.shaderlab.imagelib.PermissionStatus
import com.debanshu.shaderlab.imagelib.PickResult
import com.debanshu.shaderlab.imagelib.createImageExporter
import com.debanshu.shaderlab.imagelib.rememberImagePickerLauncher
import com.debanshu.shaderlab.imagelib.rememberPermissionHandler
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupport
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.viewmodel.ImageSource
import com.debanshu.shaderlab.viewmodel.ShaderLabTab
import com.debanshu.shaderlab.viewmodel.ShaderLabUiState
import com.debanshu.shaderlab.viewmodel.ShaderLabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.debanshu.shaderlab.playground.PlaygroundRegistry

private enum class ShaderLabSection(val label: String) { GALLERY("Gallery"), SOURCE("Source"), PIPELINE("Pipeline") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderLabContent(viewModel: ShaderLabViewModel, availableEffects: List<ShaderEffect>) {
    val uiState by viewModel.uiState.collectAsState()
    val registry = remember(availableEffects) {
        PlaygroundRegistry((availableEffects + CustomDefinitions.effects()).map {
            com.debanshu.shaderlab.playground.PlaygroundEntry(it,
                if (it.id.startsWith("custom_")) "Custom starters" else "Built-in")
        })
    }
    val registeredEffects = registry.entries.map { it.starter }
    var section by remember { mutableStateOf(ShaderLabSection.GALLERY) }
    // Retain unsaved project data while leaving native factories/sessions in the visible panel.
    val workbenchWorkspace = remember { WorkbenchWorkspace() }
    val pipelineWorkspace = remember(registry) { PipelineWorkspace(registry) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionHandler = rememberPermissionHandler()
    val shaderFactory = rememberShaderFactory()
    var isExporting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
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

    val exportImage: () -> Unit = exportAction@{
        if (isExporting) return@exportAction
        isExporting = true
        scope.launch {
            try {
                val activeEffect = uiState.activeEffect
                if (activeEffect != null) {
                    val support = shaderFactory.querySupport(ShaderOperation.IMAGE_EXPORT, activeEffect)
                    if (support.status == ShaderSupportStatus.UNSUPPORTED) {
                        snackbarHostState.showSnackbar("Effect export unavailable: ${support.message}")
                        return@launch
                    }
                }
                // Check write permission only after effect-export preflight.
                val permissionStatus = permissionHandler.checkPermission(ImagePermission.WRITE_IMAGES)
                val hasPermission =
                    when (permissionStatus) {
                        PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> {
                            true
                        }

                        PermissionStatus.NOT_REQUESTED -> {
                            val result = permissionHandler.requestPermission(ImagePermission.WRITE_IMAGES)
                            result == PermissionStatus.GRANTED || result == PermissionStatus.NOT_REQUIRED
                        }

                        PermissionStatus.DENIED -> {
                            false
                        }
                    }

                if (!hasPermission) {
                    snackbarHostState.showSnackbar("Storage access denied. Please grant permission in settings.")
                    return@launch
                }

                val selectedImage = uiState.selectedImage
                when (selectedImage) {
                    is ImageSource.Picked -> {
                        val originalBytes = selectedImage.bytes
                        if (originalBytes != null) {
                            val exporter = createImageExporter()
                            if (exporter.isSupported) {
                                // Apply shader effect if active and supported
                                val bytesToExport =
                                    if (activeEffect != null) {
                                        val processed = withContext(Dispatchers.Default) {
                                            // A blocking export owns its factory so the UI cannot
                                            // evict or close its native resources concurrently.
                                            val exportFactory = ShaderFactory.create()
                                            try {
                                                ImageProcessor.create(exportFactory)
                                                    .process(originalBytes, activeEffect)
                                            } finally {
                                                exportFactory.close()
                                            }
                                        }
                                        when (processed) {
                                            is ShaderResult.Success -> processed.value
                                            is ShaderResult.Failure -> {
                                                snackbarHostState.showSnackbar("Effect export failed: ${processed.error.message}")
                                                return@launch
                                            }
                                        }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                snackbarHostState.showSnackbar("Export failed: ${error.message ?: "Unable to save image"}")
            } finally {
                isExporting = false
            }
        }
    }
    val onAddImage: () -> Unit = {
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
    }


    val playback = com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback(
        initiallyPlaying = false, active = section == ShaderLabSection.GALLERY && uiState.activeEffect is AnimatedShaderEffect)
    LaunchedEffect(uiState.activeEffect?.id, (uiState.activeEffect as? AnimatedShaderEffect)?.isAnimating) {
        playback.isPlaying = (uiState.activeEffect as? AnimatedShaderEffect)?.isAnimating ?: false
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("ShaderLab", fontWeight = FontWeight.Bold) }, actions = {
                IconButton(onClick = viewModel::toggleBeforeAfter, enabled = section == ShaderLabSection.GALLERY) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, "Compare original and effect")
                }
                IconButton(onClick = viewModel::toggleTheme) {
                    Icon(if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode, "Toggle theme")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Image actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (isExporting) "Exporting…" else "Export image") },
                            enabled = section == ShaderLabSection.GALLERY && uiState.selectedImage is ImageSource.Picked && !isExporting,
                            onClick = { menu = false; exportImage() })
                    }
                }
            })
        },
    ) { padding ->
        androidx.compose.runtime.CompositionLocalProvider(LocalPlaygroundPlayback provides playback, LocalPlaygroundRegistry provides registry) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = section.ordinal) {
                    ShaderLabSection.entries.forEach { destination ->
                        Tab(selected = section == destination, onClick = { section = destination }, text = { Text(destination.label) })
                    }
                }
                when (section) {
                    ShaderLabSection.SOURCE -> WorkbenchPanel(workbenchWorkspace)
                    ShaderLabSection.PIPELINE -> PipelinePanel(registry, uiState.selectedImage, shaderFactory, pipelineWorkspace)
                    ShaderLabSection.GALLERY -> GalleryLayout(uiState, viewModel, registeredEffects, shaderFactory, onAddImage)
                }
            }
        }
    }
}

@Composable
private fun ShaderCapabilityGuidance(effect: ShaderEffect?, factory: ShaderFactory) {
    val preview = remember(factory, effect) {
        factory.querySupport(
            if (effect is AnimatedShaderEffect) ShaderOperation.ANIMATED_SESSION else ShaderOperation.CONTENT_RENDER,
            effect,
        )
    }
    val export = remember(factory, effect) { factory.querySupport(ShaderOperation.IMAGE_EXPORT, effect) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (effect == null) {
                "Original image · No effect selected"
            } else {
                "Preview: ${preview.supportLabel()} · Effect export: ${export.supportLabel()}"
            },
            style = MaterialTheme.typography.labelMedium,
        )
        if (effect != null && preview.status != ShaderSupportStatus.SUPPORTED) {
            Text(
                text = if (preview.status == ShaderSupportStatus.UNSUPPORTED) {
                    "Original content fallback: ${preview.message}"
                } else {
                    "Preview support is unknown; rendering will be attempted and any error shown."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (effect != null && export.status != ShaderSupportStatus.SUPPORTED) {
            Text(
                text = "Effect export: ${export.message}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (effect is CompositeEffect && preview.status == ShaderSupportStatus.SUPPORTED) {
            Text(
                text = "Composite preview is static; animated children do not receive an automatic clock.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Support describes available operations; rendering can still fail. Browser previews validate Wasm/Skia only; validate Android and native targets separately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ShaderSupport.supportLabel(): String = when (status) {
    ShaderSupportStatus.SUPPORTED -> "available"
    ShaderSupportStatus.UNSUPPORTED -> "unavailable"
    ShaderSupportStatus.UNKNOWN -> "unknown"
}

@Composable
private fun ShaderLabPreviewPane(
    uiState: ShaderLabUiState,
    factory: ShaderFactory,
    modifier: Modifier = Modifier,
) {
    val audioEffect = uiState.activeEffect as? AudioVisualizerEffect
    val distortionEffect = uiState.activeEffect as? AudioDistortionEffect
    if (uiState.selectedTab == ShaderLabTab.AUDIO && audioEffect != null) {
        AudioVisualizer(
            baseEffect = audioEffect,
            factory = factory,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    if (distortionEffect != null) {
        AudioDistortionPreview(
            baseEffect = distortionEffect,
            imageSource = uiState.selectedImage,
            factory = factory,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    AnimatedContent(
        targetState = uiState.showBeforeAfter,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "previewMode",
        modifier = modifier,
    ) { showComparison ->
        if (showComparison) {
            BeforeAfterView(
                imageSource = uiState.selectedImage,
                effect = uiState.activeEffect,
                factory = factory,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ShaderPreview(
                imageSource = uiState.selectedImage,
                effect = uiState.activeEffect,
                factory = factory,
                showFps = uiState.selectedTab == ShaderLabTab.ANIMATED,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ShaderLabControlsPanel(uiState: ShaderLabUiState, viewModel: ShaderLabViewModel,
    availableEffects: List<ShaderEffect>, shaderFactory: ShaderFactory,
    sidePanel: Boolean, modifier: Modifier = Modifier) {
    val playback = LocalPlaygroundPlayback.current
    ShaderControls(
        tabEffects = viewModel.effectsForTab(availableEffects), activeEffect = uiState.activeEffect,
        selectedTab = uiState.selectedTab, onTabSelected = { viewModel.selectTab(it, availableEffects) },
        onEffectSelected = viewModel::setActiveEffect, onPresetSelected = viewModel::loadPreset,
        onTypedParameterChanged = viewModel::updateTypedParameter,
        extraControls = {
            ReusePanel(uiState.activeEffect, viewModel::loadPreset)
            PlaygroundDisclosure("Support & diagnostics") { ShaderCapabilityGuidance(uiState.activeEffect, shaderFactory) }
        },
        onClearEffect = { viewModel.setActiveEffect(null) },
        onToggleAnimation = {
            if (uiState.activeEffect is AudioVisualizerEffect || uiState.activeEffect is AudioDistortionEffect) viewModel.toggleAnimation()
            else if (playback != null) playback.isPlaying = !playback.isPlaying
            else viewModel.toggleAnimation()
        },
        onResetParameters = viewModel::resetEffectParameters, sidePanel = sidePanel, modifier = modifier,
    )
}

@Composable
private fun GalleryLayout(uiState: ShaderLabUiState, viewModel: ShaderLabViewModel,
    availableEffects: List<ShaderEffect>, shaderFactory: ShaderFactory, onAddImage: () -> Unit) {
    val images: @Composable () -> Unit = {
        PlaygroundDisclosure("Images") {
            ImageGallery(uiState.sampleImages, uiState.pickedImages, uiState.selectedImage,
                { viewModel.selectImage(it) }, onAddImage, orientation = GalleryOrientation.Horizontal)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShaderLabPreviewPane(uiState, shaderFactory, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        images()
                    }
                }
                ShaderLabControlsPanel(uiState, viewModel, availableEffects, shaderFactory, true,
                    Modifier.width(340.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShaderLabPreviewPane(uiState, shaderFactory, Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(top = 12.dp))
                images()
                ShaderLabControlsPanel(uiState, viewModel, availableEffects, shaderFactory, false)
            }
        }
    }
}
