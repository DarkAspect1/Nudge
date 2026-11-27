package com.nudge.edge.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgePopStyle
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.launch
import kotlin.math.abs

fun buildEdgePopPath(
    path: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    protrusionPx: Float,
    screenEdge: ScreenEdge
) {
    path.reset()
    if (protrusionPx <= 0.25f || canvasWidth <= 0f || canvasHeight <= 0f) return
    val k1 = 0.24f
    val k2 = 0.17f

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val isRight = screenEdge == ScreenEdge.RIGHT
            val edgeX = if (isRight) canvasWidth else 0f
            val dir = if (isRight) -1f else 1f
            val apexX = edgeX + dir * protrusionPx
            path.moveTo(edgeX, 0f)
            path.cubicTo(
                edgeX,
                canvasHeight * k1,
                apexX,
                canvasHeight * (0.5f - k2),
                apexX,
                canvasHeight * 0.5f
            )
            path.cubicTo(
                apexX,
                canvasHeight * (0.5f + k2),
                edgeX,
                canvasHeight * (1f - k1),
                edgeX,
                canvasHeight
            )
            path.close()
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val isTop = screenEdge == ScreenEdge.TOP
            val edgeY = if (isTop) 0f else canvasHeight
            val dir = if (isTop) 1f else -1f
            val apexY = edgeY + dir * protrusionPx
            path.moveTo(0f, edgeY)
            path.cubicTo(
                canvasWidth * k1,
                edgeY,
                canvasWidth * (0.5f - k2),
                apexY,
                canvasWidth * 0.5f,
                apexY
            )
            path.cubicTo(
                canvasWidth * (0.5f + k2),
                apexY,
                canvasWidth * (1f - k1),
                edgeY,
                canvasWidth,
                edgeY
            )
            path.close()
        }
    }
}

fun buildMergedEdgePopPath(
    path: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    firstCenterPx: Float,
    secondCenterPx: Float,
    spreadPx: Float,
    firstProtrusionPx: Float,
    secondProtrusionPx: Float,
    screenEdge: ScreenEdge
) {
    path.reset()
    if (spreadPx <= 0f) return
    val cMin = minOf(firstCenterPx, secondCenterPx)
    val cMax = maxOf(firstCenterPx, secondCenterPx)
    val pMin = if (firstCenterPx <= secondCenterPx) firstProtrusionPx else secondProtrusionPx
    val pMax = if (firstCenterPx <= secondCenterPx) secondProtrusionPx else firstProtrusionPx
    val maxP = maxOf(pMin, pMax)
    if (maxP <= 0.25f) return

    val minP = minOf(pMin, pMax)
    val mergeRatio = (minP / maxP).coerceIn(0f, 1f)
    val bridgeP = maxP * mergeRatio
    val halfSpread = spreadPx * 0.5f
    val shoulderLen = (halfSpread * 0.92f).coerceAtLeast(1f)
    val startPos = cMin - halfSpread
    val endPos = cMax + halfSpread
    val midPos = (cMin + cMax) * 0.5f
    val halfGap = ((cMax - cMin) * 0.5f).coerceAtLeast(1f)

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val isRight = screenEdge == ScreenEdge.RIGHT
            val edgeX = if (isRight) canvasWidth else 0f
            val dir = if (isRight) -1f else 1f
            val x1 = edgeX + dir * pMin
            val x2 = edgeX + dir * pMax
            val xMid = edgeX + dir * bridgeP

            path.moveTo(edgeX, startPos)
            path.cubicTo(
                edgeX,
                startPos + shoulderLen * 0.48f,
                x1,
                cMin - shoulderLen * 0.34f,
                x1,
                cMin
            )
            if (mergeRatio >= 0.96f && abs(pMin - pMax) <= 0.5f) {
                path.lineTo(x2, cMax)
            } else {
                path.cubicTo(
                    x1,
                    cMin + halfGap * 0.42f,
                    xMid,
                    midPos - halfGap * 0.42f,
                    xMid,
                    midPos
                )
                path.cubicTo(
                    xMid,
                    midPos + halfGap * 0.42f,
                    x2,
                    cMax - halfGap * 0.42f,
                    x2,
                    cMax
                )
            }
            path.cubicTo(
                x2,
                cMax + shoulderLen * 0.34f,
                edgeX,
                endPos - shoulderLen * 0.48f,
                edgeX,
                endPos
            )
            path.close()
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val isTop = screenEdge == ScreenEdge.TOP
            val edgeY = if (isTop) 0f else canvasHeight
            val dir = if (isTop) 1f else -1f
            val y1 = edgeY + dir * pMin
            val y2 = edgeY + dir * pMax
            val yMid = edgeY + dir * bridgeP

            path.moveTo(startPos, edgeY)
            path.cubicTo(
                startPos + shoulderLen * 0.48f,
                edgeY,
                cMin - shoulderLen * 0.34f,
                y1,
                cMin,
                y1
            )
            if (mergeRatio >= 0.96f && abs(pMin - pMax) <= 0.5f) {
                path.lineTo(cMax, y2)
            } else {
                path.cubicTo(
                    cMin + halfGap * 0.42f,
                    y1,
                    midPos - halfGap * 0.42f,
                    yMid,
                    midPos,
                    yMid
                )
                path.cubicTo(
                    midPos + halfGap * 0.42f,
                    yMid,
                    cMax - halfGap * 0.42f,
                    y2,
                    cMax,
                    y2
                )
            }
            path.cubicTo(
                cMax + shoulderLen * 0.34f,
                y2,
                endPos - shoulderLen * 0.48f,
                edgeY,
                endPos,
                edgeY
            )
            path.close()
        }
    }
}

fun buildFlatTopEdgePopPath(
    path: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    startPosPx: Float,
    endPosPx: Float,
    protrusionPx: Float,
    screenEdge: ScreenEdge
) {
    path.reset()
    if (protrusionPx <= 0.25f || startPosPx >= endPosPx) return
    val totalSpanPx = endPosPx - startPosPx
    val shoulderLen = (totalSpanPx * 0.22f).coerceIn(4f, (totalSpanPx * 0.38f).coerceAtLeast(4f))
    val c1 = 0.50f

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val isRight = screenEdge == ScreenEdge.RIGHT
            val edgeX = if (isRight) canvasWidth else 0f
            val dir = if (isRight) -1f else 1f
            val apexX = edgeX + dir * protrusionPx

            val topShoulderEnd = startPosPx + shoulderLen
            val bottomShoulderStart = endPosPx - shoulderLen

            path.moveTo(edgeX, startPosPx)
            path.cubicTo(
                edgeX,
                startPosPx + shoulderLen * c1,
                apexX,
                topShoulderEnd - shoulderLen * c1,
                apexX,
                topShoulderEnd
            )
            path.lineTo(apexX, bottomShoulderStart)
            path.cubicTo(
                apexX,
                bottomShoulderStart + shoulderLen * c1,
                edgeX,
                endPosPx - shoulderLen * c1,
                edgeX,
                endPosPx
            )
            path.close()
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val isTop = screenEdge == ScreenEdge.TOP
            val edgeY = if (isTop) 0f else canvasHeight
            val dir = if (isTop) 1f else -1f
            val apexY = edgeY + dir * protrusionPx

            val leftShoulderEnd = startPosPx + shoulderLen
            val rightShoulderStart = endPosPx - shoulderLen

            path.moveTo(startPosPx, edgeY)
            path.cubicTo(
                startPosPx + shoulderLen * c1,
                edgeY,
                leftShoulderEnd - shoulderLen * c1,
                apexY,
                leftShoulderEnd,
                apexY
            )
            path.lineTo(rightShoulderStart, apexY)
            path.cubicTo(
                rightShoulderStart + shoulderLen * c1,
                apexY,
                endPosPx - shoulderLen * c1,
                edgeY,
                endPosPx,
                edgeY
            )
            path.close()
        }
    }
}

data class FlatPopItem(
    val startPx: Float,
    val endPx: Float,
    val protrusionPx: Float
)

fun mergeFlatPopItems(items: List<FlatPopItem>): List<FlatPopItem> {
    if (items.size <= 1) return items
    val merged = items.toMutableList()
    var changed = true
    while (changed) {
        changed = false
        outer@ for (i in 0 until merged.size) {
            for (j in i + 1 until merged.size) {
                val a = merged[i]
                val b = merged[j]
                val hA = a.endPx - a.startPx
                val hB = b.endPx - b.startPx
                val overlap = maxOf(0f, minOf(a.endPx, b.endPx) - maxOf(a.startPx, b.startPx))
                val minH = minOf(hA, hB)
                val shouldMerge = (minH > 0f && (overlap / minH) >= 0.40f) ||
                    (abs(a.startPx - b.startPx) < 1f && abs(a.endPx - b.endPx) < 1f)
                if (shouldMerge) {
                    val combined = FlatPopItem(
                        startPx = minOf(a.startPx, b.startPx),
                        endPx = maxOf(a.endPx, b.endPx),
                        protrusionPx = maxOf(a.protrusionPx, b.protrusionPx)
                    )
                    merged.removeAt(j)
                    merged[i] = combined
                    changed = true
                    break@outer
                }
            }
        }
    }
    return merged
}

@Composable
fun EdgePopStripOverlay(
    settings: NudgeSettings,
    screenEdge: ScreenEdge,
    displayRotation: Int,
    upExpanded: Boolean,
    downExpanded: Boolean,
    powerExpanded: Boolean,
    upPulseTrigger: Int = 0,
    downPulseTrigger: Int = 0,
    powerPulseTrigger: Int = 0,
    resetEpoch: Int = 0,
    snapHideUp: Boolean = false,
    snapHideDown: Boolean = false,
    snapHidePower: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val upProgress = remember { Animatable(if (upExpanded && !snapHideUp) 1f else 0f) }
    val downProgress = remember { Animatable(if (downExpanded && !snapHideDown) 1f else 0f) }
    val powerProgress = remember { Animatable(if (powerExpanded && !snapHidePower) 1f else 0f) }
    val scratchPath = remember { Path() }

    LaunchedEffect(resetEpoch) {
        if (resetEpoch > 0) {
            launch { upProgress.snapTo(0f) }
            launch { downProgress.snapTo(0f) }
            launch { powerProgress.snapTo(0f) }
        }
    }

    LaunchedEffect(upExpanded, snapHideUp, resetEpoch) {
        if (snapHideUp) {
            upProgress.snapTo(0f)
        } else if (upExpanded) {
            upProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 950f)
            )
        } else {
            upProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 440f)
            )
        }
    }

    LaunchedEffect(downExpanded, snapHideDown, resetEpoch) {
        if (snapHideDown) {
            downProgress.snapTo(0f)
        } else if (downExpanded) {
            downProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 950f)
            )
        } else {
            downProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 440f)
            )
        }
    }

    LaunchedEffect(powerExpanded, snapHidePower, resetEpoch) {
        if (snapHidePower) {
            powerProgress.snapTo(0f)
        } else if (powerExpanded) {
            powerProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 950f)
            )
        } else {
            powerProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 440f)
            )
        }
    }

    LaunchedEffect(upPulseTrigger) {
        if (upPulseTrigger > 0 && upExpanded) {
            upProgress.animateTo(1.09f, spring(dampingRatio = 0.65f, stiffness = 1200f))
            upProgress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f))
        }
    }

    LaunchedEffect(downPulseTrigger) {
        if (downPulseTrigger > 0 && downExpanded) {
            downProgress.animateTo(1.09f, spring(dampingRatio = 0.65f, stiffness = 1200f))
            downProgress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f))
        }
    }

    LaunchedEffect(powerPulseTrigger) {
        if (powerPulseTrigger > 0 && powerExpanded) {
            powerProgress.animateTo(1.09f, spring(dampingRatio = 0.65f, stiffness = 1200f))
            powerProgress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f))
        }
    }

    Canvas(modifier = modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val axisLen = if (isVertical) size.height else size.width
        val volSpreadPx = settings.popHeightDp.dp.toPx()
        val volProtrusionBasePx = settings.popWidthDp.dp.toPx()

        val upCenterPx = clampCenterInsideAxis(
            rawCenter = EdgeOrientationMapper.computeElementCenterPx(
                portraitYFraction = settings.volumeUpYFraction,
                rotation = displayRotation,
                axisLengthPx = axisLen
            ),
            spreadPx = volSpreadPx,
            axisLengthPx = axisLen
        )
        val downCenterPx = clampCenterInsideAxis(
            rawCenter = EdgeOrientationMapper.computeElementCenterPx(
                portraitYFraction = settings.volumeDownYFraction,
                rotation = displayRotation,
                axisLengthPx = axisLen
            ),
            spreadPx = volSpreadPx,
            axisLengthPx = axisLen
        )

        val upP = (volProtrusionBasePx * upProgress.value).coerceAtLeast(0f)
        val downP = (volProtrusionBasePx * downProgress.value).coerceAtLeast(0f)
        val powerSpreadPx = if (settings.enablePowerButtonPop) settings.powerPopHeightDp.dp.toPx() else 0f
        val powerProtrusionPx = if (settings.enablePowerButtonPop) {
            (settings.powerPopWidthDp.dp.toPx() * powerProgress.value).coerceAtLeast(0f)
        } else 0f
        val powerCenterPx = if (settings.enablePowerButtonPop && powerProtrusionPx > 0.25f) {
            clampCenterInsideAxis(
                rawCenter = EdgeOrientationMapper.computeElementCenterPx(
                    portraitYFraction = settings.powerButtonYFraction,
                    rotation = displayRotation,
                    axisLengthPx = axisLen
                ),
                spreadPx = powerSpreadPx,
                axisLengthPx = axisLen
            )
        } else 0f

        if (settings.edgePopStyle == EdgePopStyle.FLAT_TOP) {
            val activeItems = mutableListOf<FlatPopItem>()
            if (upP > 0.25f) {
                activeItems.add(
                    FlatPopItem(
                        startPx = upCenterPx - volSpreadPx * 0.5f,
                        endPx = upCenterPx + volSpreadPx * 0.5f,
                        protrusionPx = upP
                    )
                )
            }
            if (downP > 0.25f) {
                activeItems.add(
                    FlatPopItem(
                        startPx = downCenterPx - volSpreadPx * 0.5f,
                        endPx = downCenterPx + volSpreadPx * 0.5f,
                        protrusionPx = downP
                    )
                )
            }
            if (settings.enablePowerButtonPop && powerProtrusionPx > 0.25f) {
                activeItems.add(
                    FlatPopItem(
                        startPx = powerCenterPx - powerSpreadPx * 0.5f,
                        endPx = powerCenterPx + powerSpreadPx * 0.5f,
                        protrusionPx = powerProtrusionPx
                    )
                )
            }

            val mergedItems = mergeFlatPopItems(activeItems)
            for (item in mergedItems) {
                buildFlatTopEdgePopPath(
                    path = scratchPath,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    startPosPx = item.startPx,
                    endPosPx = item.endPx,
                    protrusionPx = item.protrusionPx,
                    screenEdge = screenEdge
                )
                if (settings.edgePopFlatTopGlowEnabled) {
                    drawFlatTopGlowLine(
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        startPosPx = item.startPx,
                        endPosPx = item.endPx,
                        protrusionPx = item.protrusionPx,
                        screenEdge = screenEdge
                    )
                }
                drawPath(path = scratchPath, color = Color.Black)
            }
        } else {
            val gapPx = abs(upCenterPx - downCenterPx) - volSpreadPx
            val shouldMergeVolume = upP > 0.25f &&
                downP > 0.25f &&
                gapPx <= 28.dp.toPx()

            if (shouldMergeVolume) {
                buildMergedEdgePopPath(
                    path = scratchPath,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    firstCenterPx = upCenterPx,
                    secondCenterPx = downCenterPx,
                    spreadPx = volSpreadPx,
                    firstProtrusionPx = upP,
                    secondProtrusionPx = downP,
                    screenEdge = screenEdge
                )
                drawPath(path = scratchPath, color = Color.Black)
            } else {
                if (upP > 0.25f) {
                    drawSinglePopAtCenter(
                        path = scratchPath,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        centerPx = upCenterPx,
                        spreadPx = volSpreadPx,
                        protrusionPx = upP,
                        screenEdge = screenEdge
                    )
                }
                if (downP > 0.25f) {
                    drawSinglePopAtCenter(
                        path = scratchPath,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        centerPx = downCenterPx,
                        spreadPx = volSpreadPx,
                        protrusionPx = downP,
                        screenEdge = screenEdge
                    )
                }
            }

            if (settings.enablePowerButtonPop && powerProtrusionPx > 0.25f) {
                drawSinglePopAtCenter(
                    path = scratchPath,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    centerPx = powerCenterPx,
                    spreadPx = powerSpreadPx,
                    protrusionPx = powerProtrusionPx,
                    screenEdge = screenEdge
                )
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlatTopGlowLine(
    canvasWidth: Float,
    canvasHeight: Float,
    startPosPx: Float,
    endPosPx: Float,
    protrusionPx: Float,
    screenEdge: ScreenEdge
) {
    if (protrusionPx <= 0.25f || startPosPx >= endPosPx) return
    val totalSpanPx = endPosPx - startPosPx
    val shoulderLen = (totalSpanPx * 0.22f).coerceIn(4f, (totalSpanPx * 0.38f).coerceAtLeast(4f))
    val peakAlpha = 0.22f
    val peakColor = android.graphics.Color.argb((peakAlpha * 255).toInt(), 255, 255, 255)
    val transparentColor = android.graphics.Color.argb(0, 255, 255, 255)

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val isRight = screenEdge == ScreenEdge.RIGHT
            val edgeX = if (isRight) canvasWidth else 0f
            val dir = if (isRight) -1f else 1f
            val apexX = edgeX + dir * protrusionPx

            val topShoulderEnd = startPosPx + shoulderLen
            val bottomShoulderStart = endPosPx - shoulderLen

            val q0X = 0.5f * edgeX + 0.5f * apexX
            val q0Y = startPosPx + 0.5f * shoulderLen

            val q1X = 0.25f * edgeX + 0.75f * apexX
            val q1Y = topShoulderEnd - 0.375f * shoulderLen

            val q2X = apexX
            val q2Y = topShoulderEnd - 0.25f * shoulderLen

            val q3X = apexX
            val q3Y = topShoulderEnd

            val b0X = apexX
            val b0Y = bottomShoulderStart

            val b1X = apexX
            val b1Y = bottomShoulderStart + 0.25f * shoulderLen

            val b2X = 0.75f * apexX + 0.25f * edgeX
            val b2Y = bottomShoulderStart + 0.375f * shoulderLen

            val b3X = 0.5f * apexX + 0.5f * edgeX
            val b3Y = endPosPx - 0.5f * shoulderLen

            val glowLen = b3Y - q0Y
            if (glowLen <= 2f) return

            val glowPath = android.graphics.Path().apply {
                moveTo(q0X, q0Y)
                cubicTo(q1X, q1Y, q2X, q2Y, q3X, q3Y)
                lineTo(b0X, b0Y)
                cubicTo(b1X, b1Y, b2X, b2Y, b3X, b3Y)
            }

            val frac = ((0.5f * shoulderLen) / glowLen).coerceIn(0.08f, 0.40f)

            val shader = android.graphics.LinearGradient(
                apexX, q0Y,
                apexX, b3Y,
                intArrayOf(transparentColor, peakColor, peakColor, transparentColor),
                floatArrayOf(0f, frac, 1f - frac, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                this.shader = shader
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.8.dp.toPx()
                strokeCap = android.graphics.Paint.Cap.ROUND
                maskFilter = android.graphics.BlurMaskFilter(2.2.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(glowPath, paint)
            }
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val isTop = screenEdge == ScreenEdge.TOP
            val edgeY = if (isTop) 0f else canvasHeight
            val dir = if (isTop) 1f else -1f
            val apexY = edgeY + dir * protrusionPx

            val leftShoulderEnd = startPosPx + shoulderLen
            val rightShoulderStart = endPosPx - shoulderLen

            val q0X = startPosPx + 0.5f * shoulderLen
            val q0Y = 0.5f * edgeY + 0.5f * apexY

            val q1X = leftShoulderEnd - 0.375f * shoulderLen
            val q1Y = 0.25f * edgeY + 0.75f * apexY

            val q2X = leftShoulderEnd - 0.25f * shoulderLen
            val q2Y = apexY

            val q3X = leftShoulderEnd
            val q3Y = apexY

            val b0X = rightShoulderStart
            val b0Y = apexY

            val b1X = rightShoulderStart + 0.25f * shoulderLen
            val b1Y = apexY

            val b2X = rightShoulderStart + 0.375f * shoulderLen
            val b2Y = 0.75f * apexY + 0.25f * edgeY

            val b3X = endPosPx - 0.5f * shoulderLen
            val b3Y = 0.5f * apexY + 0.5f * edgeY

            val glowLen = b3X - q0X
            if (glowLen <= 2f) return

            val glowPath = android.graphics.Path().apply {
                moveTo(q0X, q0Y)
                cubicTo(q1X, q1Y, q2X, q2Y, q3X, q3Y)
                lineTo(b0X, b0Y)
                cubicTo(b1X, b1Y, b2X, b2Y, b3X, b3Y)
            }

            val frac = ((0.5f * shoulderLen) / glowLen).coerceIn(0.08f, 0.40f)

            val shader = android.graphics.LinearGradient(
                q0X, apexY,
                b3X, apexY,
                intArrayOf(transparentColor, peakColor, peakColor, transparentColor),
                floatArrayOf(0f, frac, 1f - frac, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                this.shader = shader
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.8.dp.toPx()
                strokeCap = android.graphics.Paint.Cap.ROUND
                maskFilter = android.graphics.BlurMaskFilter(2.2.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(glowPath, paint)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSinglePopAtCenter(
    path: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    centerPx: Float,
    spreadPx: Float,
    protrusionPx: Float,
    screenEdge: ScreenEdge
) {
    val startPx = centerPx - spreadPx * 0.5f
    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            translate(left = 0f, top = startPx) {
                buildEdgePopPath(
                    path = path,
                    canvasWidth = canvasWidth,
                    canvasHeight = spreadPx,
                    protrusionPx = protrusionPx,
                    screenEdge = screenEdge
                )
                drawPath(path = path, color = Color.Black)
            }
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            translate(left = startPx, top = 0f) {
                buildEdgePopPath(
                    path = path,
                    canvasWidth = spreadPx,
                    canvasHeight = canvasHeight,
                    protrusionPx = protrusionPx,
                    screenEdge = screenEdge
                )
                drawPath(path = path, color = Color.Black)
            }
        }
    }
}

private fun clampCenterInsideAxis(
    rawCenter: Float,
    spreadPx: Float,
    axisLengthPx: Float
): Float {
    val half = spreadPx * 0.5f
    if (axisLengthPx <= spreadPx) return axisLengthPx * 0.5f
    return rawCenter.coerceIn(half, axisLengthPx - half)
}
