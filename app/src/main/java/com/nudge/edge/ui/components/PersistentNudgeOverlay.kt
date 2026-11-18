package com.nudge.edge.ui.components

import android.view.Surface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.PersistentNudge
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.launch

private val HousingBlack = Color(0xFF000000)

@Composable
fun PersistentNudgesCanvasOverlay(
    nudges: List<PersistentNudge>,
    isLandscape: Boolean,
    nudgeWidthDp: Float = 14f,
    nudgeHeightDp: Float = 64f,
    glowEnabled: Boolean = true,
    displayRotation: Int = Surface.ROTATION_0,
    pullDistances: Map<String, Float> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scratchPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        for (nudge in nudges) {
            if (!nudge.enabled) continue
            if (isLandscape && !nudge.displayOnLandscape) continue

            val portraitSide = if (nudge.edge == ScreenEdge.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
            val physicalEdge = EdgeOrientationMapper.resolvePhysicalEdge(portraitSide, displayRotation)
            val axisFraction = EdgeOrientationMapper.resolveAxisFraction(nudge.yFraction, displayRotation)
            val isVertical = physicalEdge == ScreenEdge.LEFT || physicalEdge == ScreenEdge.RIGHT

            val protrusionPx = with(density) { nudgeWidthDp.dp.toPx() }
            val spanPx = with(density) { nudgeHeightDp.dp.toPx() }

            val axisLength = if (isVertical) size.height else size.width
            val centerPos = axisLength * axisFraction
            val startPos = (centerPos - spanPx * 0.5f).coerceAtLeast(0f)
            val endPos = (centerPos + spanPx * 0.5f).coerceAtMost(axisLength)

            buildFlatTopEdgePopPath(
                path = scratchPath,
                canvasWidth = size.width,
                canvasHeight = size.height,
                startPosPx = startPos,
                endPosPx = endPos,
                protrusionPx = protrusionPx,
                screenEdge = physicalEdge
            )

            drawPath(path = scratchPath, color = HousingBlack)

            if (glowEnabled) {
                drawFlatTopGlowLine(
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    startPosPx = startPos,
                    endPosPx = endPos,
                    protrusionPx = protrusionPx,
                    screenEdge = physicalEdge
                )
            }
        }
    }
}

@Composable
fun PersistentNudgeHandle(
    nudge: PersistentNudge,
    isLandscape: Boolean,
    widthDp: Float = 14f,
    heightDp: Float = 64f,
    glowEnabled: Boolean = true,
    displayRotation: Int = Surface.ROTATION_0,
    onTriggerAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!nudge.enabled) return
    if (isLandscape && !nudge.displayOnLandscape) return

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val scratchPath = remember { Path() }

    val pullAnim = remember { Animatable(0f) }

    val portraitSide = if (nudge.edge == ScreenEdge.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
    val physicalEdge = EdgeOrientationMapper.resolvePhysicalEdge(portraitSide, displayRotation)
    val axisFraction = EdgeOrientationMapper.resolveAxisFraction(nudge.yFraction, displayRotation)
    val isVertical = physicalEdge == ScreenEdge.LEFT || physicalEdge == ScreenEdge.RIGHT

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(nudge.id, displayRotation) {
                val touchSlopPx = with(density) { 14.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalInwardDelta = 0f
                    var triggered = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val inwardDelta = when (physicalEdge) {
                                ScreenEdge.RIGHT -> -(change.position.x - down.position.x)
                                ScreenEdge.LEFT -> change.position.x - down.position.x
                                ScreenEdge.TOP -> change.position.y - down.position.y
                                ScreenEdge.BOTTOM -> -(change.position.y - down.position.y)
                            }
                            totalInwardDelta = inwardDelta

                            if (inwardDelta > 0f) {
                                coroutineScope.launch {
                                    pullAnim.snapTo((inwardDelta / touchSlopPx).coerceIn(0f, 1.5f))
                                }
                            }

                            if (!triggered && inwardDelta >= touchSlopPx) {
                                triggered = true
                                change.consume()
                                onTriggerAction()
                            }
                        }
                    } finally {
                        coroutineScope.launch {
                            pullAnim.animateTo(
                                0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val protrusionPx = with(density) { widthDp.dp.toPx() }
            val spanPx = with(density) { heightDp.dp.toPx() }

            val axisLength = if (isVertical) size.height else size.width
            val centerPos = axisLength * axisFraction
            val startPos = (centerPos - spanPx * 0.5f).coerceAtLeast(0f)
            val endPos = (centerPos + spanPx * 0.5f).coerceAtMost(axisLength)

            buildFlatTopEdgePopPath(
                path = scratchPath,
                canvasWidth = size.width,
                canvasHeight = size.height,
                startPosPx = startPos,
                endPosPx = endPos,
                protrusionPx = protrusionPx,
                screenEdge = physicalEdge
            )

            drawPath(path = scratchPath, color = HousingBlack)

            if (glowEnabled) {
                drawFlatTopGlowLine(
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    startPosPx = startPos,
                    endPosPx = endPos,
                    protrusionPx = protrusionPx,
                    screenEdge = physicalEdge
                )
            }
        }
    }
}
