package com.nudge.edge.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val HudHousingBlack = Color(0xFF000000)
private val HudTrackCharcoal = Color(0xFF2C2C2E)
private val HudFillWhite = Color(0xFFFFFFFF)
private val HudIconOnWhite = Color(0xFF6E6E73)
private val HudIconOnDark = Color(0xFFFFFFFF)

private const val SLOPE_ANGLE_RAD = 75.0 * PI / 180.0
private val SLOPE_SIN = sin(SLOPE_ANGLE_RAD).toFloat()
private val SLOPE_COS = cos(SLOPE_ANGLE_RAD).toFloat()

const val HUD_CUTOUT_BEZEL_PAD_DP = 0f
const val HUD_CUTOUT_INNER_PAD_DP = 6.0f
const val HUD_CUTOUT_PILL_TRIM_DP = 4f
const val HUD_CUTOUT_FILLET_TAIL_DP = 22f
const val HUD_CUTOUT_PILL_START_OFFSET_DP = HUD_CUTOUT_FILLET_TAIL_DP + HUD_CUTOUT_INNER_PAD_DP

fun computeHudOuterSpanDp(pillHeightDp: Float): Float {
    return computeHudDrawnPillSpanDp(pillHeightDp) + HUD_CUTOUT_PILL_START_OFFSET_DP * 2f
}

fun computeHudDrawnPillSpanDp(pillHeightDp: Float): Float {
    return (pillHeightDp - HUD_CUTOUT_PILL_TRIM_DP * 2f).coerceAtLeast(36f)
}

fun computeHudCutoutThicknessDp(pillWidthDp: Float, edgeInsetDp: Float = 0f): Float {
    return pillWidthDp + HUD_CUTOUT_BEZEL_PAD_DP + edgeInsetDp.coerceAtLeast(0f) + HUD_CUTOUT_INNER_PAD_DP
}

fun buildHudHousingPath(
    path: Path,
    width: Float,
    height: Float,
    screenEdge: ScreenEdge,
    pillCornerRadiusPx: Float,
    innerPadPx: Float,
    filletTailPx: Float
) {
    path.reset()
    if (width <= 0f || height <= 0f) return

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val filletHeight = filletTailPx.coerceAtMost(height * 0.25f)
            val maxOuterRadius = minOf(
                width * 0.85f,
                height * 0.5f - filletHeight
            ).coerceAtLeast(1f)
            val outerRadius = (pillCornerRadiusPx + innerPadPx).coerceIn(
                minOf(innerPadPx, maxOuterRadius).coerceAtLeast(1f),
                maxOuterRadius
            )
            val isRight = screenEdge == ScreenEdge.RIGHT
            val bezelX = if (isRight) width else 0f
            val outerWallX = if (isRight) 0f else width
            val cornerCenterX = if (isRight) outerRadius else width - outerRadius
            val topCornerCenterY = filletHeight + outerRadius
            val bottomCornerCenterY = height - filletHeight - outerRadius
            val arcJoinX = if (isRight) {
                cornerCenterX - outerRadius * SLOPE_COS
            } else {
                cornerCenterX + outerRadius * SLOPE_COS
            }
            val topArcStartY = topCornerCenterY - outerRadius * SLOPE_SIN
            val bottomArcEndY = bottomCornerCenterY + outerRadius * SLOPE_SIN
            val tangentLength = width * 0.38f
            val scoopCtrlX = if (isRight) {
                arcJoinX + tangentLength * SLOPE_SIN
            } else {
                arcJoinX - tangentLength * SLOPE_SIN
            }
            val topStartDeg = if (isRight) -105f else -75f
            val bottomStartDeg = if (isRight) 180f else 0f
            val arcSweepDeg = if (isRight) -75f else 75f

            path.moveTo(bezelX, 0f)
            path.cubicTo(
                bezelX,
                filletHeight * 0.65f,
                scoopCtrlX,
                topArcStartY - tangentLength * SLOPE_COS,
                arcJoinX,
                topArcStartY
            )
            path.arcTo(
                rect = Rect(
                    left = cornerCenterX - outerRadius,
                    top = topCornerCenterY - outerRadius,
                    right = cornerCenterX + outerRadius,
                    bottom = topCornerCenterY + outerRadius
                ),
                startAngleDegrees = topStartDeg,
                sweepAngleDegrees = arcSweepDeg,
                forceMoveTo = false
            )
            path.lineTo(outerWallX, bottomCornerCenterY)
            path.arcTo(
                rect = Rect(
                    left = cornerCenterX - outerRadius,
                    top = bottomCornerCenterY - outerRadius,
                    right = cornerCenterX + outerRadius,
                    bottom = bottomCornerCenterY + outerRadius
                ),
                startAngleDegrees = bottomStartDeg,
                sweepAngleDegrees = arcSweepDeg,
                forceMoveTo = false
            )
            path.cubicTo(
                scoopCtrlX,
                bottomArcEndY + tangentLength * SLOPE_COS,
                bezelX,
                height - filletHeight * 0.65f,
                bezelX,
                height
            )
            path.close()
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val filletSpan = filletTailPx.coerceAtMost(width * 0.25f)
            val maxOuterRadius = minOf(
                height * 0.85f,
                width * 0.5f - filletSpan
            ).coerceAtLeast(1f)
            val outerRadius = (pillCornerRadiusPx + innerPadPx).coerceIn(
                minOf(innerPadPx, maxOuterRadius).coerceAtLeast(1f),
                maxOuterRadius
            )
            val isBottom = screenEdge == ScreenEdge.BOTTOM
            val bezelY = if (isBottom) height else 0f
            val outerWallY = if (isBottom) 0f else height
            val cornerCenterY = if (isBottom) outerRadius else height - outerRadius
            val leftCornerCenterX = filletSpan + outerRadius
            val rightCornerCenterX = width - filletSpan - outerRadius
            val arcJoinY = if (isBottom) {
                cornerCenterY - outerRadius * SLOPE_COS
            } else {
                cornerCenterY + outerRadius * SLOPE_COS
            }
            val leftArcStartX = leftCornerCenterX - outerRadius * SLOPE_SIN
            val rightArcEndX = rightCornerCenterX + outerRadius * SLOPE_SIN
            val tangentLength = height * 0.38f
            val scoopCtrlY = if (isBottom) {
                arcJoinY + tangentLength * SLOPE_SIN
            } else {
                arcJoinY - tangentLength * SLOPE_SIN
            }
            val leftStartDeg = if (isBottom) -165f else 165f
            val rightStartDeg = if (isBottom) -90f else 90f
            val arcSweepDeg = if (isBottom) 75f else -75f

            path.moveTo(0f, bezelY)
            path.cubicTo(
                filletSpan * 0.65f,
                bezelY,
                leftArcStartX - tangentLength * SLOPE_COS,
                scoopCtrlY,
                leftArcStartX,
                arcJoinY
            )
            path.arcTo(
                rect = Rect(
                    left = leftCornerCenterX - outerRadius,
                    top = cornerCenterY - outerRadius,
                    right = leftCornerCenterX + outerRadius,
                    bottom = cornerCenterY + outerRadius
                ),
                startAngleDegrees = leftStartDeg,
                sweepAngleDegrees = arcSweepDeg,
                forceMoveTo = false
            )
            path.lineTo(rightCornerCenterX, outerWallY)
            path.arcTo(
                rect = Rect(
                    left = rightCornerCenterX - outerRadius,
                    top = cornerCenterY - outerRadius,
                    right = rightCornerCenterX + outerRadius,
                    bottom = cornerCenterY + outerRadius
                ),
                startAngleDegrees = rightStartDeg,
                sweepAngleDegrees = arcSweepDeg,
                forceMoveTo = false
            )
            path.cubicTo(
                rightArcEndX + tangentLength * SLOPE_COS,
                scoopCtrlY,
                width - filletSpan * 0.65f,
                bezelY,
                width,
                bezelY
            )
            path.close()
        }
    }
}

fun DrawScope.drawSpeakerGlyph(
    center: Offset,
    glyphSize: Float,
    color: Color,
    volumeFraction: Float,
    pathBuffer: Path,
    cutoutColor: Color = HudTrackCharcoal
) {
    val s = glyphSize
    val barCount = when {
        volumeFraction <= 0.005f -> 0
        volumeFraction <= 0.34f -> 1
        volumeFraction <= 0.67f -> 2
        else -> 3
    }
    val shiftX = when (barCount) {
        0 -> s * 0.005f
        1 -> -s * 0.07f
        2 -> -s * 0.15f
        else -> -s * 0.23f
    }

    val left = center.x - s * 0.20f + shiftX
    val midX = center.x - s * 0.01f + shiftX
    val right = center.x + s * 0.19f + shiftX
    val cy = center.y
    val hBody = s * 0.16f
    val hCone = s * 0.34f
    val rBack = s * 0.08f
    val rFront = s * 0.07f

    pathBuffer.reset()
    pathBuffer.moveTo(left + rBack, cy - hBody)
    pathBuffer.lineTo(midX - rBack * 0.25f, cy - hBody)
    pathBuffer.quadraticTo(midX, cy - hBody, midX + rBack * 0.65f, cy - hBody - rBack * 0.65f)
    pathBuffer.lineTo(right - rFront * 1.1f, cy - hCone + rFront * 0.7f)
    pathBuffer.quadraticTo(right, cy - hCone, right, cy - hCone + rFront * 1.15f)
    pathBuffer.lineTo(right, cy + hCone - rFront * 1.15f)
    pathBuffer.quadraticTo(right, cy + hCone, right - rFront * 1.1f, cy + hCone - rFront * 0.7f)
    pathBuffer.lineTo(midX + rBack * 0.65f, cy + hBody + rBack * 0.65f)
    pathBuffer.quadraticTo(midX, cy + hBody, midX - rBack * 0.25f, cy + hBody)
    pathBuffer.lineTo(left + rBack, cy + hBody)
    pathBuffer.quadraticTo(left, cy + hBody, left, cy + hBody - rBack)
    pathBuffer.lineTo(left, cy - hBody + rBack)
    pathBuffer.quadraticTo(left, cy - hBody, left + rBack, cy - hBody)
    pathBuffer.close()
    drawPath(path = pathBuffer, color = color)

    val strokeW = (s * 0.095f).coerceAtLeast(1.2f)

    if (barCount == 0) {
        val slashStart = Offset(center.x - s * 0.22f, cy - s * 0.25f)
        val slashEnd = Offset(center.x + s * 0.22f, cy + s * 0.25f)
        drawLine(
            color = cutoutColor,
            start = slashStart,
            end = slashEnd,
            strokeWidth = strokeW * 2.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = slashStart,
            end = slashEnd,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        return
    }

    val arcOriginX = center.x + s * 0.14f + shiftX
    val arcColor = color.copy(alpha = color.alpha * 0.85f)

    val r1 = s * 0.20f
    drawArc(
        color = arcColor,
        startAngle = -32f,
        sweepAngle = 64f,
        useCenter = false,
        topLeft = Offset(arcOriginX - r1, cy - r1),
        size = Size(r1 * 2f, r1 * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    if (barCount >= 2) {
        val r2 = s * 0.36f
        drawArc(
            color = arcColor,
            startAngle = -36f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = Offset(arcOriginX - r2, cy - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }

    if (barCount >= 3) {
        val r3 = s * 0.52f
        drawArc(
            color = arcColor,
            startAngle = -40f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(arcOriginX - r3, cy - r3),
            size = Size(r3 * 2f, r3 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun VolumeSliderHud(
    screenEdge: ScreenEdge,
    hudWidthDp: Float,
    hudHeightDp: Float,
    hudCornerRadiusDp: Float,
    floatingMode: Boolean,
    edgeInsetDp: Float,
    volumeFraction: Float,
    isVisible: Boolean,
    resetEpoch: Int = 0,
    onVolumeScrub: (Float) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    interactive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val slideProgress = remember { Animatable(if (isVisible) 1f else 0f) }
    val housingPath = remember { Path() }
    val pillPath = remember { Path() }
    val speakerPath = remember { Path() }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(volumeFraction.coerceIn(0f, 1f)) }

    val floatProgress by animateFloatAsState(
        targetValue = if (floatingMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        label = "hudFloatMode"
    )
    val animatedInsetDp by animateFloatAsState(
        targetValue = edgeInsetDp.coerceAtLeast(0f),
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "hudEdgeInset"
    )
    val animatedFraction by animateFloatAsState(
        targetValue = volumeFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 70, easing = LinearEasing),
        label = "hudVolumeFill"
    )
    val displayedFraction = if (isScrubbing) {
        scrubFraction.coerceIn(0f, 1f)
    } else {
        animatedFraction.coerceIn(0f, 1f)
    }

    val isVerticalEdge = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
    val maxInsetDp = maxOf(edgeInsetDp.coerceAtLeast(0f), animatedInsetDp)
    val cutoutThicknessDp = computeHudCutoutThicknessDp(hudWidthDp, animatedInsetDp)
    val outerSpanDp = computeHudOuterSpanDp(hudHeightDp)
    val drawnPillSpanDp = computeHudDrawnPillSpanDp(hudHeightDp)
    val totalThicknessDp = computeHudCutoutThicknessDp(hudWidthDp, maxInsetDp)
    val containerWidthDp = if (isVerticalEdge) totalThicknessDp else outerSpanDp
    val containerHeightDp = if (isVerticalEdge) outerSpanDp else totalThicknessDp

    val pillStartPx = with(density) { HUD_CUTOUT_PILL_START_OFFSET_DP.dp.toPx() }
    val pillLenPx = with(density) { drawnPillSpanDp.dp.toPx() }
    val pillEndPx = pillStartPx + pillLenPx
    val speakerTapZonePx = with(density) { (hudWidthDp * 0.90f).coerceIn(28f, 40f).dp.toPx() }
    val tapSlopPx = with(density) { 6.dp.toPx() }
    val swipeMenuThresholdPx = with(density) { 14.dp.toPx() }

    val currentOnVolumeScrub by rememberUpdatedState(onVolumeScrub)
    val currentOnScrubStateChanged by rememberUpdatedState(onScrubStateChanged)
    val currentOnOpenMenu by rememberUpdatedState(onOpenMenu)
    val currentPillStart by rememberUpdatedState(pillStartPx)
    val currentPillEnd by rememberUpdatedState(pillEndPx)
    val currentEdge by rememberUpdatedState(screenEdge)
    val currentInteractive by rememberUpdatedState(interactive && isVisible)

    LaunchedEffect(resetEpoch) {
        if (resetEpoch > 0) {
            slideProgress.snapTo(0f)
        }
    }

    LaunchedEffect(isVisible, resetEpoch) {
        if (isVisible) {
            slideProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 740f)
            )
        } else {
            slideProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 540f)
            )
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentInteractive) return@awaitEachGesture
            val downX = down.position.x
            val downY = down.position.y
            val pStart = currentPillStart
            val pEnd = currentPillEnd
            val edge = currentEdge
            val vertical = edge == ScreenEdge.LEFT || edge == ScreenEdge.RIGHT
            val trackSpan = (pEnd - pStart).coerceAtLeast(1f)
            val inSpeakerZone = if (vertical) {
                downY >= pEnd - speakerTapZonePx
            } else {
                downX <= pStart + speakerTapZonePx
            }

            var movedAlongTrack = false
            var openedMenu = false
            val longPressJob = coroutineScope.launch {
                delay(380L)
                if (!movedAlongTrack && !openedMenu) {
                    openedMenu = true
                    isScrubbing = false
                    currentOnScrubStateChanged(false)
                    currentOnOpenMenu()
                }
            }

            if (!inSpeakerZone) {
                isScrubbing = true
                currentOnScrubStateChanged(true)
                val initial = if (vertical) {
                    ((pEnd - downY) / trackSpan).coerceIn(0f, 1f)
                } else {
                    ((downX - pStart) / trackSpan).coerceIn(0f, 1f)
                }
                scrubFraction = initial
                currentOnVolumeScrub(initial)
            }
            down.consume()

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = change.position.x - downX
                    val dy = change.position.y - downY

                    val inwardDelta = when (edge) {
                        ScreenEdge.RIGHT -> -dx
                        ScreenEdge.LEFT -> dx
                        ScreenEdge.TOP -> dy
                        ScreenEdge.BOTTOM -> -dy
                    }
                    val trackDelta = if (vertical) abs(dy) else abs(dx)

                    if (!openedMenu && inwardDelta >= swipeMenuThresholdPx && inwardDelta > trackDelta * 1.25f) {
                        openedMenu = true
                        longPressJob.cancel()
                        isScrubbing = false
                        currentOnScrubStateChanged(false)
                        change.consume()
                        currentOnOpenMenu()
                        break
                    }

                    if (!change.pressed) {
                        longPressJob.cancel()
                        if (!openedMenu && inSpeakerZone && !movedAlongTrack) {
                            currentOnOpenMenu()
                        }
                        change.consume()
                        break
                    }

                    if (trackDelta > tapSlopPx) {
                        movedAlongTrack = true
                        longPressJob.cancel()
                        if (!isScrubbing) {
                            isScrubbing = true
                            currentOnScrubStateChanged(true)
                        }
                    }

                    if (isScrubbing && !openedMenu) {
                        val updated = if (vertical) {
                            ((pEnd - change.position.y) / trackSpan).coerceIn(0f, 1f)
                        } else {
                            ((change.position.x - pStart) / trackSpan).coerceIn(0f, 1f)
                        }
                        scrubFraction = updated
                        currentOnVolumeScrub(updated)
                    }
                    change.consume()
                }
            } finally {
                longPressJob.cancel()
                if (isScrubbing) {
                    isScrubbing = false
                    currentOnScrubStateChanged(false)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(containerWidthDp.dp)
            .height(containerHeightDp.dp)
            .clipToBounds()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val hidden = 1f - slideProgress.value
                    when (screenEdge) {
                        ScreenEdge.RIGHT -> translationX = size.width * hidden
                        ScreenEdge.LEFT -> translationX = -size.width * hidden
                        ScreenEdge.TOP -> translationY = -size.height * hidden
                        ScreenEdge.BOTTOM -> translationY = size.height * hidden
                    }
                }
                .then(gestureModifier)
        ) {
            if (slideProgress.value <= 0.01f) return@Canvas

            val pillW = hudWidthDp.dp.toPx()
            val pillSpan = drawnPillSpanDp.dp.toPx()
            val pillCornerRadius = hudCornerRadiusDp.dp.toPx().coerceIn(
                0f,
                minOf(pillW, pillSpan) * 0.5f
            )
            val notchAlpha = (1f - floatProgress).coerceIn(0f, 1f)
            val cutoutThicknessPx = cutoutThicknessDp.dp.toPx()
            val bezelPadPx = (HUD_CUTOUT_BEZEL_PAD_DP + animatedInsetDp).dp.toPx()
            val innerPadPx = HUD_CUTOUT_INNER_PAD_DP.dp.toPx()
            val filletTailPx = HUD_CUTOUT_FILLET_TAIL_DP.dp.toPx()
            val pillStartOffsetPx = HUD_CUTOUT_PILL_START_OFFSET_DP.dp.toPx()

            if (isVerticalEdge) {
                val isRight = screenEdge == ScreenEdge.RIGHT
                val bezelEdgeX = if (isRight) size.width else 0f
                val inwardDir = if (isRight) -1f else 1f

                if (notchAlpha > 0.01f) {
                    val notchWidth = cutoutThicknessPx * (1f - floatProgress * 0.72f)
                    val notchLeft = if (isRight) bezelEdgeX - notchWidth else 0f
                    translate(left = notchLeft, top = 0f) {
                        buildHudHousingPath(
                            path = housingPath,
                            width = notchWidth,
                            height = size.height,
                            screenEdge = screenEdge,
                            pillCornerRadiusPx = pillCornerRadius,
                            innerPadPx = innerPadPx,
                            filletTailPx = filletTailPx
                        )
                        drawPath(
                            path = housingPath,
                            color = HudHousingBlack.copy(alpha = notchAlpha)
                        )
                    }
                }

                val pillCenterX = bezelEdgeX + inwardDir * (bezelPadPx + pillW * 0.5f)
                val pillLeft = pillCenterX - pillW * 0.5f
                val pillRight = pillCenterX + pillW * 0.5f
                val pillTop = pillStartOffsetPx
                val pillBottom = pillStartOffsetPx + pillSpan

                pillPath.reset()
                pillPath.addRoundRect(
                    RoundRect(
                        rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                        cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
                    )
                )
                drawPath(path = pillPath, color = HudTrackCharcoal)

                val fillTop = (pillBottom - pillSpan * displayedFraction).coerceIn(pillTop, pillBottom)

                clipPath(pillPath) {
                    if (displayedFraction > 0.002f) {
                        drawRect(
                            color = HudFillWhite,
                            topLeft = Offset(pillLeft, fillTop),
                            size = Size(pillW, pillBottom - fillTop)
                        )
                    }
                    val iconSize = (pillW * 0.45f).coerceIn(12.dp.toPx(), 20.dp.toPx())
                    val iconCenter = Offset(
                        x = pillCenterX,
                        y = pillBottom - minOf(pillW * 0.44f, 18.dp.toPx())
                    )
                    if (fillTop > pillTop) {
                        clipRect(pillLeft, pillTop, pillRight, fillTop) {
                            drawSpeakerGlyph(
                                center = iconCenter,
                                glyphSize = iconSize,
                                color = HudIconOnDark,
                                volumeFraction = displayedFraction,
                                pathBuffer = speakerPath,
                                cutoutColor = HudTrackCharcoal
                            )
                        }
                    }
                    if (fillTop < pillBottom) {
                        clipRect(pillLeft, fillTop, pillRight, pillBottom) {
                            drawSpeakerGlyph(
                                center = iconCenter,
                                glyphSize = iconSize,
                                color = HudIconOnWhite,
                                volumeFraction = displayedFraction,
                                pathBuffer = speakerPath,
                                cutoutColor = HudFillWhite
                            )
                        }
                    }
                }


            } else {
                val isTop = screenEdge == ScreenEdge.TOP
                val bezelEdgeY = if (isTop) 0f else size.height
                val inwardDir = if (isTop) 1f else -1f

                if (notchAlpha > 0.01f) {
                    val notchHeight = cutoutThicknessPx * (1f - floatProgress * 0.72f)
                    val notchTop = if (isTop) 0f else bezelEdgeY - notchHeight
                    translate(left = 0f, top = notchTop) {
                        buildHudHousingPath(
                            path = housingPath,
                            width = size.width,
                            height = notchHeight,
                            screenEdge = screenEdge,
                            pillCornerRadiusPx = pillCornerRadius,
                            innerPadPx = innerPadPx,
                            filletTailPx = filletTailPx
                        )
                        drawPath(
                            path = housingPath,
                            color = HudHousingBlack.copy(alpha = notchAlpha)
                        )
                    }
                }

                val pillCenterY = bezelEdgeY + inwardDir * (bezelPadPx + pillW * 0.5f)
                val pillTop = pillCenterY - pillW * 0.5f
                val pillBottom = pillCenterY + pillW * 0.5f
                val pillLeft = pillStartOffsetPx
                val pillRight = pillStartOffsetPx + pillSpan

                pillPath.reset()
                pillPath.addRoundRect(
                    RoundRect(
                        rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                        cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
                    )
                )
                drawPath(path = pillPath, color = HudTrackCharcoal)

                val fillRight = (pillLeft + pillSpan * displayedFraction).coerceIn(pillLeft, pillRight)

                clipPath(pillPath) {
                    if (displayedFraction > 0.002f) {
                        drawRect(
                            color = HudFillWhite,
                            topLeft = Offset(pillLeft, pillTop),
                            size = Size(fillRight - pillLeft, pillW)
                        )
                    }
                    val iconSize = (pillW * 0.45f).coerceIn(12.dp.toPx(), 20.dp.toPx())
                    val iconCenter = Offset(
                        x = pillLeft + minOf(pillW * 0.44f, 18.dp.toPx()),
                        y = pillCenterY
                    )
                    if (fillRight > pillLeft) {
                        clipRect(pillLeft, pillTop, fillRight, pillBottom) {
                            drawSpeakerGlyph(
                                center = iconCenter,
                                glyphSize = iconSize,
                                color = HudIconOnWhite,
                                volumeFraction = displayedFraction,
                                pathBuffer = speakerPath,
                                cutoutColor = HudFillWhite
                            )
                        }
                    }
                    if (fillRight < pillRight) {
                        clipRect(fillRight, pillTop, pillRight, pillBottom) {
                            drawSpeakerGlyph(
                                center = iconCenter,
                                glyphSize = iconSize,
                                color = HudIconOnDark,
                                volumeFraction = displayedFraction,
                                pathBuffer = speakerPath,
                                cutoutColor = HudTrackCharcoal
                            )
                        }
                    }
                }


            }
        }
    }
}