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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.edge.model.CustomMode
import com.nudge.edge.model.DEFAULT_CUSTOM_MODES
import com.nudge.edge.model.NudgeIcons
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val HousingBlack = Color(0xFF000000)

const val ACTIONS_BEZEL_FILLET_DP = 34f
const val MODES_BEZEL_FILLET_DP = ACTIONS_BEZEL_FILLET_DP

fun computeActionsOuterSpanDp(lengthDp: Float, fluidityPercent: Int? = null): Float {
    return lengthDp + ACTIONS_BEZEL_FILLET_DP * 2f
}
fun computeModesOuterSpanDp(lengthDp: Float, fluidityPercent: Int? = null): Float = computeActionsOuterSpanDp(lengthDp, fluidityPercent)

@Composable
fun ActionsPanelOverlay(
    screenEdge: ScreenEdge,
    overlayWidthDp: Float,
    overlayHeightDp: Float,
    iconSpacingDp: Float = 35f,
    modes: List<CustomMode> = DEFAULT_CUSTOM_MODES,
    selectedModeId: String = modes.firstOrNull()?.id ?: "normal",
    isVisible: Boolean,
    onModeSelected: (CustomMode) -> Unit,
    onStepUp: () -> Unit = {},
    onStepDown: () -> Unit = {},
    onPauseAutoHideTimer: () -> Unit = {},
    onRestartAutoHideTimer: () -> Unit = {},
    stepFlow: SharedFlow<Int>? = null,
    iconSizeDp: Float = 24f,
    fluidityPercent: Int = 80,
    showLabel: Boolean = true,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveModes = if (modes.size >= 2) modes else DEFAULT_CUSTOM_MODES
    val initialIndex = effectiveModes.indexOfFirst { it.id == selectedModeId }.let {
        if (it >= 0) it else 0
    }

    val coroutineScope = rememberCoroutineScope()
    val wheelScrollPosition = remember { Animatable(initialIndex.toFloat()) }
    val currentEffectiveModes by rememberUpdatedState(effectiveModes)
    val currentOnModeSelected by rememberUpdatedState(onModeSelected)
    var isFingerTouchingPanel by remember { mutableStateOf(false) }
    val isPanelBusy = isFingerTouchingPanel || wheelScrollPosition.isRunning

    LaunchedEffect(isPanelBusy) {
        if (isPanelBusy) {
            onPauseAutoHideTimer()
        } else {
            onRestartAutoHideTimer()
        }
    }

    val density = LocalDensity.current
    val totalSpanDp = computeActionsOuterSpanDp(overlayHeightDp)
    val protrusionDp = overlayWidthDp

    val isVerticalEdge = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
    val widthDp = if (isVerticalEdge) protrusionDp else totalSpanDp
    val heightDp = if (isVerticalEdge) totalSpanDp else protrusionDp

    val panelThicknessDp = if (isVerticalEdge) overlayWidthDp else overlayHeightDp
    val maxAllowedByWidthDp = (panelThicknessDp.dp - 6.dp).coerceAtLeast(10.dp)
    val effectiveIconSizeDp = minOf(24.dp, maxAllowedByWidthDp)

    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val minCurveZonePx = with(density) { 32.dp.toPx() }.coerceAtMost(if (isVerticalEdge) heightPx * 0.35f else widthPx * 0.35f)
    val maxCurveZonePx = with(density) { 56.dp.toPx() }
    val iconSpacingPx = with(density) { 35.dp.toPx() }

    val totalLengthPx = if (isVerticalEdge) heightPx else widthPx
    val maxSlotsBySpace = ((totalLengthPx / iconSpacingPx).toInt() + 2).coerceAtMost(20)
    val totalVisibleSlots = minOf(20, maxSlotsBySpace)
    val halfSpan = totalVisibleSlots / 2
    val maxDistance = (halfSpan + 0.5f).coerceAtLeast(2.8f)

    val slideAnim = remember { Animatable(if (isVisible) 1f else 0f) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val currIndex = effectiveModes.indexOfFirst { it.id == selectedModeId }.coerceAtLeast(0)
            wheelScrollPosition.snapTo(currIndex.toFloat())
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

    LaunchedEffect(stepFlow) {
        stepFlow?.collect { stepDirection ->
            val modesNow = currentEffectiveModes
            if (modesNow.isEmpty()) return@collect
            val nextTarget = (wheelScrollPosition.targetValue.roundToInt() + stepDirection).toFloat()
            wheelScrollPosition.animateTo(
                targetValue = nextTarget,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            val actualIdx = Math.floorMod(nextTarget.roundToInt(), modesNow.size)
            currentOnModeSelected(modesNow[actualIdx])
        }
    }

    if (slideAnim.value <= 0.001f && !isVisible) {
        return
    }

    val centerVirtualIndex = wheelScrollPosition.value.roundToInt()
    val centerModeIndex = Math.floorMod(centerVirtualIndex, effectiveModes.size)
    val currentMode = effectiveModes[centerModeIndex]

    val housingPath = remember { Path() }
    val animProgress = slideAnim.value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            }
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
        if (isVerticalEdge) {
            val isRight = screenEdge == ScreenEdge.RIGHT
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRight && showLabel) {
                    ActionsTextLabel(
                        text = currentMode.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = true,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                        .pointerInput(effectiveModes.size) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    onPauseAutoHideTimer()
                                    val touchSlopPx = with(density) { 8.dp.toPx() }
                                    var totalDelta = 0f
                                    var isDrag = false
                                    try {
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val delta = change.position.y - lastPos.y
                                            lastPos = change.position
                                            totalDelta += delta
                                            if (abs(totalDelta) > touchSlopPx) {
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
                                        if (isDrag) {
                                            val targetIndex = wheelScrollPosition.value.roundToInt()
                                            val actualIdx = Math.floorMod(targetIndex, effectiveModes.size)
                                            if (isVisible && isFingerTouchingPanel) {
                                                onModeSelected(effectiveModes[actualIdx])
                                            }
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
                                        } else {
                                            isFingerTouchingPanel = false
                                            val centerAxis = with(density) { heightDp.dp.toPx() } / 2f
                                            val currentScroll = wheelScrollPosition.value
                                            val tappedVirtualIndex = (currentScroll + ((down.position.y - centerAxis) / iconSpacingPx)).roundToInt()
                                            val expectedIconCoord = centerAxis + (tappedVirtualIndex - currentScroll) * iconSpacingPx
                                            val iconRadiusPx = with(density) { (effectiveIconSizeDp / 2f + 8.dp).toPx() }
                                            val hitIcon = abs(down.position.y - expectedIconCoord) <= iconRadiusPx
                                            if (hitIcon && isVisible) {
                                                val actualIdx = Math.floorMod(tappedVirtualIndex, effectiveModes.size)
                                                val tappedMode = effectiveModes[actualIdx]
                                                launch {
                                                    try {
                                                        wheelScrollPosition.animateTo(
                                                            targetValue = tappedVirtualIndex.toFloat(),
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                        onModeSelected(tappedMode)
                                                    } finally {
                                                        isFingerTouchingPanel = false
                                                    }
                                                }
                                            } else {
                                                onDismiss()
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

                    val customShape = remember(widthPx, heightPx, screenEdge, minCurveZonePx, maxCurveZonePx) {
                        object : Shape {
                            override fun createOutline(
                                size: androidx.compose.ui.geometry.Size,
                                layoutDirection: LayoutDirection,
                                density: Density
                            ): Outline {
                                val path = Path()
                                buildOrganicBezelPath(
                                    path = path,
                                    width = size.width,
                                    height = size.height,
                                    screenEdge = screenEdge,
                                    minCurveZonePx = minCurveZonePx,
                                    maxCurveZonePx = maxCurveZonePx
                                )
                                return Outline.Generic(path)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(customShape)
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val numModes = effectiveModes.size
                        val halfModes = (numModes - 1) / 2
                        val minOffset = -halfModes
                        val maxOffset = numModes - 1 - halfModes
                        for (offset in minOffset..maxOffset) {
                            val virtualIndex = centerVirtualIndex + offset
                            val actualModeIndex = Math.floorMod(virtualIndex, effectiveModes.size)
                            val mode = effectiveModes[actualModeIndex]
                            val fractionalDistance = virtualIndex - wheelScrollPosition.value
                            val distance = abs(fractionalDistance)
                            if (distance <= maxDistance) {
                                val scale = when {
                                    distance <= 1f -> 1.0f - distance * (1.0f - 0.82f)
                                    distance <= 2f -> 0.82f - (distance - 1f) * (0.82f - 0.60f)
                                    else -> (0.60f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.40f).coerceAtLeast(0.1f)
                                }
                                val alpha = when {
                                    distance <= 1f -> 1.0f - distance * (1.0f - 0.78f)
                                    distance <= 2f -> 0.78f - (distance - 1f) * (0.78f - 0.45f)
                                    else -> (0.45f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.45f).coerceAtLeast(0f)
                                }
                                val yOffsetPx = fractionalDistance * iconSpacingPx
                                val iconColor = Color(mode.colorHex)

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(0, yOffsetPx.roundToInt()) }
                                        .scale(scale)
                                        .alpha(alpha)
                                        .size(effectiveIconSizeDp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            coroutineScope.launch {
                                                wheelScrollPosition.animateTo(
                                                    targetValue = virtualIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                                onModeSelected(mode)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (mode.iconKey.equals("NotificationsOff", ignoreCase = true) || mode.id.equals("silent", ignoreCase = true)) {
                                            Icons.Rounded.NotificationsOff
                                        } else {
                                            NudgeIcons.getIcon(mode.iconKey)
                                        },
                                        contentDescription = mode.label,
                                        tint = iconColor,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isRight && showLabel) {
                    ActionsTextLabel(
                        text = currentMode.label.uppercase(),
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
                    ActionsTextLabel(
                        text = currentMode.label.uppercase(),
                        isVisible = isVisible,
                        isVerticalEdge = false,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(heightDp.dp)
                        .pointerInput(effectiveModes.size) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    isFingerTouchingPanel = true
                                    onPauseAutoHideTimer()
                                    val touchSlopPx = with(density) { 8.dp.toPx() }
                                    var totalDelta = 0f
                                    var isDrag = false
                                    try {
                                        var lastPos = down.position
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            val delta = change.position.x - lastPos.x
                                            lastPos = change.position
                                            totalDelta += delta
                                            if (abs(totalDelta) > touchSlopPx) {
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
                                        if (isDrag) {
                                            val targetIndex = wheelScrollPosition.value.roundToInt()
                                            val actualIdx = Math.floorMod(targetIndex, effectiveModes.size)
                                            if (isVisible && isFingerTouchingPanel) {
                                                onModeSelected(effectiveModes[actualIdx])
                                            }
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
                                        } else {
                                            isFingerTouchingPanel = false
                                            val centerAxis = with(density) { widthDp.dp.toPx() } / 2f
                                            val currentScroll = wheelScrollPosition.value
                                            val tappedVirtualIndex = (currentScroll + ((down.position.x - centerAxis) / iconSpacingPx)).roundToInt()
                                            val expectedIconCoord = centerAxis + (tappedVirtualIndex - currentScroll) * iconSpacingPx
                                            val iconRadiusPx = with(density) { (effectiveIconSizeDp / 2f + 8.dp).toPx() }
                                            val hitIcon = abs(down.position.x - expectedIconCoord) <= iconRadiusPx
                                            if (hitIcon && isVisible) {
                                                val actualIdx = Math.floorMod(tappedVirtualIndex, effectiveModes.size)
                                                val tappedMode = effectiveModes[actualIdx]
                                                launch {
                                                    try {
                                                        wheelScrollPosition.animateTo(
                                                            targetValue = tappedVirtualIndex.toFloat(),
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                             )
                                                        )
                                                        onModeSelected(tappedMode)
                                                    } finally {
                                                        isFingerTouchingPanel = false
                                                    }
                                                }
                                            } else {
                                                onDismiss()
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

                    val customShape = remember(widthPx, heightPx, screenEdge, minCurveZonePx, maxCurveZonePx) {
                        object : Shape {
                            override fun createOutline(
                                size: androidx.compose.ui.geometry.Size,
                                layoutDirection: LayoutDirection,
                                density: Density
                            ): Outline {
                                val path = Path()
                                buildOrganicBezelPath(
                                    path = path,
                                    width = size.width,
                                    height = size.height,
                                    screenEdge = screenEdge,
                                    minCurveZonePx = minCurveZonePx,
                                    maxCurveZonePx = maxCurveZonePx
                                )
                                return Outline.Generic(path)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(customShape)
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val numModes = effectiveModes.size
                        val halfModes = (numModes - 1) / 2
                        val minOffset = -halfModes
                        val maxOffset = numModes - 1 - halfModes
                        for (offset in minOffset..maxOffset) {
                            val virtualIndex = centerVirtualIndex + offset
                            val actualModeIndex = Math.floorMod(virtualIndex, effectiveModes.size)
                            val mode = effectiveModes[actualModeIndex]
                            val fractionalDistance = virtualIndex - wheelScrollPosition.value
                            val distance = abs(fractionalDistance)
                            if (distance <= maxDistance) {
                                val scale = when {
                                    distance <= 1f -> 1.0f - distance * (1.0f - 0.82f)
                                    distance <= 2f -> 0.82f - (distance - 1f) * (0.82f - 0.60f)
                                    else -> (0.60f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.40f).coerceAtLeast(0.1f)
                                }
                                val alpha = when {
                                    distance <= 1f -> 1.0f - distance * (1.0f - 0.78f)
                                    distance <= 2f -> 0.78f - (distance - 1f) * (0.78f - 0.45f)
                                    else -> (0.45f - ((distance - 2f) / (maxDistance - 2f).coerceAtLeast(1f)) * 0.45f).coerceAtLeast(0f)
                                }
                                val xOffsetPx = fractionalDistance * iconSpacingPx
                                val iconColor = Color(mode.colorHex)

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(xOffsetPx.roundToInt(), 0) }
                                        .scale(scale)
                                        .alpha(alpha)
                                        .size(effectiveIconSizeDp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            coroutineScope.launch {
                                                wheelScrollPosition.animateTo(
                                                    targetValue = virtualIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    )
                                                )
                                                onModeSelected(mode)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (mode.iconKey.equals("NotificationsOff", ignoreCase = true) || mode.id.equals("silent", ignoreCase = true)) {
                                            Icons.Rounded.NotificationsOff
                                        } else {
                                            NudgeIcons.getIcon(mode.iconKey)
                                        },
                                        contentDescription = mode.label,
                                        tint = iconColor,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isBottom && showLabel) {
                    ActionsTextLabel(
                        text = currentMode.label.uppercase(),
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
private fun ActionsTextLabel(
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
