@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
package dev.threeviewer.ui

import android.text.format.Formatter
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.threeviewer.R
import dev.threeviewer.data.LoadedModel
import dev.threeviewer.render.OrbitCamera
import dev.threeviewer.render.VulkanModelView
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ViewerScreen(model: LoadedModel, back: () -> Unit, open: () -> Unit) {
    val modelName = if (model.source.uri.startsWith("sample:")) stringResource(R.string.sample_name) else model.source.name
    var sheet by rememberSaveable(model.source.uri) { mutableStateOf(0) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val fullscreenBack = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val backMotion = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var ready by remember(model) { mutableStateOf(false) }
    var failed by remember(model) { mutableStateOf(false) }
    var playing by rememberSaveable(model.source.uri) { mutableStateOf(false) }
    var background by rememberSaveable { mutableIntStateOf(0) }
    var exposure by rememberSaveable { mutableFloatStateOf(0f) }
    var animation by rememberSaveable(model.source.uri) { mutableIntStateOf(0) }
    var renderer by remember { mutableStateOf<VulkanModelView?>(null) }
    var zoomPercent by remember(model) { mutableIntStateOf(100) }
    val camera = remember(model) { OrbitCamera() }
    val scheme = MaterialTheme.colorScheme
    val studioColor = scheme.surfaceContainerLow
    val bg = when (background) { 1 -> Color(0xFFF3F3F3); 2 -> Color(0xFF1C1C20); else -> studioColor }
    val argb = bg.toArgb()
    val rgb = intArrayOf((argb shr 16) and 255, (argb shr 8) and 255, argb and 255)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, renderer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) renderer?.setActive(true)
            if (event == Lifecycle.Event.ON_PAUSE) renderer?.setActive(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); renderer?.setActive(false) }
    }
    // ModalBottomSheet owns its predictive-back callback. Only full screen needs custom handling.
    PredictiveBackHandler(enabled = fullscreen && sheet == 0) { events ->
        try {
            events.collect { fullscreenBack.snapTo(it.progress) }
            fullscreen = false
            fullscreenBack.snapTo(0f)
        } catch (cancelled: CancellationException) {
            scope.launch { fullscreenBack.animateTo(0f, backMotion) }
            throw cancelled
        }
    }

    val actions = listOf(
        CustomAccessibilityAction(stringResource(R.string.rotate_left)) { camera.rotate(-.25, 0.0); true },
        CustomAccessibilityAction(stringResource(R.string.rotate_right)) { camera.rotate(.25, 0.0); true },
        CustomAccessibilityAction(stringResource(R.string.rotate_up)) { camera.rotate(0.0, .2); true },
        CustomAccessibilityAction(stringResource(R.string.rotate_down)) { camera.rotate(0.0, -.2); true },
        CustomAccessibilityAction(stringResource(R.string.zoom_in)) { camera.zoom(1.2); true },
        CustomAccessibilityAction(stringResource(R.string.zoom_out)) { camera.zoom(1 / 1.2); true },
        CustomAccessibilityAction(stringResource(R.string.reset_view)) { camera.reset(); true }
    )
    val description = stringResource(R.string.model_view, modelName)
    val cameraDescription = stringResource(R.string.camera_state, zoomPercent)
    Scaffold(containerColor = bg, topBar = {
        if (!fullscreen) TopAppBar(title = {
            Text(modelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        }, navigationIcon = { ActionIcon(R.drawable.ic_arrow_back, R.string.back, onClick = back) },
            actions = { ActionIcon(R.drawable.ic_folder_open, R.string.open_model, onClick = open) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = bg))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).graphicsLayer {
            val scale = 1f - .06f * fullscreenBack.value
            scaleX = scale; scaleY = scale
        }) {
            if (!failed) AndroidView(factory = { context ->
                VulkanModelView(context, model, camera, onReady = { ready = true }, onFailure = { failed = true }, onCameraChanged = { zoomPercent = it })
                    .also { renderer = it }
            }, update = { it.configure(rgb, exposure, playing, animation) },
                onRelease = { it.setActive(false) },
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = description
                    stateDescription = cameraDescription
                    customActions = actions
                }.onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                        Key.DirectionLeft -> { camera.rotate(-.12, 0.0); true }
                        Key.DirectionRight -> { camera.rotate(.12, 0.0); true }
                        Key.DirectionUp -> { camera.rotate(0.0, .12); true }
                        Key.DirectionDown -> { camera.rotate(0.0, -.12); true }
                        Key.Plus, Key.Equals -> { camera.zoom(1.15); true }
                        Key.Minus -> { camera.zoom(1 / 1.15); true }
                        Key.MoveHome -> { camera.reset(); true }
                        else -> false
                    }
                }.focusable())
            if (!ready && !failed) Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Text(stringResource(R.string.loading), Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (failed) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.render_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(onClick = open, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.open_model)) }
            }
            if (!fullscreen) {
                HorizontalFloatingToolbar(expanded = true,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 24.dp)) {
                    ActionIcon(R.drawable.ic_restart_alt, R.string.reset_view, ready) { camera.reset() }
                    if (model.info.animations.isNotEmpty()) {
                        FilledIconToggleButton(playing, { playing = it }, enabled = ready) {
                            Symbol(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                                stringResource(if (playing) R.string.pause_animation else R.string.play_animation))
                        }
                    }
                    ActionIcon(R.drawable.ic_tune, R.string.view_options, ready) { sheet = 1 }
                    ActionIcon(R.drawable.ic_info, R.string.model_info) { sheet = 2 }
                    ActionIcon(R.drawable.ic_open_in_full, R.string.fullscreen, ready) { fullscreen = true }
                }
            } else {
                FilledTonalIconButton({ fullscreen = false }, Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp)) {
                    Symbol(R.drawable.ic_fullscreen_exit, stringResource(R.string.exit_fullscreen))
                }
            }
        }
    }
    if (sheet != 0) ModalBottomSheet(onDismissRequest = { sheet = 0 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (sheet == 1) R.string.view_options else R.string.model_info), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
                ActionIcon(R.drawable.ic_close, R.string.close) { sheet = 0 }
            }
            if (sheet == 1) {
                SectionTitle(R.string.background)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.background_studio, R.string.background_light, R.string.background_dark).forEachIndexed { index, label ->
                        FilterChip(background == index, { background = index }, label = { Text(stringResource(label)) },
                            leadingIcon = if (background == index) { { Symbol(R.drawable.ic_check) } } else null)
                    }
                }
                SectionTitle(R.string.lighting)
                val exposureLabel = stringResource(R.string.exposure)
                Text(exposureLabel, style = MaterialTheme.typography.bodyLarge)
                Slider(exposure, { exposure = it }, valueRange = -2f..2f, steps = 7,
                    modifier = Modifier.semantics { contentDescription = exposureLabel })
                if (model.info.animations.isNotEmpty()) {
                    SectionTitle(R.string.animation)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        model.info.animations.forEachIndexed { index, name ->
                            FilterChip(animation == index, { animation = index }, label = {
                                Text(if (model.source.uri.startsWith("sample:")) stringResource(R.string.animation_number, index + 1)
                                    else name.ifBlank { stringResource(R.string.animation_number, index + 1) })
                            })
                        }
                    }
                }
                SectionTitle(R.string.camera)
                Text(stringResource(R.string.gesture_help), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton({ camera.rotate(-.25, 0.0) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_chevron_left, stringResource(R.string.rotate_left)) }
                    FilledTonalIconButton({ camera.rotate(.25, 0.0) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_chevron_right, stringResource(R.string.rotate_right)) }
                    FilledTonalIconButton({ camera.rotate(0.0, .2) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.rotate_up)) }
                    FilledTonalIconButton({ camera.rotate(0.0, -.2) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.rotate_down)) }
                    FilledTonalIconButton({ camera.zoom(1.2) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_add, stringResource(R.string.zoom_in)) }
                    FilledTonalIconButton({ camera.zoom(1 / 1.2) }, Modifier.size(48.dp)) { Symbol(R.drawable.ic_remove, stringResource(R.string.zoom_out)) }
                }
                Button(onClick = { camera.reset() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.reset_view)) }
            } else {
                val context = LocalContext.current
                val numbers = NumberFormat.getIntegerInstance()
                InfoRow(R.string.file_name, modelName)
                InfoRow(R.string.file_size, Formatter.formatFileSize(context, model.info.bytes.toLong()))
                InfoRow(R.string.format, stringResource(R.string.glb_format))
                InfoRow(R.string.meshes, numbers.format(model.info.meshes))
                InfoRow(R.string.triangles, numbers.format(model.info.triangles))
                InfoRow(R.string.materials, numbers.format(model.info.materials))
                InfoRow(R.string.animations, numbers.format(model.info.animations.size))
                InfoRow(R.string.renderer, stringResource(R.string.vulkan))
            }
        }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
