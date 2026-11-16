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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.edge.model.DndModeType
import com.nudge.edge.model.NudgeIcons
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HousingBlack = Color(0xFF000000)
private val ActiveSilentRed = Color(0xFFFF453A)
private val ActiveRingWhite = Color(0xFFFFFFFF)
private val MutedGray = Color(0xFF6E6E73)

const val DND_BEZEL_FILLET_DP = 34f

fun computeTailSpanDp(fluidityPercent: Int? = null): Float {
    return DND_BEZEL_FILLET_DP
}

fun computeDndOuterSpanDp(lengthDp: Float, fluidityPercent: Int? = null): Float {
    return lengthDp + DND_BEZEL_FILLET_DP * 2f
}

fun buildCleanBellPath(s: Float): Path {
    val path = Path()
    path.moveTo(12.0f * s, 4.2f * s)
    path.cubicTo(11.4f * s, 4.2f * s, 11.0f * s, 4.6f * s, 11.0f * s, 5.1f * s)
    path.cubicTo(8.0f * s, 6.0f * s, 6.5f * s, 8.5f * s, 6.5f * s, 12.0f * s)
    path.lineTo(6.5f * s, 15.0f * s)
    path.cubicTo(6.5f * s, 16.5f * s, 4.8f * s, 17.5f * s, 4.8f * s, 18.0f * s)
    path.lineTo(19.2f * s, 18.0f * s)
    path.cubicTo(19.2f * s, 17.5f * s, 17.5f * s, 16.5f * s, 17.5f * s, 15.0f * s)
    path.lineTo(17.5f * s, 12.0f * s)
    path.cubicTo(17.5f * s, 8.5f * s, 16.0f * s, 6.0f * s, 13.0f * s, 5.1f * s)
    path.cubicTo(13.0f * s, 4.6f * s, 12.6f * s, 4.2f * s, 12.0f * s, 4.2f * s)
    path.close()
    return path
}

@Composable
fun CleanSlashedBellIcon(
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val s = size.minDimension / 24f
        drawPath(path = buildCleanBellPath(s), color = iconTint)
        drawArc(
            color = iconTint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(10.0f * s, 18.5f * s),
            size = Size(4.0f * s, 4.0f * s)
        )
        drawLine(
            color = Color.Transparent,
            start = Offset(3.5f * s, 3.5f * s),
            end = Offset(20.5f * s, 20.5f * s),
            strokeWidth = 3.8f * s,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Clear
        )
        drawLine(
            color = iconTint,
            start = Offset(3.5f * s, 3.5f * s),
            end = Offset(20.5f * s, 20.5f * s),
            strokeWidth = 2.0f * s,
            cap = StrokeCap.Round
        )
    }
}

fun buildOrganicBezelPath(
    path: Path,
    width: Float,
    height: Float,
    screenEdge: ScreenEdge,
    minCurveZonePx: Float = 0f,
    maxCurveZonePx: Float = Float.MAX_VALUE,
    curvaturePercent: Int? = null
) {
    path.reset()
    if (width <= 0f || height <= 0f) return

    when (screenEdge) {
        ScreenEdge.LEFT -> {
            val curveZone = (height * 0.30f).coerceIn(minCurveZonePx, maxCurveZonePx).coerceAtMost(height * 0.45f)
            path.moveTo(0f, 0f)
            path.cubicTo(
                x1 = 0f,         y1 = curveZone * 0.55f,
                x2 = width,      y2 = curveZone * 0.45f,
                x3 = width,      y3 = curveZone
            )
            path.lineTo(width, height - curveZone)
            path.cubicTo(
                x1 = width,      y1 = height - (curveZone * 0.45f),
                x2 = 0f,         y2 = height - (curveZone * 0.55f),
                x3 = 0f,         y3 = height
            )
            path.close()
        }
        ScreenEdge.RIGHT -> {
            val curveZone = (height * 0.30f).coerceIn(minCurveZonePx, maxCurveZonePx).coerceAtMost(height * 0.45f)
            path.moveTo(width, 0f)
            path.cubicTo(
                x1 = width,      y1 = curveZone * 0.55f,
                x2 = 0f,         y2 = curveZone * 0.45f,
                x3 = 0f,         y3 = curveZone
            )
            path.lineTo(0f, height - curveZone)
            path.cubicTo(
                x1 = 0f,         y1 = height - (curveZone * 0.45f),
                x2 = width,      y2 = height - (curveZone * 0.55f),
                x3 = width,      y3 = height
            )
            path.close()
        }
        ScreenEdge.TOP -> {
            val curveZone = (width * 0.30f).coerceIn(minCurveZonePx, maxCurveZonePx).coerceAtMost(width * 0.45f)
            path.moveTo(0f, 0f)
            path.cubicTo(
                x1 = curveZone * 0.55f, y1 = 0f,
                x2 = curveZone * 0.45f, y2 = height,
                x3 = curveZone,         y3 = height
            )
            path.lineTo(width - curveZone, height)
            path.cubicTo(
                x1 = width - (curveZone * 0.45f), y1 = height,
                x2 = width - (curveZone * 0.55f), y2 = 0f,
                x3 = width,                       y3 = 0f
            )
            path.close()
        }
        ScreenEdge.BOTTOM -> {
            val curveZone = (width * 0.30f).coerceIn(minCurveZonePx, maxCurveZonePx).coerceAtMost(width * 0.45f)
            path.moveTo(0f, height)
            path.cubicTo(
                x1 = curveZone * 0.55f, y1 = height,
                x2 = curveZone * 0.45f, y2 = 0f,
                x3 = curveZone,         y3 = 0f
            )
            path.lineTo(width - curveZone, 0f)
            path.cubicTo(
                x1 = width - (curveZone * 0.45f), y1 = 0f,
                x2 = width - (curveZone * 0.55f), y2 = height,
                x3 = width,                       y3 = height
            )
            path.close()
        }
    }
}

@Composable
fun DndSilentBezelOverlay(
    screenEdge: ScreenEdge,
    overlayWidthDp: Float,
    overlayHeightDp: Float,
    modeType: DndModeType,
    isSilentOrDndActive: Boolean,
    isVisible: Boolean,
    onSelectState: (Boolean) -> Unit = {},
    onPauseAutoHideTimer: () -> Unit = {},
    onRestartAutoHideTimer: () -> Unit = {},
    iconSizeDp: Float = 24f,
    unfocusedOffsetDp: Float = 38f,
    fluidityPercent: Int = 80,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val totalSpanDp = computeDndOuterSpanDp(overlayHeightDp)
    val protrusionDp = overlayWidthDp

    val isVerticalEdge = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
    val widthDp = if (isVerticalEdge) protrusionDp else totalSpanDp
    val heightDp = if (isVerticalEdge) totalSpanDp else protrusionDp

    val panelThicknessDp = if (isVerticalEdge) overlayWidthDp else overlayHeightDp
    val maxAllowedByWidthDp = (panelThicknessDp.dp - 6.dp).coerceAtLeast(10.dp)
    val effectiveIconSizeDp = minOf(24.dp, maxAllowedByWidthDp)

    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val minCurveZonePx = with(density) { 16.dp.toPx() }.coerceAtMost(if (isVerticalEdge) heightPx * 0.35f else widthPx * 0.35f)
    val maxCurveZonePx = with(density) { 56.dp.toPx() }

    val slideAnim = remember { Animatable(if (isVisible) 1f else 0f) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            slideAnim.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            slideAnim.animateTo(
                0f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        }
    }

    if (slideAnim.value <= 0.001f && !isVisible) {
        return
    }

    val focusProgress = remember { Animatable(if (isSilentOrDndActive) 0f else 1f) }
    var isFingerTouchingPanel by remember { mutableStateOf(false) }
    val isPanelBusy = isFingerTouchingPanel || focusProgress.isRunning

    LaunchedEffect(isPanelBusy) {
        if (isPanelBusy) {
            onPauseAutoHideTimer()
        } else {
            onRestartAutoHideTimer()
        }
    }

    LaunchedEffect(isSilentOrDndActive) {
        val target = if (isSilentOrDndActive) 0f else 1f
        if (focusProgress.targetValue != target) {
            focusProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val animatedFocus = focusProgress.value.coerceIn(0f, 1f)
    val centerCoord = if (isVerticalEdge) heightPx / 2f else widthPx / 2f
    val maxSlotSpacing = (if (isVerticalEdge) heightPx else widthPx) * 0.36f
    val slotSpacing = with(density) { unfocusedOffsetDp.dp.toPx() }.coerceAtMost(maxSlotSpacing)

    val slot0Coord = centerCoord + ((0f - animatedFocus) * slotSpacing)
    val slot1Coord = centerCoord + ((1f - animatedFocus) * slotSpacing)

    val activeSlot0Color = if (modeType == DndModeType.DND_ON_OFF) Color(0xFF5E5CE6) else ActiveSilentRed
    val slot0Color = activeSlot0Color
    val slot0Scale = 0.82f + 0.18f * (1f - animatedFocus)
    val slot0Alpha = 0.45f + 0.55f * (1f - animatedFocus)

    val slot1Scale = 0.82f + 0.18f * animatedFocus
    val slot1Alpha = 0.45f + 0.55f * animatedFocus
    val slot1Color = Color.White

    val labelText = when (modeType) {
        DndModeType.SILENT_RING -> if (isSilentOrDndActive) "SILENT" else "RING"
        DndModeType.DND_ON_OFF -> if (isSilentOrDndActive) "DND ON" else "DND OFF"
    }

    val housingPath = remember { Path() }
    val animProgress = slideAnim.value.coerceIn(0f, 1f)

    val bezelShape = remember(widthPx, heightPx, screenEdge, minCurveZonePx, maxCurveZonePx) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val p = Path()
                buildOrganicBezelPath(
                    path = p,
                    width = size.width,
                    height = size.height,
                    screenEdge = screenEdge,
                    minCurveZonePx = minCurveZonePx,
                    maxCurveZonePx = maxCurveZonePx
                )
                return Outline.Generic(p)
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                when (screenEdge) {
                    ScreenEdge.LEFT -> {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationX = (-widthPx * (1f - animProgress)).coerceAtMost(0f)
                    }
                    ScreenEdge.RIGHT -> {
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        translationX = (widthPx * (1f - animProgress)).coerceAtLeast(0f)
                    }
                    ScreenEdge.TOP -> {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = (-heightPx * (1f - animProgress)).coerceAtMost(0f)
                    }
                    ScreenEdge.BOTTOM -> {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = (heightPx * (1f - animProgress)).coerceAtLeast(0f)
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
        val iconHalfBoxPx = with(density) { (effectiveIconSizeDp.value / 2f + 4f).dp.toPx() }

        if (isVerticalEdge) {
            val isRight = screenEdge == ScreenEdge.RIGHT
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRight && showLabel) {
                    TextLabel(
                        text = labelText,
                        isVisible = isVisible,
                        isVerticalEdge = true,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                        .pointerInput(Unit) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    onPauseAutoHideTimer()
                                    try {
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val delta = change.position.y - lastPos.y
                                            lastPos = change.position
                                            if (delta != 0f) {
                                                change.consume()
                                                launch {
                                                    val stepDelta = -delta / slotSpacing
                                                    focusProgress.snapTo(
                                                        (focusProgress.value + stepDelta).coerceIn(0f, 1f)
                                                    )
                                                }
                                            }
                                        }
                                    } finally {
                                        val targetSlot = if (focusProgress.value < 0.5f) 0 else 1
                                        val newState = (targetSlot == 0)
                                        if (newState != isSilentOrDndActive) {
                                            onSelectState(newState)
                                        }
                                        launch {
                                            try {
                                                focusProgress.animateTo(
                                                    targetValue = targetSlot.toFloat(),
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
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        buildOrganicBezelPath(
                            path = housingPath,
                            width = size.width,
                            height = size.height,
                            screenEdge = screenEdge,
                            minCurveZonePx = minCurveZonePx,
                            maxCurveZonePx = maxCurveZonePx
                        )
                        drawPath(housingPath, HousingBlack)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(bezelShape)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val fadeFraction = 0.16f
                                val maskBrush = Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    fadeFraction to Color.Black,
                                    (1f - fadeFraction) to Color.Black,
                                    1.00f to Color.Transparent,
                                    startY = 0f,
                                    endY = size.height
                                )
                                drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, (slot0Coord - iconHalfBoxPx).roundToInt()) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectState(true)
                                    coroutineScope.launch {
                                        focusProgress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (modeType == DndModeType.DND_ON_OFF) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = slot0Color,
                                modifier = Modifier
                                    .scale(slot0Scale)
                                    .alpha(slot0Alpha)
                                    .size(effectiveIconSizeDp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, (slot1Coord - iconHalfBoxPx).roundToInt()) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectState(false)
                                    coroutineScope.launch {
                                        focusProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = slot1Color,
                                modifier = Modifier
                                    .scale(slot1Scale)
                                    .alpha(slot1Alpha)
                                    .size(effectiveIconSizeDp)
                            )
                        }
                    }
                }

                if (!isRight && showLabel) {
                    TextLabel(
                        text = labelText,
                        isVisible = isVisible,
                        isVerticalEdge = true,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        } else {
            val isBottom = screenEdge == ScreenEdge.BOTTOM
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isBottom && showLabel) {
                    TextLabel(
                        text = labelText,
                        isVisible = isVisible,
                        isVerticalEdge = false,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                        .pointerInput(Unit) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    onPauseAutoHideTimer()
                                    try {
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val delta = change.position.x - lastPos.x
                                            lastPos = change.position
                                            if (delta != 0f) {
                                                change.consume()
                                                launch {
                                                    val stepDelta = -delta / slotSpacing
                                                    focusProgress.snapTo(
                                                        (focusProgress.value + stepDelta).coerceIn(0f, 1f)
                                                    )
                                                }
                                            }
                                        }
                                    } finally {
                                        val targetSlot = if (focusProgress.value < 0.5f) 0 else 1
                                        val newState = (targetSlot == 0)
                                        if (newState != isSilentOrDndActive) {
                                            onSelectState(newState)
                                        }
                                        launch {
                                            try {
                                                focusProgress.animateTo(
                                                    targetValue = targetSlot.toFloat(),
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
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        buildOrganicBezelPath(
                            path = housingPath,
                            width = size.width,
                            height = size.height,
                            screenEdge = screenEdge,
                            minCurveZonePx = minCurveZonePx,
                            maxCurveZonePx = maxCurveZonePx
                        )
                        drawPath(housingPath, HousingBlack)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(bezelShape)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val fadeFraction = 0.16f
                                val maskBrush = Brush.horizontalGradient(
                                    0.00f to Color.Transparent,
                                    fadeFraction to Color.Black,
                                    (1f - fadeFraction) to Color.Black,
                                    1.00f to Color.Transparent,
                                    startX = 0f,
                                    endX = size.width
                                )
                                drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .offset { IntOffset((slot0Coord - iconHalfBoxPx).roundToInt(), 0) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectState(true)
                                    coroutineScope.launch {
                                        focusProgress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (modeType == DndModeType.DND_ON_OFF) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                tint = slot0Color,
                                modifier = Modifier
                                    .scale(slot0Scale)
                                    .alpha(slot0Alpha)
                                    .size(effectiveIconSizeDp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .offset { IntOffset((slot1Coord - iconHalfBoxPx).roundToInt(), 0) }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onSelectState(false)
                                    coroutineScope.launch {
                                        focusProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = slot1Color,
                                modifier = Modifier
                                    .scale(slot1Scale)
                                    .alpha(slot1Alpha)
                                    .size(effectiveIconSizeDp)
                            )
                        }
                    }
                }

                if (!isBottom && showLabel) {
                    TextLabel(
                        text = labelText,
                        isVisible = isVisible,
                        isVerticalEdge = false,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextLabel(
    text: String,
    isVisible: Boolean,
    isVerticalEdge: Boolean,
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
