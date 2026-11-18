package com.nudge.edge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.nudge.edge.model.NudgeMenuApp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewArray
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.edge.model.PersistentNudgeAction
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val HousingBlack = Color(0xFF000000)

fun getIconForAction(action: PersistentNudgeAction): ImageVector {
    return when (action) {
        PersistentNudgeAction.VOLUME_SLIDER_HUD -> Icons.Rounded.GraphicEq
        PersistentNudgeAction.AUDIO_MIXER -> Icons.Rounded.Tune
        PersistentNudgeAction.VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
        PersistentNudgeAction.VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
        PersistentNudgeAction.DND_PANEL -> Icons.Rounded.Bedtime
        PersistentNudgeAction.TOGGLE_DND -> Icons.Rounded.DoNotDisturbOn
        PersistentNudgeAction.TOGGLE_SILENT_RING -> Icons.Rounded.NotificationsOff
        PersistentNudgeAction.ACTIONS_PANEL -> Icons.Rounded.Dashboard
        PersistentNudgeAction.NUDGE_MENU -> Icons.Rounded.Menu
        PersistentNudgeAction.NOTIFICATIONS -> Icons.Rounded.Notifications
        PersistentNudgeAction.QUICK_PANEL -> Icons.Rounded.Widgets
        PersistentNudgeAction.TAKE_SCREENSHOT -> Icons.Rounded.CropFree
        PersistentNudgeAction.LOCK_SCREEN -> Icons.Rounded.Lock
        PersistentNudgeAction.POWER_MENU -> Icons.Rounded.PowerSettingsNew
        PersistentNudgeAction.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        PersistentNudgeAction.HOME -> Icons.Rounded.Home
        PersistentNudgeAction.RECENTS -> Icons.Rounded.ViewArray
        PersistentNudgeAction.VOICE_ASSISTANT -> Icons.Rounded.Mic
        PersistentNudgeAction.MEDIA_PLAY_PAUSE -> Icons.Rounded.PlayArrow
        PersistentNudgeAction.MEDIA_NEXT -> Icons.Rounded.SkipNext
        PersistentNudgeAction.MEDIA_PREVIOUS -> Icons.Rounded.SkipPrevious
        PersistentNudgeAction.TOGGLE_FLASHLIGHT -> Icons.Rounded.FlashlightOn
        PersistentNudgeAction.TOGGLE_AUTO_ROTATE -> Icons.Rounded.ScreenRotation
        PersistentNudgeAction.OPEN_CAMERA -> Icons.Rounded.CameraAlt
        PersistentNudgeAction.APP_SWITCH -> Icons.Rounded.SwapHoriz
        PersistentNudgeAction.OPEN_APP -> Icons.Rounded.Apps
        PersistentNudgeAction.SHOW_SYSTEM_VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    }
}

fun getShortLabelForAction(action: PersistentNudgeAction): String {
    return when (action) {
        PersistentNudgeAction.VOLUME_SLIDER_HUD -> "Slider HUD"
        PersistentNudgeAction.AUDIO_MIXER -> "Mixer"
        PersistentNudgeAction.VOLUME_UP -> "Vol Up"
        PersistentNudgeAction.VOLUME_DOWN -> "Vol Down"
        PersistentNudgeAction.DND_PANEL -> "DND Panel"
        PersistentNudgeAction.TOGGLE_DND -> "Toggle DND"
        PersistentNudgeAction.TOGGLE_SILENT_RING -> "Silent/Ring"
        PersistentNudgeAction.ACTIONS_PANEL -> "Actions"
        PersistentNudgeAction.NUDGE_MENU -> "Menu"
        PersistentNudgeAction.NOTIFICATIONS -> "Notifications"
        PersistentNudgeAction.QUICK_PANEL -> "Quick Panel"
        PersistentNudgeAction.TAKE_SCREENSHOT -> "Screenshot"
        PersistentNudgeAction.LOCK_SCREEN -> "Lock"
        PersistentNudgeAction.POWER_MENU -> "Power"
        PersistentNudgeAction.BACK -> "Back"
        PersistentNudgeAction.HOME -> "Home"
        PersistentNudgeAction.RECENTS -> "Recents"
        PersistentNudgeAction.VOICE_ASSISTANT -> "Assistant"
        PersistentNudgeAction.MEDIA_PLAY_PAUSE -> "Play/Pause"
        PersistentNudgeAction.MEDIA_NEXT -> "Next"
        PersistentNudgeAction.MEDIA_PREVIOUS -> "Prev"
        PersistentNudgeAction.TOGGLE_FLASHLIGHT -> "Flashlight"
        PersistentNudgeAction.TOGGLE_AUTO_ROTATE -> "Rotate"
        PersistentNudgeAction.OPEN_CAMERA -> "Camera"
        PersistentNudgeAction.APP_SWITCH -> "Last App"
        PersistentNudgeAction.OPEN_APP -> "Open App"
        PersistentNudgeAction.SHOW_SYSTEM_VOLUME -> "System Slider"
    }
}

sealed class NudgeMenuItem {
    abstract val label: String
    abstract val action: PersistentNudgeAction
    abstract val targetPackage: String

    data class ActionItem(override val action: PersistentNudgeAction) : NudgeMenuItem() {
        override val label: String get() = getShortLabelForAction(action)
        override val targetPackage: String get() = ""
    }

    data class AppItem(val packageName: String, val appName: String) : NudgeMenuItem() {
        override val label: String get() = appName
        override val action: PersistentNudgeAction get() = PersistentNudgeAction.OPEN_APP
        override val targetPackage: String get() = packageName
    }
}

@Composable
private fun NudgeMenuItemIcon(
    item: NudgeMenuItem,
    sizeDp: Dp,
    modifier: Modifier = Modifier
) {
    when (item) {
        is NudgeMenuItem.ActionItem -> {
            Icon(
                imageVector = getIconForAction(item.action),
                contentDescription = item.action.displayName,
                tint = Color.White,
                modifier = modifier.requiredSize(sizeDp)
            )
        }
        is NudgeMenuItem.AppItem -> {
            val context = LocalContext.current
            val appIconBitmap = remember(item.packageName) {
                runCatching {
                    context.packageManager.getApplicationIcon(item.packageName).toBitmap(64, 64).asImageBitmap()
                }.getOrNull()
            }
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = item.appName,
                    modifier = modifier
                        .requiredSize(sizeDp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = item.appName,
                    tint = Color.White,
                    modifier = modifier.requiredSize(sizeDp)
                )
            }
        }
    }
}

fun buildNudgeMenuBezelPath(
    path: Path,
    width: Float,
    height: Float,
    screenEdge: ScreenEdge
) {
    buildOrganicBezelPath(
        path = path,
        width = width,
        height = height,
        screenEdge = screenEdge
    )
}

@Composable
fun NudgeMenuOverlay(
    screenEdge: ScreenEdge,
    overlayWidthDp: Float = 50f,
    overlayHeightDp: Float = 300f,
    isVisible: Boolean,
    nudgeMenuApps: List<NudgeMenuApp> = emptyList(),
    onlyApps: Boolean = false,
    volumeStepTrigger: Int = 0,
    onActionClicked: (PersistentNudgeAction, String) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val menuItems = remember(nudgeMenuApps, onlyApps) {
        if (onlyApps) {
            nudgeMenuApps.map { NudgeMenuItem.AppItem(it.packageName, it.appName) }
        } else {
            val baseActions = PersistentNudgeAction.entries.filter {
                it != PersistentNudgeAction.NUDGE_MENU && it != PersistentNudgeAction.OPEN_APP
            }
            baseActions.map { NudgeMenuItem.ActionItem(it) }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val wheelScrollPosition = remember { Animatable(0f) }
    var isFingerTouchingPanel by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val isHorizontalBezel = screenEdge == ScreenEdge.TOP || screenEdge == ScreenEdge.BOTTOM
    val containerWidthDp = if (isHorizontalBezel) overlayHeightDp else overlayWidthDp
    val containerHeightDp = if (isHorizontalBezel) overlayWidthDp else overlayHeightDp

    val defaultMaxIconSizeDp = 22.dp
    val maxAllowedByWidthDp = (overlayWidthDp.dp - 14.dp).coerceIn(12.dp, 22.dp)
    val effectiveIconSizeDp = minOf(defaultMaxIconSizeDp, maxAllowedByWidthDp)

    val iconSpacingPx = with(density) { 52.dp.toPx() }
    val totalLengthPx = with(density) { (if (isHorizontalBezel) containerWidthDp else containerHeightDp).dp.toPx() }
    val maxSlotsBySpace = ((totalLengthPx / iconSpacingPx).toInt() + 2).coerceAtMost(25)
    val totalVisibleSlots = minOf(25, maxSlotsBySpace)
    val halfSpan = totalVisibleSlots / 2
    val maxDistance = (halfSpan + 0.5f).coerceAtLeast(2.8f)

    val slideAnim = remember { Animatable(if (isVisible) 1f else 0f) }
    var lastVolumeStep by remember { mutableStateOf(volumeStepTrigger) }
    LaunchedEffect(volumeStepTrigger) {
        val diff = volumeStepTrigger - lastVolumeStep
        lastVolumeStep = volumeStepTrigger
        if (diff != 0 && menuItems.isNotEmpty()) {
            val targetVirtualIndex = (wheelScrollPosition.value + diff).roundToInt()
            wheelScrollPosition.animateTo(
                targetValue = targetVirtualIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            slideAnim.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 320f
                )
            )
        } else {
            slideAnim.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 380f
                )
            )
        }
    }

    if (slideAnim.value <= 0.001f && !isVisible) {
        return
    }

    val animProgress = slideAnim.value.coerceIn(0f, 1f)
    val centerVirtualIndex = wheelScrollPosition.value.roundToInt()
    val centerActionIndex = Math.floorMod(centerVirtualIndex, menuItems.size)
    val currentItem = menuItems[centerActionIndex]

    val bezelPath = remember { Path() }
    val bezelShape = remember(containerWidthDp, containerHeightDp, screenEdge) {
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                buildOrganicBezelPath(
                    path = bezelPath,
                    width = size.width,
                    height = size.height,
                    screenEdge = screenEdge
                )
                return Outline.Generic(bezelPath)
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            }
            .graphicsLayer {
                val shiftBreadth = with(density) { overlayWidthDp.dp.toPx() }
                when (screenEdge) {
                    ScreenEdge.LEFT -> {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationX = (-shiftBreadth * (1f - animProgress)).coerceAtMost(0f)
                    }
                    ScreenEdge.RIGHT -> {
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        translationX = (shiftBreadth * (1f - animProgress)).coerceAtLeast(0f)
                    }
                    ScreenEdge.TOP -> {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = (-shiftBreadth * (1f - animProgress)).coerceAtMost(0f)
                    }
                    ScreenEdge.BOTTOM -> {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (shiftBreadth * (1f - animProgress)).coerceAtLeast(0f)
                    }
                }
                alpha = animProgress
            },
        contentAlignment = when (screenEdge) {
            ScreenEdge.LEFT -> Alignment.CenterStart
            ScreenEdge.RIGHT -> Alignment.CenterEnd
            ScreenEdge.TOP -> Alignment.TopCenter
            ScreenEdge.BOTTOM -> Alignment.BottomCenter
        }
    ) {
        if (!isHorizontalBezel) {
            val isRight = screenEdge == ScreenEdge.RIGHT
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRight) {
                    NudgeMenuTextLabel(
                        text = currentItem.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = true,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(end = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onActionClicked(currentItem.action, currentItem.targetPackage)
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .requiredWidth(containerWidthDp.dp)
                        .requiredHeight(containerHeightDp.dp)
                        .clipToBounds()
                        .pointerInput(menuItems.size) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    val downPos = down.position
                                    var lastPos = downPos
                                    var totalDrag = 0f
                                    var isDrag = false
                                    val touchSlop = viewConfiguration.touchSlop
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val dx = change.position.x - downPos.x
                                            val dismissSwipe = (screenEdge == ScreenEdge.RIGHT && dx > touchSlop * 2) ||
                                                (screenEdge == ScreenEdge.LEFT && dx < -touchSlop * 2)
                                            if (dismissSwipe) {
                                                change.consume()
                                                onDismiss()
                                                break
                                            }
                                            val delta = change.position.y - lastPos.y
                                            lastPos = change.position
                                            totalDrag += abs(delta)
                                            if (totalDrag > touchSlop) {
                                                isDrag = true
                                            }
                                            if (isDrag && delta != 0f) {
                                                change.consume()
                                                launch {
                                                    val stepDelta = delta / iconSpacingPx
                                                    wheelScrollPosition.snapTo(wheelScrollPosition.value - stepDelta)
                                                }
                                            }
                                        }
                                    } finally {
                                        if (!isDrag) {
                                            val centerAxis = size.height / 2f
                                            val currentScroll = wheelScrollPosition.value
                                            val tappedVirtualIndex = (currentScroll + ((downPos.y - centerAxis) / iconSpacingPx)).roundToInt()
                                            val actualIdx = Math.floorMod(tappedVirtualIndex, menuItems.size)
                                            val tappedItem = menuItems[actualIdx]
                                            launch {
                                                wheelScrollPosition.animateTo(
                                                    targetValue = tappedVirtualIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                                onActionClicked(tappedItem.action, tappedItem.targetPackage)
                                            }
                                        } else {
                                            val targetIndex = wheelScrollPosition.value.roundToInt()
                                            launch {
                                                try {
                                                    wheelScrollPosition.animateTo(
                                                        targetValue = targetIndex.toFloat(),
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                } finally {
                                                    isFingerTouchingPanel = false
                                                }
                                            }
                                        }
                                        isFingerTouchingPanel = false
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        buildOrganicBezelPath(
                            path = bezelPath,
                            width = size.width,
                            height = size.height,
                            screenEdge = screenEdge
                        )
                        drawPath(path = bezelPath, color = HousingBlack)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(bezelShape)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val maskBrush = Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.90f to Color.Black,
                                    1.00f to Color.Transparent,
                                    startY = 0f,
                                    endY = size.height
                                )
                                drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        for (offset in -halfSpan..halfSpan) {
                            val virtualIndex = centerVirtualIndex + offset
                            val actualIndex = Math.floorMod(virtualIndex, menuItems.size)
                            val item = menuItems[actualIndex]
                            val fractionalDistance = virtualIndex - wheelScrollPosition.value
                            val distance = abs(fractionalDistance)
                            if (distance <= maxDistance) {
                                val alpha = when {
                                    distance <= 1f -> 1.0f - distance * 0.22f
                                    distance <= 2f -> 0.78f - (distance - 1f) * 0.33f
                                    else -> (0.45f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.15f).coerceAtLeast(0.30f)
                                }
                                val yOffsetPx = fractionalDistance * iconSpacingPx

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, yOffsetPx.roundToInt()) }
                                        .alpha(alpha)
                                        .requiredSize(effectiveIconSizeDp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NudgeMenuItemIcon(
                                        item = item,
                                        sizeDp = effectiveIconSizeDp
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isRight) {
                    NudgeMenuTextLabel(
                        text = currentItem.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = true,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(start = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onActionClicked(currentItem.action, currentItem.targetPackage)
                            }
                    )
                }
            }
        } else {
            val isBottom = screenEdge == ScreenEdge.BOTTOM
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBottom) {
                    NudgeMenuTextLabel(
                        text = currentItem.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(bottom = 10.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onActionClicked(currentItem.action, currentItem.targetPackage)
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .requiredWidth(containerWidthDp.dp)
                        .requiredHeight(containerHeightDp.dp)
                        .clipToBounds()
                        .pointerInput(menuItems.size) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    val downPos = down.position
                                    var lastPos = downPos
                                    var totalDrag = 0f
                                    var isDrag = false
                                    val touchSlop = viewConfiguration.touchSlop
                                    try {
                                        while (true) {
                                             val event = awaitPointerEvent()
                                             val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                             if (!change.pressed) break
                                             val dy = change.position.y - downPos.y
                                             val dismissSwipe = (screenEdge == ScreenEdge.BOTTOM && dy > touchSlop * 2) ||
                                                 (screenEdge == ScreenEdge.TOP && dy < -touchSlop * 2)
                                             if (dismissSwipe) {
                                                 change.consume()
                                                 onDismiss()
                                                 break
                                             }
                                             val delta = change.position.x - lastPos.x
                                             lastPos = change.position
                                             totalDrag += abs(delta)
                                             if (totalDrag > touchSlop) {
                                                 isDrag = true
                                             }
                                             if (isDrag && delta != 0f) {
                                                 change.consume()
                                                 launch {
                                                     val stepDelta = delta / iconSpacingPx
                                                     wheelScrollPosition.snapTo(wheelScrollPosition.value - stepDelta)
                                                 }
                                             }
                                        }
                                    } finally {
                                        if (!isDrag) {
                                            val centerAxis = size.width / 2f
                                            val currentScroll = wheelScrollPosition.value
                                            val tappedVirtualIndex = (currentScroll + ((downPos.x - centerAxis) / iconSpacingPx)).roundToInt()
                                            val actualIdx = Math.floorMod(tappedVirtualIndex, menuItems.size)
                                            val tappedItem = menuItems[actualIdx]
                                            launch {
                                                wheelScrollPosition.animateTo(
                                                    targetValue = tappedVirtualIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                                onActionClicked(tappedItem.action, tappedItem.targetPackage)
                                            }
                                        } else {
                                            val targetIndex = wheelScrollPosition.value.roundToInt()
                                            launch {
                                                try {
                                                    wheelScrollPosition.animateTo(
                                                        targetValue = targetIndex.toFloat(),
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                } finally {
                                                    isFingerTouchingPanel = false
                                                }
                                            }
                                        }
                                        isFingerTouchingPanel = false
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        buildOrganicBezelPath(
                            path = bezelPath,
                            width = size.width,
                            height = size.height,
                            screenEdge = screenEdge
                        )
                        drawPath(path = bezelPath, color = HousingBlack)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(bezelShape)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val maskBrush = Brush.horizontalGradient(
                                    0.00f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.90f to Color.Black,
                                    1.00f to Color.Transparent,
                                    startX = 0f,
                                    endX = size.width
                                )
                                drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        for (offset in -halfSpan..halfSpan) {
                            val virtualIndex = centerVirtualIndex + offset
                            val actualIndex = Math.floorMod(virtualIndex, menuItems.size)
                            val item = menuItems[actualIndex]
                            val fractionalDistance = virtualIndex - wheelScrollPosition.value
                            val distance = abs(fractionalDistance)
                            if (distance <= maxDistance) {
                                val alpha = when {
                                    distance <= 1f -> 1.0f - distance * 0.22f
                                    distance <= 2f -> 0.78f - (distance - 1f) * 0.33f
                                    else -> (0.45f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.15f).coerceAtLeast(0.30f)
                                }
                                val xOffsetPx = fractionalDistance * iconSpacingPx

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(xOffsetPx.roundToInt(), 0) }
                                        .alpha(alpha)
                                        .requiredSize(effectiveIconSizeDp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NudgeMenuItemIcon(
                                        item = item,
                                        sizeDp = effectiveIconSizeDp
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isBottom) {
                    NudgeMenuTextLabel(
                        text = currentItem.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(top = 10.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onActionClicked(currentItem.action, currentItem.targetPackage)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun NudgeMenuTextLabel(
    text: String,
    isVisible: Boolean,
    isVerticalEdge: Boolean,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = if (isVerticalEdge) {
            fadeIn(animationSpec = tween(150)) + slideInHorizontally(
                initialOffsetX = { it / 3 },
                animationSpec = tween(180)
            )
        } else {
            fadeIn(animationSpec = tween(150)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(180)
            )
        },
        exit = if (isVerticalEdge) {
            fadeOut(animationSpec = tween(100)) + slideOutHorizontally(
                targetOffsetX = { it / 3 },
                animationSpec = tween(120)
            )
        } else {
            fadeOut(animationSpec = tween(100)) + slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(120)
            )
        },
        modifier = modifier
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            style = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.65f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f
                )
            )
        )
    }
}