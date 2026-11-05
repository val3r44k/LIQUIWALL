package com.val3r44k.vallport.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.val3r44k.vallport.R
import com.val3r44k.vallport.data.DropWallConfig
import com.val3r44k.vallport.graphics.CircleGestureDetector
import com.val3r44k.vallport.graphics.WallpaperBitmapLoader
import com.val3r44k.vallport.physics.SpringMeshPhysics
import com.val3r44k.vallport.service.DropWallWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppScreen {
    WELCOME,
    GALLERY,
    EDITOR,
}

enum class CanvasToolMode {
    DRAW,
    ERASE,
}

data class MaskDrawStroke(
    val path: Path,
    val strokeWidth: Float,
    val isEraser: Boolean,
    val isFilled: Boolean = false,
)

data class LocalGalleryItem(
    val id: String,
    val title: String,
    val imagePath: String?,
    val isDefault: Boolean = false,
)

private val AppAccentColor = Color(0xFFCC0000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropWallConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var config by remember { mutableStateOf(DropWallConfig.loadForWallpaper(context, null)) }
    var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }

    var stiffness by remember { mutableFloatStateOf(config.stiffness) }
    var damping by remember { mutableFloatStateOf(config.damping) }
    var tiltSensitivity by remember { mutableFloatStateOf(config.tiltSensitivity) }
    var touchRadius by remember { mutableFloatStateOf(config.touchRadius) }
    var parallaxEnabled by remember { mutableStateOf(config.parallaxEnabled) }

    var autoFillCircle by remember { mutableStateOf(config.autoFillCircle) }

    var showQuickActions by remember { mutableStateOf(config.showQuickActions) }
    var showToolToolbar by remember { mutableStateOf(config.showToolToolbar) }
    var showSettingsTabs by remember { mutableStateOf(config.showSettingsTabs) }

    // Mask editing history with robust snapshot-based Undo / Redo stacks
    val strokes = remember { mutableStateListOf<MaskDrawStroke>() }
    val undoStack = remember { mutableStateListOf<List<MaskDrawStroke>>() }
    val redoStack = remember { mutableStateListOf<List<MaskDrawStroke>>() }

    var brushSize by remember { mutableFloatStateOf(36f) }
    var toolMode by remember { mutableStateOf(CanvasToolMode.DRAW) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var wallpaperImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var savedMaskBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    val previewMesh = remember { SpringMeshPhysics(cols = 16, rows = 28) }

    val photoUpdatedMsg = stringResource(R.string.photo_updated_toast)

    fun reloadPreviewBitmap(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                WallpaperBitmapLoader.loadBackground(
                    context = context,
                    targetWidth = w, targetHeight = h, customPath = config.customImagePath,
                )
            }
            wallpaperImageBitmap = bmp.asImageBitmap()
            previewMesh.setup(w.toFloat(), h.toFloat())

            val mask = if (strokes.isNotEmpty()) {
                createMaskBitmapFromStrokes(w, h, w, h, strokes.toList())
            } else {
                WallpaperBitmapLoader.loadMask(w, h, config.customMaskPath)
            }

            if (mask != null) {
                savedMaskBitmap = mask.asImageBitmap()
                updateMeshWeightsFromMask(previewMesh, mask)
            } else {
                savedMaskBitmap = null
                previewMesh.setAllWeights(1.0f)
            }
        }
    }

    LaunchedEffect(previewSize, config.customImagePath, config.customMaskPath) {
        if (previewSize.width > 0 && previewSize.height > 0) {
            reloadPreviewBitmap(previewSize.width, previewSize.height)
        }
    }

    fun persistConfig(newConfig: DropWallConfig) {
        config = newConfig
        DropWallConfig.saveForWallpaper(context, newConfig)
        previewMesh.stiffness = newConfig.stiffness
        previewMesh.damping = newConfig.damping
    }

    // Photo picker launcher for importing custom images into internal app storage
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = WallpaperBitmapLoader.saveCustomImageFromUri(context, uri)
            if (savedPath != null) {
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                savedMaskBitmap = null

                val updated = DropWallConfig.loadForWallpaper(context, savedPath)
                stiffness = updated.stiffness
                damping = updated.damping
                tiltSensitivity = updated.tiltSensitivity
                touchRadius = updated.touchRadius
                autoFillCircle = updated.autoFillCircle

                persistConfig(updated)
                currentScreen = AppScreen.EDITOR

                if (previewSize.width > 0 && previewSize.height > 0) {
                    reloadPreviewBitmap(previewSize.width, previewSize.height)
                }
                scope.launch { snackbarHostState.showSnackbar(photoUpdatedMsg) }
            }
        }
    }

    fun syncMaskToMeshAndDisk(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val bgWidth = wallpaperImageBitmap?.width ?: width
        val bgHeight = wallpaperImageBitmap?.height ?: height

        val maskBitmap = createMaskBitmapFromStrokes(bgWidth, bgHeight, width, height, strokes)
        if (maskBitmap != null) {
            val maskPath = WallpaperBitmapLoader.saveCustomMaskBitmap(context, config.customImagePath, maskBitmap)
            savedMaskBitmap = maskBitmap.asImageBitmap()
            if (maskPath != config.customMaskPath) {
                persistConfig(config.copy(customMaskPath = maskPath))
            }
            updateMeshWeightsFromMask(previewMesh, maskBitmap)
        } else if (strokes.isEmpty() && config.customMaskPath != null) {
            val existingMask = WallpaperBitmapLoader.loadMask(width, height, config.customMaskPath)
            if (existingMask != null) {
                savedMaskBitmap = existingMask.asImageBitmap()
                updateMeshWeightsFromMask(previewMesh, existingMask)
            }
        }
    }

    if (currentScreen == AppScreen.WELCOME) {
        WelcomeScreen(
            hasExistingProject = !config.customImagePath.isNullOrEmpty(),
            accentColor = AppAccentColor,
            onOpenGallery = {
                currentScreen = AppScreen.GALLERY
            },
            onImportPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onResumeProject = {
                currentScreen = AppScreen.EDITOR
                if (previewSize.width > 0 && previewSize.height > 0) {
                    reloadPreviewBitmap(previewSize.width, previewSize.height)
                }
            }
        )
        return
    }

    if (currentScreen == AppScreen.GALLERY) {
        InternalGalleryScreen(
            accentColor = AppAccentColor,
            currentCustomImagePath = config.customImagePath,
            onSelectWallpaper = { selectedPath ->
                strokes.clear()
                undoStack.clear()
                redoStack.clear()
                savedMaskBitmap = null
                val updated = DropWallConfig.loadForWallpaper(context, selectedPath)
                stiffness = updated.stiffness
                damping = updated.damping
                tiltSensitivity = updated.tiltSensitivity
                touchRadius = updated.touchRadius
                autoFillCircle = updated.autoFillCircle
                persistConfig(updated)
                currentScreen = AppScreen.EDITOR
                if (previewSize.width > 0 && previewSize.height > 0) {
                    reloadPreviewBitmap(previewSize.width, previewSize.height)
                }
            },
            onDeleteWallpaper = { itemToDelete ->
                if (itemToDelete.imagePath != null) {
                    val file = File(itemToDelete.imagePath)
                    if (file.exists()) file.delete()
                    WallpaperBitmapLoader.clearCustomMask(context, itemToDelete.imagePath)
                    if (config.customImagePath == itemToDelete.imagePath) {
                        val updated = DropWallConfig.loadForWallpaper(context, null)
                        persistConfig(updated)
                    }
                }
            },
            onExportWallpaper = { imagePath ->
                if (imagePath != null) {
                    val projConfig = DropWallConfig.loadForWallpaper(context, imagePath)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "LIQUIWALL Project Export")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "LIQUIWALL Project:\nImage Path: ${projConfig.customImagePath}\nMask Path: ${projConfig.customMaskPath}\nStiffness: ${projConfig.stiffness}\nDamping: ${projConfig.damping}\nTilt Sensitivity: ${projConfig.tiltSensitivity}"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Project"))
                }
            },
            onAddCustomPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onBack = { currentScreen = AppScreen.WELCOME }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(AppAccentColor),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { currentScreen = AppScreen.WELCOME },
                        shape = RectangleShape,
                    ) {
                        Text(text = "Home", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Sharp Square Canvas Preview Frame (Zero Rounding, Height 540dp) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(540.dp)
                        .border(
                            width = 2.dp,
                            color = Color.LightGray,
                            shape = RectangleShape
                        ),
                    shape = RectangleShape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        JellyWallpaperEditorScreen(
                            wallpaperImageBitmap = wallpaperImageBitmap,
                            savedMaskBitmap = savedMaskBitmap,
                            meshPhysics = previewMesh,
                            parallaxEnabled = parallaxEnabled,
                            strokes = strokes,
                            brushSize = brushSize,
                            toolMode = toolMode,
                            autoFillCircle = autoFillCircle,
                            onSizeChanged = { size ->
                                previewSize = size
                                if (previewMesh.vertexCount == 0 && size.width > 0 && size.height > 0) {
                                    previewMesh.setup(size.width.toFloat(), size.height.toFloat())
                                }
                            },
                            onStrokeStart = {
                                undoStack.add(strokes.toList())
                                redoStack.clear()
                            },
                            onStrokeEnd = { completedStroke ->
                                strokes.add(completedStroke)
                                redoStack.clear()
                                if (completedStroke.isFilled) {
                                    scope.launch { snackbarHostState.showSnackbar("Circle detected & auto-filled!") }
                                }
                                syncMaskToMeshAndDisk(previewSize.width, previewSize.height)
                            },
                            onPhysicsDisturbance = { touchX, touchY, dx, dy ->
                                previewMesh.applyTouchDisturbance(touchX, touchY, dx, dy, touchRadius, config.touchImpulse)
                            },
                        )
                    }
                }
            }

            // ── Complete Sharp Square Toolbar Bar Directly Below Preview ──
            if (showToolToolbar) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = toolMode == CanvasToolMode.DRAW,
                            onClick = { toolMode = CanvasToolMode.DRAW },
                            label = { Text(stringResource(R.string.draw_mode), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RectangleShape,
                        )
                        FilterChip(
                            selected = toolMode == CanvasToolMode.ERASE,
                            onClick = { toolMode = CanvasToolMode.ERASE },
                            label = { Text(stringResource(R.string.erase_mode), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RectangleShape,
                        )
                        Spacer(modifier = Modifier.size(2.dp))
                        OutlinedButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    redoStack.add(strokes.toList())
                                    val previousState = undoStack.removeAt(undoStack.size - 1)
                                    strokes.clear()
                                    strokes.addAll(previousState)
                                    syncMaskToMeshAndDisk(previewSize.width, previewSize.height)
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            shape = RectangleShape,
                        ) {
                            Text(stringResource(R.string.undo), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack.add(strokes.toList())
                                    val nextState = redoStack.removeAt(redoStack.size - 1)
                                    strokes.clear()
                                    strokes.addAll(nextState)
                                    syncMaskToMeshAndDisk(previewSize.width, previewSize.height)
                                }
                            },
                            enabled = redoStack.isNotEmpty(),
                            shape = RectangleShape,
                        ) {
                            Text(stringResource(R.string.redo), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                undoStack.add(strokes.toList())
                                redoStack.clear()
                                strokes.clear()
                                savedMaskBitmap = null
                                WallpaperBitmapLoader.clearCustomMask(context, config.customImagePath)
                                previewMesh.setAllWeights(1.0f)
                                if (config.customMaskPath != null) {
                                    persistConfig(config.copy(customMaskPath = null))
                                }
                                if (previewSize.width > 0 && previewSize.height > 0) {
                                    reloadPreviewBitmap(previewSize.width, previewSize.height)
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.clear_mask),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // ── 60 FPS Physics Simulation Ticker for Preview ──
            LaunchedEffect(stiffness, damping, parallaxEnabled) {
                previewMesh.stiffness = stiffness
                previewMesh.damping = damping
                var prevNanos = System.nanoTime()
                while (true) {
                    withFrameNanos { nowNanos ->
                        val dt = ((nowNanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                        prevNanos = nowNanos
                        if (parallaxEnabled) {
                            previewMesh.update(dt)
                        } else {
                            if (previewSize.width > 0 && previewSize.height > 0) {
                                previewMesh.setup(previewSize.width.toFloat(), previewSize.height.toFloat())
                            }
                        }
                    }
                }
            }

            // ── Action Controls (Apply Wallpaper with Auto-Save, Open Gallery, Reset) ──
            if (showQuickActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            // Automatically save mask and configuration before applying live wallpaper
                            if (previewSize.width > 0 && previewSize.height > 0) {
                                syncMaskToMeshAndDisk(previewSize.width, previewSize.height)
                            }
                            persistConfig(config)
                            launchLiveWallpaperChooser(context)
                        },
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccentColor),
                    ) {
                        Text(text = stringResource(R.string.apply_wallpaper), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    FilledTonalButton(
                        onClick = {
                            currentScreen = AppScreen.GALLERY
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RectangleShape,
                    ) {
                        Text(text = stringResource(R.string.open_gallery), fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
                    }
                }

                if (config.customImagePath != null) {
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            undoStack.clear()
                            redoStack.clear()
                            savedMaskBitmap = null
                            WallpaperBitmapLoader.clearCustomMask(context, config.customImagePath)
                            WallpaperBitmapLoader.clearCustomImage(context)
                            previewMesh.setAllWeights(1.0f)
                            val updated = DropWallConfig.loadForWallpaper(context, null)
                            persistConfig(updated)
                            if (previewSize.width > 0 && previewSize.height > 0) {
                                reloadPreviewBitmap(previewSize.width, previewSize.height)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RectangleShape,
                    ) {
                        Text(stringResource(R.string.reset_default_photo), fontSize = 13.sp)
                    }
                }
            }

            // ── Settings Tabs ──
            if (showSettingsTabs) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.clip(RectangleShape),
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.tab_mask), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.tab_physics), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text(stringResource(R.string.tab_theme), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                    )
                }

                if (selectedTabIndex == 0) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.auto_fill_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.auto_fill_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = autoFillCircle, onCheckedChange = { autoFillCircle = it; persistConfig(config.copy(autoFillCircle = it)) })
                            }
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.brush_size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("${brushSize.toInt()} px", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppAccentColor)
                                }
                                Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 12f..90f)
                            }
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.touch_radius), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("${touchRadius.toInt()} px", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppAccentColor)
                                }
                                Slider(value = touchRadius, onValueChange = { touchRadius = it; persistConfig(config.copy(touchRadius = it)) }, valueRange = 100f..500f)
                            }
                        }
                    }
                }

                if (selectedTabIndex == 1) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.physics_settings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Column {
                                Text(stringResource(R.string.stiffness_label, stiffness), style = MaterialTheme.typography.bodyMedium)
                                Slider(value = stiffness, onValueChange = { stiffness = it; persistConfig(config.copy(stiffness = it)) }, valueRange = 20f..200f)
                            }
                            Column {
                                Text(stringResource(R.string.damping_label, damping), style = MaterialTheme.typography.bodyMedium)
                                Slider(value = damping, onValueChange = { damping = it; persistConfig(config.copy(damping = it)) }, valueRange = 4f..35f)
                            }
                        }
                    }
                }

                if (selectedTabIndex == 2) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RectangleShape) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.master_physics_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.master_physics_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = parallaxEnabled,
                                    onCheckedChange = {
                                        parallaxEnabled = it
                                        persistConfig(config.copy(parallaxEnabled = it))
                                    },
                                )
                            }
                            Column {
                                Text(stringResource(R.string.tilt_label, tiltSensitivity), style = MaterialTheme.typography.bodyMedium)
                                Slider(value = tiltSensitivity, onValueChange = { tiltSensitivity = it; persistConfig(config.copy(tiltSensitivity = it)) }, valueRange = 10f..150f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean Square Internal Local Gallery Screen with Delete and Export buttons on each project card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalGalleryScreen(
    accentColor: Color,
    currentCustomImagePath: String?,
    onSelectWallpaper: (String?) -> Unit,
    onDeleteWallpaper: (LocalGalleryItem) -> Unit,
    onExportWallpaper: (String?) -> Unit,
    onAddCustomPhoto: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val items = remember {
        mutableStateListOf<LocalGalleryItem>().apply {
            val filesDir = context.filesDir
            val savedFiles = filesDir.listFiles { _, name -> name.startsWith("custom_wallpaper") }
            savedFiles?.forEachIndexed { index, file ->
                if (file.exists() && file.length() > 0) {
                    add(LocalGalleryItem(id = "custom_$index", title = "Wallpaper #${index + 1}", imagePath = file.absolutePath))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("< Back", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Grid of Wallpapers with Real Thumbnails, Prominent Import Tile, and Delete/Export Actions
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Prominent First Tile: Import Custom Photo
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .border(
                                width = 2.dp,
                                color = accentColor,
                                shape = RectangleShape
                            )
                            .clickable { onAddCustomPhoto() },
                        shape = RectangleShape,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = accentColor.copy(alpha = 0.08f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.import_photo),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                items(items) { item ->
                    val isSelected = (item.imagePath == currentCustomImagePath)

                    var thumbnailBitmap by remember(item.imagePath) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(item.imagePath) {
                        if (item.imagePath != null) {
                            withContext(Dispatchers.IO) {
                                val bmp = WallpaperBitmapLoader.loadBackground(
                                    context = context,
                                    targetWidth = 240,
                                    targetHeight = 420,
                                    customPath = item.imagePath
                                )
                                thumbnailBitmap = bmp.asImageBitmap()
                            }
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) accentColor else Color.LightGray,
                                shape = RectangleShape,
                            )
                            .clickable { onSelectWallpaper(item.imagePath) },
                        shape = RectangleShape,
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (thumbnailBitmap != null) {
                                Image(
                                    bitmap = thumbnailBitmap!!,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(accentColor)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Title & Delete/Export Actions Banner Overlay at bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        if (isSelected) {
                                            Text(
                                                text = stringResource(R.string.active_badge),
                                                color = accentColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    if (!item.isDefault) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Export button
                                            Text(
                                                text = stringResource(R.string.export_project),
                                                color = Color.Cyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = 0.15f), RectangleShape)
                                                    .clickable {
                                                        onExportWallpaper(item.imagePath)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                            // Delete button
                                            Text(
                                                text = stringResource(R.string.delete_project),
                                                color = Color.Red,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = 0.15f), RectangleShape)
                                                    .clickable {
                                                        onDeleteWallpaper(item)
                                                        items.remove(item)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * WelcomeScreen: Dashboard allowing user to import photos directly or open gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    hasExistingProject: Boolean,
    accentColor: Color,
    onOpenGallery: () -> Unit,
    onImportPhoto: () -> Unit,
    onResumeProject: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(accentColor),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LIQUIWALL Live Wallpaper",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create buttery-smooth, interactive jelly touch & tilt wallpapers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(40.dp))

            if (hasExistingProject) {
                Button(
                    onClick = onResumeProject,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                ) {
                    Text(text = stringResource(R.string.resume_project), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onImportPhoto,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                Text(text = "+ ${stringResource(R.string.import_photo)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenGallery,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RectangleShape,
            ) {
                Text(text = stringResource(R.string.open_gallery), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * JellyWallpaperEditorScreen:
 * Renders wallpaper mesh warp, ultra-smooth responsive lineTo drawing, real-time Eraser Brush,
 * and Double-Tap to Delete Object / Stroke on Canvas.
 */
@Composable
fun JellyWallpaperEditorScreen(
    wallpaperImageBitmap: ImageBitmap?,
    savedMaskBitmap: ImageBitmap?,
    meshPhysics: SpringMeshPhysics,
    parallaxEnabled: Boolean,
    strokes: MutableList<MaskDrawStroke>,
    brushSize: Float,
    toolMode: CanvasToolMode,
    autoFillCircle: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onStrokeStart: () -> Unit,
    onStrokeEnd: (MaskDrawStroke) -> Unit,
    onPhysicsDisturbance: (Float, Float, Float, Float) -> Unit,
) {
    val isEraserMode = (toolMode == CanvasToolMode.ERASE)

    val strokePoints = remember { mutableListOf<Offset>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151515))
            .onSizeChanged { onSizeChanged(it) }
            .pointerInput(toolMode, brushSize, autoFillCircle, parallaxEnabled) {
                coroutineScope {
                    launch {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                // Double-tap on object / stroke removes it
                                val hitIndex = strokes.indexOfLast { stroke ->
                                    val bounds = android.graphics.RectF()
                                    stroke.path.asAndroidPath().computeBounds(bounds, true)
                                    bounds.inset(-stroke.strokeWidth * 1.5f, -stroke.strokeWidth * 1.5f)
                                    bounds.contains(tapOffset.x, tapOffset.y)
                                }
                                if (hitIndex != -1) {
                                    onStrokeStart()
                                    strokes.removeAt(hitIndex)
                                    onStrokeEnd(MaskDrawStroke(Path(), 0f, false))
                                }
                            },
                            onTap = { tapOffset ->
                                if (parallaxEnabled) {
                                    onPhysicsDisturbance(tapOffset.x, tapOffset.y, 0f, 0f)
                                }
                            }
                        )
                    }
                    launch {
                        detectDragGestures(
                            onDragStart = { offset ->
                                strokePoints.clear()
                                strokePoints.add(offset)
                                currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                onStrokeStart()
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                strokePoints.add(change.position)
                                currentPath = currentPath?.let { p ->
                                    Path().apply {
                                        addPath(p)
                                        lineTo(change.position.x, change.position.y)
                                    }
                                }
                            },
                            onDragEnd = {
                                val path = currentPath
                                if (path != null) {
                                    val isClosed = !isEraserMode && autoFillCircle
                                            && CircleGestureDetector.isClosedLoop(strokePoints)
                                    val finalPath = if (isClosed) Path().apply { addPath(path); close() } else path
                                    onStrokeEnd(
                                        MaskDrawStroke(
                                            path = finalPath,
                                            strokeWidth = brushSize,
                                            isEraser = isEraserMode,
                                            isFilled = isClosed,
                                        )
                                    )
                                }
                                strokePoints.clear()
                                currentPath = null
                            },
                            onDragCancel = {
                                strokePoints.clear()
                                currentPath = null
                            },
                        )
                    }
                }
            },
    ) {
        wallpaperImageBitmap?.let { bmp ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
                nativeCanvas.drawBitmapMesh(
                    bmp.asAndroidBitmap(),
                    meshPhysics.cols,
                    meshPhysics.rows,
                    meshPhysics.currentVerts,
                    0,
                    null,
                    0,
                    paint,
                )
            }
        }

        // Live Real-Time Mask & Eraser Brush Overlay with offscreen saveLayer for 100% precision erasing
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            if (strokes.isNotEmpty() || currentPath != null) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val saveCount = nativeCanvas.saveLayer(0f, 0f, w, h, null)

                val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(90, 230, 30, 30)
                    style = AndroidPaint.Style.FILL
                }

                val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(190, 230, 30, 30)
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                }

                val erasePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.TRANSPARENT
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }

                for (stroke in strokes) {
                    val nativePath = stroke.path.asAndroidPath()
                    val paintToUse = if (stroke.isEraser) erasePaint else strokePaint
                    paintToUse.strokeWidth = stroke.strokeWidth

                    if (!stroke.isEraser && stroke.isFilled) {
                        nativeCanvas.drawPath(nativePath, fillPaint)
                    }
                    nativeCanvas.drawPath(nativePath, paintToUse)
                }

                currentPath?.let { inProgress ->
                    val nativePath = inProgress.asAndroidPath()
                    val paintToUse = if (isEraserMode) erasePaint else strokePaint
                    paintToUse.strokeWidth = brushSize
                    nativeCanvas.drawPath(nativePath, paintToUse)
                }

                nativeCanvas.restoreToCount(saveCount)
            } else if (savedMaskBitmap != null) {
                // Persistent display of saved mask bitmap when strokes list is empty on launch
                val tintPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(
                        android.graphics.Color.argb(180, 230, 30, 30),
                        PorterDuff.Mode.SRC_IN
                    )
                }
                drawContext.canvas.nativeCanvas.drawBitmap(
                    savedMaskBitmap.asAndroidBitmap(),
                    0f,
                    0f,
                    tintPaint
                )
            }
        }
    }
}

fun updateMeshWeightsFromMask(mesh: SpringMeshPhysics, mask: Bitmap) {
    val numCols = mesh.cols
    val numRows = mesh.rows
    var idx = 0
    for (r in 0..numRows) {
        for (c in 0..numCols) {
            val vIdx = idx / 2
            val vx = mesh.origVerts[idx]
            val vy = mesh.origVerts[idx + 1]

            val px = vx.toInt().coerceIn(0, mask.width - 1)
            val py = vy.toInt().coerceIn(0, mask.height - 1)
            val pixel = mask.getPixel(px, py)
            val weight = android.graphics.Color.alpha(pixel) / 255.0f
            mesh.setWeightAt(vIdx, weight)
            idx += 2
        }
    }
    mesh.smoothWeights()
    mesh.smoothWeights()
}

/**
 * Creates 1:1 mathematically synchronized mask bitmap scaled to target background resolution!
 */
fun createMaskBitmapFromStrokes(
    targetWidth: Int,
    targetHeight: Int,
    previewWidth: Int,
    previewHeight: Int,
    strokes: List<MaskDrawStroke>,
): Bitmap? {
    if (targetWidth <= 0 || targetHeight <= 0 || previewWidth <= 0 || previewHeight <= 0 || strokes.isEmpty()) return null

    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val scaleX = targetWidth.toFloat() / previewWidth.toFloat()
    val scaleY = targetHeight.toFloat() / previewHeight.toFloat()
    val matrix = AndroidMatrix().apply {
        postScale(scaleX, scaleY)
    }

    val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = AndroidPaint.Style.FILL
    }

    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    val erasePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.TRANSPARENT
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    for (s in strokes) {
        val nativePath = AndroidPath(s.path.asAndroidPath())
        nativePath.transform(matrix) // 1:1 exact mathematical coordinate transformation!

        val paintToUse = if (s.isEraser) erasePaint else strokePaint
        paintToUse.strokeWidth = s.strokeWidth * scaleX

        if (!s.isEraser && s.isFilled) {
            canvas.drawPath(nativePath, fillPaint)
        }
        canvas.drawPath(nativePath, paintToUse)
    }

    return bitmap
}

fun launchLiveWallpaperChooser(context: Context) {
    try {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, DropWallWallpaperService::class.java),
            )
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: Exception) {
            // Not supported
        }
    }
}
