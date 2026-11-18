package com.nudge.edge.ui.components

import kotlin.math.roundToInt
import android.graphics.BlurMaskFilter
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.view.View
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.nudge.edge.service.OverlayBlurController
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

fun computeHudOuterSpanDp(pillHeightDp: Float, fluidityPercent: Int? = null): Float {
    return computeHudDrawnPillSpanDp(pillHeightDp) + (HUD_CUTOUT_FILLET_TAIL_DP + HUD_CUTOUT_INNER_PAD_DP) * 2f
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
    filletTailPx: Float,
    curvaturePercent: Int = 75
) {
    path.reset()
    if (width <= 0f || height <= 0f) return
    val effectiveFilletTailPx = filletTailPx

    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val filletHeight = effectiveFilletTailPx.coerceAtMost(height * 0.25f)
            val maxOuterRadius = minOf(
                width * 0.85f,
                height * 0.5f - filletHeight
            ).coerceAtLeast(1f)
            val outerRadius = (pillCornerRadiusPx + innerPadPx).coerceIn(
                minOf(innerPadPx, maxOuterRadius).coerceAtLeast(1f),
                maxOuterRadius
            )
            val isRight = screenEdge == ScreenEdge.RIGHT
            val bleed = 6f
            val bezelX = if (isRight) width + bleed else -bleed
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
            val tangentLength = width * 0.36f
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
            val filletSpan = effectiveFilletTailPx.coerceAtMost(width * 0.25f)
            val maxOuterRadius = minOf(
                height * 0.85f,
                width * 0.5f - filletSpan
            ).coerceAtLeast(1f)
            val outerRadius = (pillCornerRadiusPx + innerPadPx).coerceIn(
                minOf(innerPadPx, maxOuterRadius).coerceAtLeast(1f),
                maxOuterRadius
            )
            val isBottom = screenEdge == ScreenEdge.BOTTOM
            val bleed = 6f
            val bezelY = if (isBottom) height + bleed else -bleed
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
            val tangentLength = height * 0.36f
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
    cutoutColor: Color = Color.Transparent
) {
    drawCustomSpeakerIcon(
        center = center,
        size = glyphSize,
        color = color,
        volumeFraction = volumeFraction,
        pathBuffer = pathBuffer
    )
}

fun DrawScope.drawCustomSpeakerIcon(
    center: Offset,
    size: Float,
    color: Color,
    volumeFraction: Float,
    pathBuffer: Path,
    horizontalShiftUnits: Float = 0f,
    wave1Alpha: Float = if (volumeFraction >= 0.01f) 1f else 0f,
    wave1Scale: Float = if (volumeFraction >= 0.01f) 1f else 0.85f,
    wave2Alpha: Float = if (volumeFraction >= 0.34f) 1f else 0f,
    wave2Scale: Float = if (volumeFraction >= 0.34f) 1f else 0.85f,
    wave3Alpha: Float = if (volumeFraction >= 0.67f) 1f else 0f,
    wave3Scale: Float = if (volumeFraction >= 0.67f) 1f else 0.85f
) {
    val s = size / 24f
    val ox = center.x - 12f * s + horizontalShiftUnits * s
    val oy = center.y - 12f * s

    pathBuffer.reset()
    pathBuffer.moveTo(ox + 2.5f * s, oy + 9.0f * s)
    pathBuffer.lineTo(ox + 6.0f * s, oy + 9.0f * s)
    pathBuffer.lineTo(ox + 10.5f * s, oy + 4.8f * s)
    pathBuffer.lineTo(ox + 10.5f * s, oy + 19.2f * s)
    pathBuffer.lineTo(ox + 6.0f * s, oy + 15.0f * s)
    pathBuffer.lineTo(ox + 2.5f * s, oy + 15.0f * s)
    pathBuffer.close()

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color
            this.isAntiAlias = true
            this.pathEffect = PathEffect.cornerPathEffect(1.6f * s)
        }
        canvas.drawPath(pathBuffer, paint)
    }

    val strokeWidth = 1.6f * s
    val arcCenter = Offset(ox + 9.6f * s, oy + 12.0f * s)
    val waveStartAngle = -38f
    val waveSweepAngle = 76f

    if (volumeFraction < 0.01f) {
        val xCenter = Offset(ox + 15.5f * s, oy + 12.0f * s)
        val arm = 2.8f * s
        val xStrokeWidth = 1.9f * s
        drawLine(
            color = color,
            start = Offset(xCenter.x - arm, xCenter.y - arm),
            end = Offset(xCenter.x + arm, xCenter.y + arm),
            strokeWidth = xStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(xCenter.x - arm, xCenter.y + arm),
            end = Offset(xCenter.x + arm, xCenter.y - arm),
            strokeWidth = xStrokeWidth,
            cap = StrokeCap.Round
        )
    }

    if (wave1Alpha > 0.005f) {
        val r1 = 5.2f * s * wave1Scale.coerceIn(0.5f, 1.5f)
        drawArc(
            color = color.copy(alpha = color.alpha * wave1Alpha.coerceIn(0f, 1f)),
            startAngle = waveStartAngle,
            sweepAngle = waveSweepAngle,
            useCenter = false,
            topLeft = Offset(arcCenter.x - r1, arcCenter.y - r1),
            size = Size(r1 * 2f, r1 * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    if (wave2Alpha > 0.005f) {
        val r2 = 8.2f * s * wave2Scale.coerceIn(0.5f, 1.5f)
        drawArc(
            color = color.copy(alpha = color.alpha * wave2Alpha.coerceIn(0f, 1f)),
            startAngle = waveStartAngle,
            sweepAngle = waveSweepAngle,
            useCenter = false,
            topLeft = Offset(arcCenter.x - r2, arcCenter.y - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    if (wave3Alpha > 0.005f) {
        val r3 = 11.2f * s * wave3Scale.coerceIn(0.5f, 1.5f)
        drawArc(
            color = color.copy(alpha = color.alpha * wave3Alpha.coerceIn(0f, 1f)),
            startAngle = waveStartAngle,
            sweepAngle = waveSweepAngle,
            useCenter = false,
            topLeft = Offset(arcCenter.x - r3, arcCenter.y - r3),
            size = Size(r3 * 2f, r3 * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawDynamicSliderIcon(
    center: Offset,
    iconSize: Float,
    displayedFraction: Float,
    isBluetoothConnected: Boolean,
    inCall: Boolean,
    isMediaPlayingNow: Boolean,
    activeStreamType: Int,
    darkColor: Color,
    whiteColor: Color,
    isWhiteSection: Boolean,
    callPainter: VectorPainter,
    pathBuffer: Path,
    horizontalShiftUnits: Float,
    wave1Alpha: Float,
    wave1Scale: Float,
    wave2Alpha: Float,
    wave2Scale: Float,
    wave3Alpha: Float,
    wave3Scale: Float,
    customAppIcon: Drawable? = null
) {
    if (customAppIcon != null) {
        val bitmap = customAppIcon.toBitmap(iconSize.toInt().coerceAtLeast(1), iconSize.toInt().coerceAtLeast(1)).asImageBitmap()
        drawImage(
            image = bitmap,
            topLeft = Offset(center.x - iconSize * 0.5f, center.y - iconSize * 0.5f)
        )
        return
    }
    val isBt = isBluetoothConnected
    val isCall = (inCall && !isMediaPlayingNow) || activeStreamType == AudioManager.STREAM_VOICE_CALL
    val tintColor = if (isBt) Color(0xFFFF453A) else if (isWhiteSection) whiteColor else darkColor

    if (isCall) {
        translate(left = center.x - iconSize * 0.5f, top = center.y - iconSize * 0.5f) {
            with(callPainter) {
                draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(tintColor))
            }
        }
    } else {
        drawCustomSpeakerIcon(
            center = center,
            size = iconSize,
            color = tintColor,
            volumeFraction = displayedFraction,
            pathBuffer = pathBuffer,
            horizontalShiftUnits = horizontalShiftUnits,
            wave1Alpha = wave1Alpha,
            wave1Scale = wave1Scale,
            wave2Alpha = wave2Alpha,
            wave2Scale = wave2Scale,
            wave3Alpha = wave3Alpha,
            wave3Scale = wave3Scale
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
    isBluetoothConnected: Boolean = false,
    activeStreamType: Int = AudioManager.STREAM_MUSIC,
    inCall: Boolean = false,
    isMediaPlayingNow: Boolean = false,
    volumeSliderFluidityPercent: Int = 75,
    maxVolumeLimit: Int = 100,
    customAppIcon: Drawable? = null,
    onVolumeScrub: (Float) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    onIconLongClick: () -> Unit = {},
    interactive: Boolean = true,
    instantAppearance: Boolean = false,
    showVolumeCounter: Boolean = false,
    pillTransparency: Int = 0,
    volumeSliderBlur: Float = 0f,
    pillBlurDp: Float = 0f,
    isMuteAllSounds: Boolean = false,
    isThin: Boolean = false,
    isHoldingVolUpAtMax: Boolean = false,
    isHoldingVolDownAtMin: Boolean = false,
    extraBezelDistancePx: Float = 0f,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val blurRadiusPx = OverlayBlurController.computeBlurRadiusPx(volumeSliderBlur, density.density)
    val isRuntimeBlurActive = OverlayBlurController.isRuntimeBlurActive(context)
    val isSamsungActive = OverlayBlurController.isSamsungBackendActive()
    val coroutineScope = rememberCoroutineScope()
    val slideProgress = remember { Animatable(if (isVisible) 1f else 0f) }
    val housingPath = remember { Path() }
    val pillPath = remember { Path() }
    val speakerPath = remember { Path() }

    val callPainter = rememberVectorPainter(Icons.Rounded.Call)

    val isCall = activeStreamType == AudioManager.STREAM_VOICE_CALL
    val maxCallSteps = (maxVolumeLimit / 10).coerceAtLeast(1)
    val minFraction = if (isCall) (1f / maxCallSteps.toFloat()).coerceIn(0f, 1f) else 0f

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(volumeFraction.coerceIn(minFraction, 1f)) }
    var isOverMaxScrub by remember { mutableStateOf(false) }
    var isOverMinScrub by remember { mutableStateOf(false) }

    LaunchedEffect(volumeFraction, isScrubbing) {
        if (!isScrubbing) {
            scrubFraction = volumeFraction.coerceIn(minFraction, 1f)
        }
    }

    val targetWidth = if (isThin) 7f else hudWidthDp
    val animatedWidthDp by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "hudWidth"
    )
    val effectiveFloating = floatingMode || isThin
    val floatProgress by animateFloatAsState(
        targetValue = if (effectiveFloating) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        label = "hudFloatMode"
    )
    val targetEdgeInset = if (isThin) 6f else (if (floatingMode) edgeInsetDp else 0f)
    val animatedInsetDp by animateFloatAsState(
        targetValue = targetEdgeInset.coerceAtLeast(0f),
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "hudEdgeInset"
    )
    val targetCornerRadius = if (isThin) animatedWidthDp * 0.5f else hudCornerRadiusDp
    val animatedCornerRadiusDp by animateFloatAsState(
        targetValue = targetCornerRadius,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "hudCornerRadius"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isThin) 0f else 1f,
        animationSpec = tween(120),
        label = "hudIconAlpha"
    )

    val isOverMax = (isHoldingVolUpAtMax && volumeFraction >= 0.999f) || isOverMaxScrub
    val isOverMin = (isHoldingVolDownAtMin && volumeFraction <= 0.001f) || isOverMinScrub

    val stretchScaleMax by animateFloatAsState(
        targetValue = if (isOverMax) 1.08f else 1.0f,
        animationSpec = if (isOverMax) {
            spring(dampingRatio = 0.75f, stiffness = 280f)
        } else {
            spring(dampingRatio = 0.80f, stiffness = 340f)
        },
        label = "hudStretchMax"
    )

    val stretchScaleMin by animateFloatAsState(
        targetValue = if (isOverMin) 1.08f else 1.0f,
        animationSpec = if (isOverMin) {
            spring(dampingRatio = 0.75f, stiffness = 280f)
        } else {
            spring(dampingRatio = 0.80f, stiffness = 340f)
        },
        label = "hudStretchMin"
    )

    val targetNormalizedVolume = if (isMuteAllSounds) {
        0f
    } else if (isScrubbing) {
        scrubFraction
    } else {
        volumeFraction.coerceIn(minFraction, 1f)
    }
    val animatedVolume by animateFloatAsState(
        targetValue = targetNormalizedVolume,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SmoothVolumeScrub"
    )
    val displayedFraction = if (isMuteAllSounds) 0f else animatedVolume.coerceIn(minFraction, 1f)
    val volumePercent = (displayedFraction * 100f).roundToInt()
    val activeWaveCount = when {
        volumePercent <= 0 -> 0
        volumePercent <= 33 -> 1
        volumePercent <= 66 -> 2
        else -> 3
    }
    val targetCenterShiftUnits = when (activeWaveCount) {
        0 -> 0.8f
        1 -> 3.3f
        2 -> 2.0f
        else -> 0.7f
    }
    val animatedShiftUnits by animateFloatAsState(
        targetValue = targetCenterShiftUnits,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SoundIconRecenter"
    )
    val wave1Alpha by animateFloatAsState(
        targetValue = if (activeWaveCount >= 1) 1f else 0f,
        animationSpec = tween(150),
        label = "hudWave1Alpha"
    )
    val wave1Scale by animateFloatAsState(
        targetValue = if (activeWaveCount >= 1) 1f else 0.85f,
        animationSpec = tween(150),
        label = "hudWave1Scale"
    )
    val wave2Alpha by animateFloatAsState(
        targetValue = if (activeWaveCount >= 2) 1f else 0f,
        animationSpec = tween(150),
        label = "hudWave2Alpha"
    )
    val wave2Scale by animateFloatAsState(
        targetValue = if (activeWaveCount >= 2) 1f else 0.85f,
        animationSpec = tween(150),
        label = "hudWave2Scale"
    )
    val wave3Alpha by animateFloatAsState(
        targetValue = if (activeWaveCount >= 3) 1f else 0f,
        animationSpec = tween(150),
        label = "hudWave3Alpha"
    )
    val wave3Scale by animateFloatAsState(
        targetValue = if (activeWaveCount >= 3) 1f else 0.85f,
        animationSpec = tween(150),
        label = "hudWave3Scale"
    )

    val isVerticalEdge = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
    val maxInsetDp = maxOf(edgeInsetDp.coerceAtLeast(0f), animatedInsetDp)
    val cutoutThicknessDp = computeHudCutoutThicknessDp(hudWidthDp, animatedInsetDp)
    val outerSpanDp = computeHudOuterSpanDp(hudHeightDp, volumeSliderFluidityPercent)
    val drawnPillSpanDp = computeHudDrawnPillSpanDp(hudHeightDp)
    val totalThicknessDp = computeHudCutoutThicknessDp(hudWidthDp, maxInsetDp)
    val containerWidthDp = if (isVerticalEdge) totalThicknessDp else outerSpanDp
    val containerHeightDp = if (isVerticalEdge) outerSpanDp else totalThicknessDp

    val pillStartPx = with(density) { HUD_CUTOUT_PILL_START_OFFSET_DP.dp.toPx() }
    val pillLenPx = with(density) { drawnPillSpanDp.dp.toPx() }
    val pillEndPx = pillStartPx + pillLenPx
    val speakerTapZonePx = with(density) { (hudWidthDp * 1.0f).coerceIn(32f, 48f).dp.toPx() }
    val tapSlopPx = with(density) { 6.dp.toPx() }
    val swipeMenuThresholdPx = with(density) { 14.dp.toPx() }

    val currentOnVolumeScrub by rememberUpdatedState(onVolumeScrub)
    val currentOnScrubStateChanged by rememberUpdatedState(onScrubStateChanged)
    val currentOnOpenMenu by rememberUpdatedState(onOpenMenu)
    val currentOnIconLongClick by rememberUpdatedState(onIconLongClick)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val currentPillStart by rememberUpdatedState(pillStartPx)
    val currentPillEnd by rememberUpdatedState(pillEndPx)
    val currentEdge by rememberUpdatedState(screenEdge)
    val currentInteractive by rememberUpdatedState(interactive && isVisible)
    val currentIsThin by rememberUpdatedState(isThin)
    val currentIsMuteAllSounds by rememberUpdatedState(isMuteAllSounds)
    val currentCustomAppIcon by rememberUpdatedState(customAppIcon)
    val currentMinFraction by rememberUpdatedState(minFraction)

    LaunchedEffect(resetEpoch) {
        if (resetEpoch > 0) {
            slideProgress.snapTo(0f)
        }
    }

    LaunchedEffect(isVisible, resetEpoch) {
        if (isVisible) {
            if (instantAppearance) {
                if (slideProgress.value < 0.05f) {
                    slideProgress.snapTo(0.20f)
                }
                slideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 650f
                    )
                )
            } else {
                if (slideProgress.value > 0.01f) {
                    slideProgress.snapTo(0f)
                }
                slideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 320f
                    )
                )
            }
        } else {
            slideProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 380f
                )
            )
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val wasThinOnDown = currentIsThin
            currentOnUserInteraction()
            if (currentIsMuteAllSounds || !currentInteractive) return@awaitEachGesture
            val downX = down.position.x
            val downY = down.position.y
            val pStart = currentPillStart
            val pEnd = currentPillEnd
            val edge = currentEdge
            val vertical = edge == ScreenEdge.LEFT || edge == ScreenEdge.RIGHT
            val trackSpan = (pEnd - pStart).coerceAtLeast(1f)
            val inSpeakerZone = (currentCustomAppIcon == null) && (if (vertical) {
                downY >= pEnd - speakerTapZonePx
            } else {
                downX <= pStart + speakerTapZonePx
            })

            fun calculateFraction(rawPos: Float): Float {
                if (currentIsMuteAllSounds) return 0f
                return if (vertical) {
                    ((pEnd - rawPos) / trackSpan).coerceIn(currentMinFraction, 1f)
                } else {
                    ((rawPos - pStart) / trackSpan).coerceIn(currentMinFraction, 1f)
                }
            }

            var movedAlongTrack = false
            var isLongPressHandled = false
            var longPressJob: Job? = null
            if (inSpeakerZone && !wasThinOnDown && currentCustomAppIcon == null) {
                longPressJob = coroutineScope.launch {
                    delay(450L)
                    while (isActive && !movedAlongTrack) {
                        isLongPressHandled = true
                        currentOnUserInteraction()
                        currentOnIconLongClick()
                        delay(650L)
                    }
                }
            }

            if (!inSpeakerZone && !currentIsMuteAllSounds) {
                isScrubbing = true
                currentOnScrubStateChanged(true)
                val initial = calculateFraction(if (vertical) downY else downX)
                scrubFraction = initial
                currentOnVolumeScrub(initial)
            }
            down.consume()

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dy = change.position.y - downY
                    val dx = change.position.x - downX
                    val trackDelta = if (vertical) abs(dy) else abs(dx)

                    if (!change.pressed) {
                        longPressJob?.cancel()
                        if (inSpeakerZone && !movedAlongTrack && !isLongPressHandled) {
                            if (!wasThinOnDown && !currentIsThin && currentCustomAppIcon == null) {
                                currentOnOpenMenu()
                            }
                        }
                        change.consume()
                        break
                    }

                    if (trackDelta > tapSlopPx) {
                        movedAlongTrack = true
                        longPressJob?.cancel()
                        if (!isScrubbing) {
                            isScrubbing = true
                            currentOnScrubStateChanged(true)
                        }
                    }

                    if (isScrubbing && !currentIsMuteAllSounds) {
                        val pos = if (vertical) change.position.y else change.position.x
                        val updated = calculateFraction(pos)
                        scrubFraction = updated
                        currentOnVolumeScrub(updated)
                        isOverMaxScrub = if (vertical) change.position.y < pStart else change.position.x > pEnd
                        isOverMinScrub = if (vertical) change.position.y > pEnd else change.position.x < pStart
                    }
                    change.consume()
                }
            } finally {
                longPressJob?.cancel()
                isOverMaxScrub = false
                isOverMinScrub = false
                if (isScrubbing) {
                    isScrubbing = false
                    currentOnScrubStateChanged(false)
                }
            }
        }
    }

    val effectiveSlideProgress = slideProgress.value

    Box(
        modifier = modifier
            .width(containerWidthDp.dp)
            .height(containerHeightDp.dp)
    ) {
        if (isSamsungActive && blurRadiusPx > 0 && isVisible && effectiveSlideProgress > 0.01f) {
            val pillWDp = animatedWidthDp.dp
            val maxStretchExtraDp = if (stretchScaleMax > 1.001f) drawnPillSpanDp * (stretchScaleMax - 1f) else 0f
            val minStretchExtraDp = if (stretchScaleMin > 1.001f) drawnPillSpanDp * (stretchScaleMin - 1f) else 0f
            val totalStretchedPillSpanDp = (drawnPillSpanDp + maxStretchExtraDp + minStretchExtraDp).dp
            val shiftYDp = (minStretchExtraDp - maxStretchExtraDp) * 0.5f
            val shiftXDp = (maxStretchExtraDp - minStretchExtraDp) * 0.5f

            val cornerRadiusPx = with(density) {
                animatedCornerRadiusDp.dp.toPx().coerceIn(
                    0f,
                    minOf(pillWDp.toPx(), totalStretchedPillSpanDp.toPx()) * 0.5f
                )
            }
            val bezelPadDp = (HUD_CUTOUT_BEZEL_PAD_DP * (1f - floatProgress) + animatedInsetDp).coerceAtLeast(0f).dp

            val trackModifier = if (isVerticalEdge) {
                val isRight = screenEdge == ScreenEdge.RIGHT
                Modifier
                    .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(
                        end = if (isRight) bezelPadDp else 0.dp,
                        start = if (!isRight) bezelPadDp else 0.dp
                    )
                    .offset(y = shiftYDp.dp)
                    .width(pillWDp)
                    .height(totalStretchedPillSpanDp)
            } else {
                val isBottom = screenEdge == ScreenEdge.BOTTOM
                Modifier
                    .align(if (isBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(
                        bottom = if (isBottom) bezelPadDp else 0.dp,
                        top = if (!isBottom) bezelPadDp else 0.dp
                    )
                    .offset(x = shiftXDp.dp)
                    .width(totalStretchedPillSpanDp)
                    .height(pillWDp)
            }

            AndroidView(
                factory = { ctx ->
                    View(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view ->
                    OverlayBlurController.applySamsungTrackBlur(
                        view = view,
                        blurRadiusPx = blurRadiusPx,
                        cornerRadiusPx = cornerRadiusPx,
                        isHudVisible = isVisible && effectiveSlideProgress > 0.01f
                    )
                },
                modifier = trackModifier
                    .graphicsLayer {
                        val hidden = 1f - effectiveSlideProgress
                        val totalDistanceX = containerWidthDp.dp.toPx() + extraBezelDistancePx
                        val totalDistanceY = containerHeightDp.dp.toPx() + extraBezelDistancePx
                        val scaleFactor = 0.85f + 0.15f * effectiveSlideProgress
                        when (screenEdge) {
                            ScreenEdge.RIGHT -> {
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                translationX = (totalDistanceX * hidden).coerceAtLeast(0f)
                            }
                            ScreenEdge.LEFT -> {
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = (-totalDistanceX * hidden).coerceAtMost(0f)
                            }
                            ScreenEdge.TOP -> {
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationY = (-totalDistanceY * hidden).coerceAtMost(0f)
                            }
                            ScreenEdge.BOTTOM -> {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                translationY = (totalDistanceY * hidden).coerceAtLeast(0f)
                            }
                        }
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                        alpha = effectiveSlideProgress
                    }
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    val hidden = 1f - effectiveSlideProgress
                    val totalDistanceX = size.width + extraBezelDistancePx
                    val totalDistanceY = size.height + extraBezelDistancePx
                    val scaleFactor = 0.85f + 0.15f * effectiveSlideProgress
                    when (screenEdge) {
                        ScreenEdge.RIGHT -> {
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            translationX = (totalDistanceX * hidden).coerceAtLeast(0f)
                        }
                        ScreenEdge.LEFT -> {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            translationX = (-totalDistanceX * hidden).coerceAtMost(0f)
                        }
                        ScreenEdge.TOP -> {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = (-totalDistanceY * hidden).coerceAtMost(0f)
                        }
                        ScreenEdge.BOTTOM -> {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = (totalDistanceY * hidden).coerceAtLeast(0f)
                        }
                    }
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                    alpha = effectiveSlideProgress
                }
                .then(gestureModifier)
        ) {
            if (effectiveSlideProgress <= 0.01f) return@Canvas

            val pillW = animatedWidthDp.dp.toPx()
            val pillSpan = drawnPillSpanDp.dp.toPx()
            val pillCornerRadius = animatedCornerRadiusDp.dp.toPx().coerceIn(
                0f,
                minOf(pillW, pillSpan) * 0.5f
            )
            val notchAlpha = (1f - floatProgress).coerceIn(0f, 1f)
            val cutoutThicknessPx = cutoutThicknessDp.dp.toPx()
            val bezelPadPx = (HUD_CUTOUT_BEZEL_PAD_DP * (1f - floatProgress) + animatedInsetDp).dp.toPx()
            val innerPadPx = HUD_CUTOUT_INNER_PAD_DP.dp.toPx()
            val filletTailPx = HUD_CUTOUT_FILLET_TAIL_DP.dp.toPx()
            val pillStartOffsetPx = HUD_CUTOUT_PILL_START_OFFSET_DP.dp.toPx()

            if (isVerticalEdge) {
                val isRight = screenEdge == ScreenEdge.RIGHT
                val bezelEdgeX = if (isRight) size.width else 0f
                val inwardDir = if (isRight) -1f else 1f

                val verticalPad = (size.height - pillSpan) * 0.5f
                val pillCenterX = bezelEdgeX + inwardDir * (bezelPadPx + pillW * 0.5f)
                val pillLeft = pillCenterX - pillW * 0.5f
                val pillRight = pillCenterX + pillW * 0.5f
                val basePillTop = verticalPad
                val basePillBottom = verticalPad + pillSpan
                val maxStretchExtra = if (stretchScaleMax > 1.001f) pillSpan * (stretchScaleMax - 1f) else 0f
                val minStretchExtra = if (stretchScaleMin > 1.001f) pillSpan * (stretchScaleMin - 1f) else 0f
                val pillTop = basePillTop - maxStretchExtra
                val pillBottom = basePillBottom + minStretchExtra

                pillPath.reset()
                pillPath.addRoundRect(
                    RoundRect(
                        rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                        cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
                    )
                )

                val housingTop = -maxStretchExtra
                val housingHeight = size.height + maxStretchExtra + minStretchExtra

                if (notchAlpha > 0.01f) {
                    val notchWidth = cutoutThicknessPx * (1f - floatProgress * 0.72f)
                    val notchLeft = if (isRight) bezelEdgeX - notchWidth else 0f
                    translate(left = notchLeft, top = housingTop) {
                        buildHudHousingPath(
                            path = housingPath,
                            width = notchWidth,
                            height = housingHeight,
                            screenEdge = screenEdge,
                            pillCornerRadiusPx = pillCornerRadius,
                            innerPadPx = innerPadPx,
                            filletTailPx = filletTailPx,
                            curvaturePercent = volumeSliderFluidityPercent
                        )
                        drawPath(
                            path = housingPath,
                            color = HudHousingBlack.copy(alpha = notchAlpha)
                        )
                    }
                    drawPath(
                        path = pillPath,
                        color = Color.Black,
                        blendMode = BlendMode.Clear
                    )
                }

                val transFraction = (pillTransparency / 100f).coerceIn(0f, 1f)
                val userUnfilledAlpha = (1f - transFraction).coerceIn(0f, 1f)
                val effectiveUnfilledAlpha = if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                    maxOf(userUnfilledAlpha, 0.72f)
                } else {
                    userUnfilledAlpha
                }
                val fillAlpha = 1f

                if (pillBlurDp > 0.5f) {
                    drawIntoCanvas { canvas ->
                        val blurPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(
                                (effectiveUnfilledAlpha * 180).roundToInt().coerceIn(0, 255),
                                50, 50, 55
                            )
                            maskFilter = BlurMaskFilter(
                                pillBlurDp.dp.toPx(),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        canvas.nativeCanvas.drawPath(pillPath.asAndroidPath(), blurPaint)
                    }
                }

                drawPath(path = pillPath, color = HudTrackCharcoal.copy(alpha = effectiveUnfilledAlpha))
                if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                    drawPath(
                        path = pillPath,
                        color = Color.White.copy(alpha = 0.14f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }

                val currentSpan = pillBottom - pillTop
                val fillTop = (pillBottom - currentSpan * displayedFraction).coerceIn(pillTop, pillBottom)

                clipPath(pillPath) {
                    if (displayedFraction > 0.002f) {
                        drawRect(
                            color = HudFillWhite.copy(alpha = fillAlpha),
                            topLeft = Offset(pillLeft, fillTop),
                            size = Size(pillW, pillBottom - fillTop)
                        )
                    }
                    if (iconAlpha > 0.01f) {
                        val iconSize = (pillW * 0.55f).coerceIn(16.dp.toPx(), 27.dp.toPx())
                        val iconCenter = Offset(
                            x = pillCenterX,
                            y = pillBottom - minOf(pillW * 0.50f, 24.dp.toPx())
                        )
                        val counterSteps = if (isCall) {
                            val minFine = 10
                            (displayedFraction * maxVolumeLimit).roundToInt().coerceIn(minFine, maxVolumeLimit)
                        } else {
                            (displayedFraction * maxVolumeLimit).roundToInt().coerceIn(0, maxVolumeLimit)
                        }
                        val counterText = "$counterSteps"
                        val counterTextSize = (pillW * 0.32f).coerceIn(10.dp.toPx(), 13.dp.toPx())
                        val counterCenterY = pillTop + minOf(pillW * 0.45f, 18.dp.toPx())
                        val speakerColorDark = (if (isBluetoothConnected) Color(0xFFFF453A) else HudIconOnDark).copy(alpha = iconAlpha)
                        val speakerColorWhite = (if (isBluetoothConnected) Color(0xFFFF453A) else HudIconOnWhite).copy(alpha = iconAlpha)
                        if (fillTop > pillTop) {
                            clipRect(pillLeft, pillTop, pillRight, fillTop) {
                                if (showVolumeCounter) {
                                    drawIntoCanvas { canvas ->
                                        val textPaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            textSize = counterTextSize
                                            color = HudIconOnDark.copy(alpha = iconAlpha).toArgb()
                                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        }
                                        val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                        canvas.nativeCanvas.drawText(counterText, pillCenterX, baseline, textPaint)
                                    }
                                }
                                drawDynamicSliderIcon(
                                    center = iconCenter,
                                    iconSize = iconSize,
                                    displayedFraction = displayedFraction,
                                    isBluetoothConnected = isBluetoothConnected,
                                    inCall = inCall,
                                    isMediaPlayingNow = isMediaPlayingNow,
                                    activeStreamType = activeStreamType,
                                    darkColor = speakerColorDark,
                                    whiteColor = speakerColorWhite,
                                    isWhiteSection = false,
                                    callPainter = callPainter,
                                    pathBuffer = housingPath,
                                    horizontalShiftUnits = animatedShiftUnits,
                                    wave1Alpha = wave1Alpha,
                                    wave1Scale = wave1Scale,
                                    wave2Alpha = wave2Alpha,
                                    wave2Scale = wave2Scale,
                                    wave3Alpha = wave3Alpha,
                                    wave3Scale = wave3Scale,
                                    customAppIcon = customAppIcon
                                )
                            }
                        }
                        if (fillTop < pillBottom) {
                            clipRect(pillLeft, fillTop, pillRight, pillBottom) {
                                if (showVolumeCounter) {
                                    drawIntoCanvas { canvas ->
                                        val textPaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            textSize = counterTextSize
                                            color = HudIconOnWhite.copy(alpha = iconAlpha).toArgb()
                                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        }
                                        val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                        canvas.nativeCanvas.drawText(counterText, pillCenterX, baseline, textPaint)
                                    }
                                }
                                drawDynamicSliderIcon(
                                    center = iconCenter,
                                    iconSize = iconSize,
                                    displayedFraction = displayedFraction,
                                    isBluetoothConnected = isBluetoothConnected,
                                    inCall = inCall,
                                    isMediaPlayingNow = isMediaPlayingNow,
                                    activeStreamType = activeStreamType,
                                    darkColor = speakerColorDark,
                                    whiteColor = speakerColorWhite,
                                    isWhiteSection = true,
                                    callPainter = callPainter,
                                    pathBuffer = housingPath,
                                    horizontalShiftUnits = animatedShiftUnits,
                                    wave1Alpha = wave1Alpha,
                                    wave1Scale = wave1Scale,
                                    wave2Alpha = wave2Alpha,
                                    wave2Scale = wave2Scale,
                                    wave3Alpha = wave3Alpha,
                                    wave3Scale = wave3Scale,
                                    customAppIcon = customAppIcon
                                )
                            }
                        }
                    }
                }

            } else {
                val isTop = screenEdge == ScreenEdge.TOP
                val bezelEdgeY = if (isTop) 0f else size.height
                val inwardDir = if (isTop) 1f else -1f

                val horizontalPad = (size.width - pillSpan) * 0.5f
                val pillCenterY = bezelEdgeY + inwardDir * (bezelPadPx + pillW * 0.5f)
                val pillTop = pillCenterY - pillW * 0.5f
                val pillBottom = pillCenterY + pillW * 0.5f
                val basePillLeft = horizontalPad
                val basePillRight = horizontalPad + pillSpan
                val maxStretchExtra = if (stretchScaleMax > 1.001f) pillSpan * (stretchScaleMax - 1f) else 0f
                val minStretchExtra = if (stretchScaleMin > 1.001f) pillSpan * (stretchScaleMin - 1f) else 0f
                val pillRight = basePillRight + maxStretchExtra
                val pillLeft = basePillLeft - minStretchExtra

                pillPath.reset()
                pillPath.addRoundRect(
                    RoundRect(
                        rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                        cornerRadius = CornerRadius(pillCornerRadius, pillCornerRadius)
                    )
                )

                val housingLeft = -minStretchExtra
                val housingWidth = size.width + maxStretchExtra + minStretchExtra

                if (notchAlpha > 0.01f) {
                    val notchHeight = cutoutThicknessPx * (1f - floatProgress * 0.72f)
                    val notchTop = if (isTop) 0f else bezelEdgeY - notchHeight
                    translate(left = housingLeft, top = notchTop) {
                        buildHudHousingPath(
                            path = housingPath,
                            width = housingWidth,
                            height = notchHeight,
                            screenEdge = screenEdge,
                            pillCornerRadiusPx = pillCornerRadius,
                            innerPadPx = innerPadPx,
                            filletTailPx = filletTailPx,
                            curvaturePercent = volumeSliderFluidityPercent
                        )
                        drawPath(
                            path = housingPath,
                            color = HudHousingBlack.copy(alpha = notchAlpha)
                        )
                    }
                    drawPath(
                        path = pillPath,
                        color = Color.Black,
                        blendMode = BlendMode.Clear
                    )
                }

                val transFraction = (pillTransparency / 100f).coerceIn(0f, 1f)
                val userUnfilledAlpha = (1f - transFraction).coerceIn(0f, 1f)
                val effectiveUnfilledAlpha = if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                    maxOf(userUnfilledAlpha, 0.72f)
                } else {
                    userUnfilledAlpha
                }
                val fillAlpha = 1f

                if (pillBlurDp > 0.5f) {
                    drawIntoCanvas { canvas ->
                        val blurPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(
                                (effectiveUnfilledAlpha * 180).roundToInt().coerceIn(0, 255),
                                50, 50, 55
                            )
                            maskFilter = BlurMaskFilter(
                                pillBlurDp.dp.toPx(),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        canvas.nativeCanvas.drawPath(pillPath.asAndroidPath(), blurPaint)
                    }
                }

                drawPath(path = pillPath, color = HudTrackCharcoal.copy(alpha = effectiveUnfilledAlpha))
                if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                    drawPath(
                        path = pillPath,
                        color = Color.White.copy(alpha = 0.14f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }

                val currentSpan = pillRight - pillLeft
                val fillRight = (pillLeft + currentSpan * displayedFraction).coerceIn(pillLeft, pillRight)

                clipPath(pillPath) {
                    if (displayedFraction > 0.002f) {
                        drawRect(
                            color = HudFillWhite.copy(alpha = fillAlpha),
                            topLeft = Offset(pillLeft, pillTop),
                            size = Size(fillRight - pillLeft, pillW)
                        )
                    }
                    if (iconAlpha > 0.01f) {
                        val iconSize = (pillW * 0.55f).coerceIn(16.dp.toPx(), 27.dp.toPx())
                        val iconCenter = Offset(
                            x = pillLeft + minOf(pillW * 0.50f, 24.dp.toPx()),
                            y = pillCenterY
                        )
                        val counterSteps = if (isCall) {
                            val minFine = 10
                            (displayedFraction * maxVolumeLimit).roundToInt().coerceIn(minFine, maxVolumeLimit)
                        } else {
                            (displayedFraction * maxVolumeLimit).roundToInt().coerceIn(0, maxVolumeLimit)
                        }
                        val counterText = "$counterSteps"
                        val counterTextSize = (pillW * 0.32f).coerceIn(10.dp.toPx(), 13.dp.toPx())
                        val counterCenterX = pillRight - minOf(pillW * 0.45f, 18.dp.toPx())
                        val speakerColorDark = (if (isBluetoothConnected) Color(0xFFFF453A) else HudIconOnDark).copy(alpha = iconAlpha)
                        val speakerColorWhite = (if (isBluetoothConnected) Color(0xFFFF453A) else HudIconOnWhite).copy(alpha = iconAlpha)
                        if (fillRight > pillLeft) {
                            clipRect(pillLeft, pillTop, fillRight, pillBottom) {
                                if (showVolumeCounter) {
                                    drawIntoCanvas { canvas ->
                                        val textPaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            textSize = counterTextSize
                                            color = HudIconOnWhite.copy(alpha = iconAlpha).toArgb()
                                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        }
                                        val baseline = pillCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                        canvas.nativeCanvas.drawText(counterText, counterCenterX, baseline, textPaint)
                                    }
                                }
                                drawDynamicSliderIcon(
                                    center = iconCenter,
                                    iconSize = iconSize,
                                    displayedFraction = displayedFraction,
                                    isBluetoothConnected = isBluetoothConnected,
                                    inCall = inCall,
                                    isMediaPlayingNow = isMediaPlayingNow,
                                    activeStreamType = activeStreamType,
                                    darkColor = speakerColorDark,
                                    whiteColor = speakerColorWhite,
                                    isWhiteSection = true,
                                    callPainter = callPainter,
                                    pathBuffer = housingPath,
                                    horizontalShiftUnits = animatedShiftUnits,
                                    wave1Alpha = wave1Alpha,
                                    wave1Scale = wave1Scale,
                                    wave2Alpha = wave2Alpha,
                                    wave2Scale = wave2Scale,
                                    wave3Alpha = wave3Alpha,
                                    wave3Scale = wave3Scale,
                                    customAppIcon = customAppIcon
                                )
                            }
                        }
                        if (fillRight < pillRight) {
                            clipRect(fillRight, pillTop, pillRight, pillBottom) {
                                if (showVolumeCounter) {
                                    drawIntoCanvas { canvas ->
                                        val textPaint = android.graphics.Paint().apply {
                                            isAntiAlias = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            textSize = counterTextSize
                                            color = HudIconOnDark.copy(alpha = iconAlpha).toArgb()
                                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        }
                                        val baseline = pillCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                        canvas.nativeCanvas.drawText(counterText, counterCenterX, baseline, textPaint)
                                    }
                                }
                                drawDynamicSliderIcon(
                                    center = iconCenter,
                                    iconSize = iconSize,
                                    displayedFraction = displayedFraction,
                                    isBluetoothConnected = isBluetoothConnected,
                                    inCall = inCall,
                                    isMediaPlayingNow = isMediaPlayingNow,
                                    activeStreamType = activeStreamType,
                                    darkColor = speakerColorDark,
                                    whiteColor = speakerColorWhite,
                                    isWhiteSection = false,
                                    callPainter = callPainter,
                                    pathBuffer = housingPath,
                                    horizontalShiftUnits = animatedShiftUnits,
                                    wave1Alpha = wave1Alpha,
                                    wave1Scale = wave1Scale,
                                    wave2Alpha = wave2Alpha,
                                    wave2Scale = wave2Scale,
                                    wave3Alpha = wave3Alpha,
                                    wave3Scale = wave3Scale,
                                    customAppIcon = customAppIcon
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}
