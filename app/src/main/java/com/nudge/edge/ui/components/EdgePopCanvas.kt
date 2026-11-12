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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.EdgeOrientationMapper
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val upProgress = remember { Animatable(if (upExpanded) 1f else 0f) }
    val downProgress = remember { Animatable(if (downExpanded) 1f else 0f) }
    val powerProgress = remember { Animatable(if (powerExpanded) 1f else 0f) }
    val scratchPath = remember { Path() }

    LaunchedEffect(resetEpoch) {
        if (resetEpoch > 0) {
            launch { upProgress.snapTo(0f) }
            launch { downProgress.snapTo(0f) }
            launch { powerProgress.snapTo(0f) }
        }
    }

    LaunchedEffect(upExpanded, resetEpoch) {
        if (upExpanded) {
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

    LaunchedEffect(downExpanded, resetEpoch) {
        if (downExpanded) {
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

    LaunchedEffect(powerExpanded, resetEpoch) {
        if (powerExpanded) {
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

    Canvas(modifier = modifier.fillMaxSize()) {
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
        val delta = abs(upCenterPx - downCenterPx)
        val shouldMergeVolume = upP > 0.25f &&
            downP > 0.25f &&
            delta < (volSpreadPx * 2f)

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

        if (settings.enablePowerButtonPop) {
            val powerSpreadPx = settings.powerPopHeightDp.dp.toPx()
            val powerProtrusionPx = (settings.powerPopWidthDp.dp.toPx() * powerProgress.value)
                .coerceAtLeast(0f)
            if (powerProtrusionPx > 0.25f) {
                val powerCenterPx = clampCenterInsideAxis(
                    rawCenter = EdgeOrientationMapper.computeElementCenterPx(
                        portraitYFraction = settings.powerButtonYFraction,
                        rotation = displayRotation,
                        axisLengthPx = axisLen
                    ),
                    spreadPx = powerSpreadPx,
                    axisLengthPx = axisLen
                )
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
