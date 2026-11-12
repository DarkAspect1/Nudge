package com.nudge.edge.ui.settings

import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.ui.components.HUD_CUTOUT_BEZEL_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_FILLET_TAIL_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_INNER_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_PILL_START_OFFSET_DP
import com.nudge.edge.ui.components.buildEdgePopPath
import com.nudge.edge.ui.components.buildHudHousingPath
import com.nudge.edge.ui.components.buildMergedEdgePopPath
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudDrawnPillSpanDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import com.nudge.edge.ui.components.drawSpeakerGlyph
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun DevicePreviewHeader(
    serviceActive: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillContainerColor by animateColorAsState(
        targetValue = if (serviceActive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        label = "statusPillBg"
    )
    val pillContentColor by animateColorAsState(
        targetValue = if (serviceActive) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        label = "statusPillFg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Nudge",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            onClick = onStatusClick,
            shape = RoundedCornerShape(50),
            color = pillContainerColor,
            contentColor = pillContentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (serviceActive) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.AccessibilityNew
                    },
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (serviceActive) {
                        "Service Active"
                    } else {
                        "Enable Accessibility"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DevicePreviewCanvas(
    settings: NudgeSettings,
    previewTarget: PreviewTarget,
    musicVolumeFraction: Float,
    realScreenWidthDp: Float,
    realScreenHeightDp: Float,
    displayRotation: Int,
    onPreviewTap: (PreviewTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceAspect = (realScreenWidthDp / realScreenHeightDp).coerceIn(0.42f, 2.35f)
    val screenTopColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val screenBottomColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val frameStrokeColor = MaterialTheme.colorScheme.outline
    val activeAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.tertiary

    val isLandscape = EdgeOrientationMapper.isLandscapeRotation(displayRotation)
    val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)
    val popScreenEdge = EdgeOrientationMapper.resolvePhysicalEdge(
        settings.popEdgeSide,
        displayRotation
    )
    val hudScreenEdge = EdgeOrientationMapper.resolveHudScreenEdge(
        settings = settings,
        rotation = displayRotation
    )

    val previewFloatProgress by animateFloatAsState(
        targetValue = if (settings.floatingMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        label = "previewFloatProgress"
    )
    val previewInsetDp by animateFloatAsState(
        targetValue = settings.hudEdgeInsetDp.coerceAtLeast(0f),
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "previewInsetDp"
    )

    val popPath = remember { Path() }
    val hudPath = remember { Path() }
    val pillPath = remember { Path() }
    val speakerPath = remember { Path() }
    val screenClipPath = remember { Path() }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .aspectRatio(deviceAspect, matchHeightConstraintsFirst = !isLandscape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings, realScreenWidthDp, realScreenHeightDp, displayRotation) {
                        detectTapGestures { offset ->
                            val tapX = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val tapY = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val hudFraction = EdgeOrientationMapper.resolveHudAxisFraction(
                                settings = settings,
                                rotation = displayRotation
                            )
                            val (hx, hy) = previewEdgePoint(hudScreenEdge, hudFraction)
                            val upPos = EdgeOrientationMapper.resolveAxisFraction(
                                settings.volumeUpYFraction,
                                displayRotation
                            )
                            val downPos = EdgeOrientationMapper.resolveAxisFraction(
                                settings.volumeDownYFraction,
                                displayRotation
                            )
                            val powerPos = EdgeOrientationMapper.resolveAxisFraction(
                                settings.powerButtonYFraction,
                                displayRotation
                            )
                            val (ux, uy) = previewEdgePoint(popScreenEdge, upPos)
                            val (dx, dy) = previewEdgePoint(popScreenEdge, downPos)
                            val (px, py) = previewEdgePoint(popScreenEdge, powerPos)
                            val candidates = buildList {
                                if (hudAllowed) {
                                    add(PreviewTarget.HUD to hypot(tapX - hx, tapY - hy))
                                }
                                if (popAllowed) {
                                    add(PreviewTarget.POP_UP to hypot(tapX - ux, tapY - uy))
                                    add(PreviewTarget.POP_DOWN to hypot(tapX - dx, tapY - dy))
                                    if (settings.enablePowerButtonPop) {
                                        add(PreviewTarget.POP_POWER to hypot(tapX - px, tapY - py))
                                    }
                                }
                            }
                            val target = candidates.minByOrNull { it.second }?.first ?: PreviewTarget.NONE
                            if (target != PreviewTarget.NONE) {
                                onPreviewTap(target)
                            }
                        }
                    }
            ) {
                val scale = size.height / realScreenHeightDp.coerceAtLeast(1f)
                val cornerRadiusPx = 18.dp.toPx()
                val bezelStrokePx = 3.dp.toPx()

                screenClipPath.reset()
                screenClipPath.addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                )

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(screenTopColor, screenBottomColor)
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )

                val popIsVertical = popScreenEdge == ScreenEdge.LEFT ||
                    popScreenEdge == ScreenEdge.RIGHT
                val popAxisLen = if (popIsVertical) size.height else size.width
                val popSpreadPx = settings.popHeightDp * scale
                val popProtrusionPx = settings.popWidthDp * scale

                val upCenterPx = computePreviewCenter(
                    portraitFraction = settings.volumeUpYFraction,
                    rotation = displayRotation,
                    axisLengthPx = popAxisLen,
                    elementSpanPx = popSpreadPx
                )
                val downCenterPx = computePreviewCenter(
                    portraitFraction = settings.volumeDownYFraction,
                    rotation = displayRotation,
                    axisLengthPx = popAxisLen,
                    elementSpanPx = popSpreadPx
                )
                val powerSpreadPx = settings.powerPopHeightDp * scale
                val powerProtrusionPx = settings.powerPopWidthDp * scale
                val powerCenterPx = computePreviewCenter(
                    portraitFraction = settings.powerButtonYFraction,
                    rotation = displayRotation,
                    axisLengthPx = popAxisLen,
                    elementSpanPx = powerSpreadPx
                )

                clipPath(screenClipPath) {
                    if (popAllowed) {
                        val popDelta = abs(upCenterPx - downCenterPx)
                        val shouldShowMerged = previewTarget == PreviewTarget.POP_BOTH &&
                            popDelta < popSpreadPx * 2f

                        if (shouldShowMerged) {
                            buildMergedEdgePopPath(
                                path = popPath,
                                canvasWidth = size.width,
                                canvasHeight = size.height,
                                firstCenterPx = upCenterPx,
                                secondCenterPx = downCenterPx,
                                spreadPx = popSpreadPx,
                                firstProtrusionPx = popProtrusionPx,
                                secondProtrusionPx = popProtrusionPx,
                                screenEdge = popScreenEdge
                            )
                            drawPath(path = popPath, color = Color.Black)
                        } else {
                            drawPreviewPop(
                                popPath = popPath,
                                canvasWidth = size.width,
                                canvasHeight = size.height,
                                centerPx = upCenterPx,
                                spreadPx = popSpreadPx,
                                protrusionPx = popProtrusionPx,
                                screenEdge = popScreenEdge,
                                highlight = previewTarget == PreviewTarget.POP_UP ||
                                    previewTarget == PreviewTarget.POP_BOTH,
                                accent = activeAccent,
                                strokeWidth = 1.6.dp.toPx()
                            )
                            drawPreviewPop(
                                popPath = popPath,
                                canvasWidth = size.width,
                                canvasHeight = size.height,
                                centerPx = downCenterPx,
                                spreadPx = popSpreadPx,
                                protrusionPx = popProtrusionPx,
                                screenEdge = popScreenEdge,
                                highlight = previewTarget == PreviewTarget.POP_DOWN ||
                                    previewTarget == PreviewTarget.POP_BOTH,
                                accent = activeAccent,
                                strokeWidth = 1.6.dp.toPx()
                            )
                        }

                        if (settings.enablePowerButtonPop) {
                            drawPreviewPop(
                                popPath = popPath,
                                canvasWidth = size.width,
                                canvasHeight = size.height,
                                centerPx = powerCenterPx,
                                spreadPx = powerSpreadPx,
                                protrusionPx = powerProtrusionPx,
                                screenEdge = popScreenEdge,
                                highlight = previewTarget == PreviewTarget.POP_POWER,
                                accent = secondaryAccent,
                                strokeWidth = 1.6.dp.toPx()
                            )
                        }
                    }

                    if (hudAllowed) {
                        val hudIsVertical = hudScreenEdge == ScreenEdge.LEFT ||
                            hudScreenEdge == ScreenEdge.RIGHT
                        val pillW = settings.hudWidthDp * scale
                        val pillSpan = computeHudDrawnPillSpanDp(settings.hudHeightDp) * scale
                        val cutoutThickness = computeHudCutoutThicknessDp(settings.hudWidthDp, previewInsetDp) * scale
                        val outerSpan = computeHudOuterSpanDp(settings.hudHeightDp) * scale
                        val bezelPad = (HUD_CUTOUT_BEZEL_PAD_DP + previewInsetDp) * scale
                        val innerPad = HUD_CUTOUT_INNER_PAD_DP * scale
                        val filletTail = HUD_CUTOUT_FILLET_TAIL_DP * scale
                        val pillStartOffset = HUD_CUTOUT_PILL_START_OFFSET_DP * scale
                        val pillCornerR = (settings.hudCornerRadiusDp * scale).coerceIn(
                            0f,
                            minOf(pillW, pillSpan) * 0.5f
                        )
                        val hudAxisLen = if (hudIsVertical) size.height else size.width
                        val hudCenterFraction = EdgeOrientationMapper.resolveHudAxisFraction(
                            settings = settings,
                            rotation = displayRotation
                        )
                        val hudRawCenter = hudCenterFraction * hudAxisLen
                        val hudClampedCenter = if (hudAxisLen <= outerSpan) {
                            hudAxisLen * 0.5f
                        } else {
                            hudRawCenter.coerceIn(outerSpan * 0.5f, hudAxisLen - outerSpan * 0.5f)
                        }
                        val hudStartOffset = (hudClampedCenter - outerSpan * 0.5f)
                            .coerceIn(0f, (hudAxisLen - outerSpan).coerceAtLeast(0f))

                        if (hudIsVertical) {
                            val isHudRight = hudScreenEdge == ScreenEdge.RIGHT
                            val hudBezelX = if (isHudRight) size.width else 0f
                            val inwardDir = if (isHudRight) -1f else 1f
                            val hudTop = hudStartOffset
                            val notchAlpha = (1f - previewFloatProgress) *
                                if (previewTarget == PreviewTarget.HUD) 1f else 0.85f
                            if (notchAlpha > 0.02f) {
                                val notchW = cutoutThickness * (1f - previewFloatProgress * 0.72f)
                                val notchLeft = if (isHudRight) hudBezelX - notchW else 0f
                                translate(left = notchLeft, top = hudTop) {
                                    buildHudHousingPath(
                                        path = hudPath,
                                        width = notchW,
                                        height = outerSpan,
                                        screenEdge = hudScreenEdge,
                                        pillCornerRadiusPx = pillCornerR,
                                        innerPadPx = innerPad,
                                        filletTailPx = filletTail
                                    )
                                    drawPath(path = hudPath, color = Color.Black.copy(alpha = notchAlpha))
                                }
                            }

                            val pillCenterX = hudBezelX + inwardDir * (bezelPad + pillW * 0.5f)
                            val pillLeft = pillCenterX - pillW * 0.5f
                            val pillRight = pillCenterX + pillW * 0.5f
                            val pillTop = hudTop + pillStartOffset
                            val pillBottom = pillTop + pillSpan

                            pillPath.reset()
                            pillPath.addRoundRect(
                                RoundRect(
                                    rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                                    cornerRadius = CornerRadius(pillCornerR, pillCornerR)
                                )
                            )
                            drawPath(path = pillPath, color = Color(0xFF2C2C2E))

                            val fillTop = (pillBottom - pillSpan * musicVolumeFraction).coerceIn(pillTop, pillBottom)
                            clipPath(pillPath) {
                                if (musicVolumeFraction > 0.01f) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(pillLeft, fillTop),
                                        size = Size(pillW, pillBottom - fillTop)
                                    )
                                }
                                val glyphSize = (pillW * 0.45f).coerceAtLeast(3f)
                                val glyphCenter = Offset(pillCenterX, pillBottom - pillW * 0.44f)
                                if (fillTop > pillTop) {
                                    clipRect(pillLeft, pillTop, pillRight, fillTop) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color.White,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color(0xFF2C2C2E)
                                        )
                                    }
                                }
                                if (fillTop < pillBottom) {
                                    clipRect(pillLeft, fillTop, pillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color(0xFF6E6E73),
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            val isHudTop = hudScreenEdge == ScreenEdge.TOP
                            val hudBezelY = if (isHudTop) 0f else size.height
                            val inwardDir = if (isHudTop) 1f else -1f
                            val hudLeft = hudStartOffset
                            val notchAlpha = (1f - previewFloatProgress) *
                                if (previewTarget == PreviewTarget.HUD) 1f else 0.85f
                            if (notchAlpha > 0.02f) {
                                val notchH = cutoutThickness * (1f - previewFloatProgress * 0.72f)
                                val notchTop = if (isHudTop) 0f else hudBezelY - notchH
                                translate(left = hudLeft, top = notchTop) {
                                    buildHudHousingPath(
                                        path = hudPath,
                                        width = outerSpan,
                                        height = notchH,
                                        screenEdge = hudScreenEdge,
                                        pillCornerRadiusPx = pillCornerR,
                                        innerPadPx = innerPad,
                                        filletTailPx = filletTail
                                    )
                                    drawPath(path = hudPath, color = Color.Black.copy(alpha = notchAlpha))
                                }
                            }

                            val pillCenterY = hudBezelY + inwardDir * (bezelPad + pillW * 0.5f)
                            val pillTop = pillCenterY - pillW * 0.5f
                            val pillBottom = pillCenterY + pillW * 0.5f
                            val pillLeft = hudLeft + pillStartOffset
                            val pillRight = pillLeft + pillSpan

                            pillPath.reset()
                            pillPath.addRoundRect(
                                RoundRect(
                                    rect = Rect(pillLeft, pillTop, pillRight, pillBottom),
                                    cornerRadius = CornerRadius(pillCornerR, pillCornerR)
                                )
                            )
                            drawPath(path = pillPath, color = Color(0xFF2C2C2E))

                            val fillRight = (pillLeft + pillSpan * musicVolumeFraction).coerceIn(pillLeft, pillRight)
                            clipPath(pillPath) {
                                if (musicVolumeFraction > 0.01f) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(pillLeft, pillTop),
                                        size = Size(fillRight - pillLeft, pillW)
                                    )
                                }
                                val glyphSize = (pillW * 0.45f).coerceAtLeast(3f)
                                val glyphCenter = Offset(pillLeft + pillW * 0.44f, pillCenterY)
                                if (fillRight > pillLeft) {
                                    clipRect(pillLeft, pillTop, fillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color(0xFF6E6E73),
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color.White
                                        )
                                    }
                                }
                                if (fillRight < pillRight) {
                                    clipRect(fillRight, pillTop, pillRight, pillBottom) {
                                        drawSpeakerGlyph(
                                            center = glyphCenter,
                                            glyphSize = glyphSize,
                                            color = Color.White,
                                            volumeFraction = musicVolumeFraction,
                                            pathBuffer = speakerPath,
                                            cutoutColor = Color(0xFF2C2C2E)
                                        )
                                    }
                                }
                            }
                        }


                    }

                    val cameraOffset = when {
                        !isLandscape -> Offset(size.width * 0.5f, 9.dp.toPx())
                        displayRotation == Surface.ROTATION_90 -> Offset(9.dp.toPx(), size.height * 0.5f)
                        else -> Offset(size.width - 9.dp.toPx(), size.height * 0.5f)
                    }
                    drawCircle(
                        color = frameStrokeColor.copy(alpha = 0.55f),
                        radius = 3.dp.toPx(),
                        center = cameraOffset
                    )
                }

                drawRoundRect(
                    color = frameStrokeColor,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = bezelStrokePx)
                )

                val markerColor = activeAccent.copy(alpha = 0.75f)
                val powerMarkerColor = secondaryAccent.copy(alpha = 0.85f)
                val markerThick = 3.dp.toPx()
                if (popIsVertical) {
                    val markerX = if (popScreenEdge == ScreenEdge.RIGHT) {
                        size.width + 4.dp.toPx()
                    } else {
                        -7.dp.toPx()
                    }
                    val markerH = (popSpreadPx * 0.48f).coerceAtLeast(6.dp.toPx())
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(markerX, upCenterPx - markerH * 0.5f),
                        size = Size(markerThick, markerH),
                        cornerRadius = CornerRadius(markerThick, markerThick)
                    )
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(markerX, downCenterPx - markerH * 0.5f),
                        size = Size(markerThick, markerH),
                        cornerRadius = CornerRadius(markerThick, markerThick)
                    )
                    if (settings.enablePowerButtonPop) {
                        val pMarkerH = (powerSpreadPx * 0.48f).coerceAtLeast(6.dp.toPx())
                        drawRoundRect(
                            color = powerMarkerColor,
                            topLeft = Offset(markerX, powerCenterPx - pMarkerH * 0.5f),
                            size = Size(markerThick, pMarkerH),
                            cornerRadius = CornerRadius(markerThick, markerThick)
                        )
                    }
                } else {
                    val markerY = if (popScreenEdge == ScreenEdge.BOTTOM) {
                        size.height + 4.dp.toPx()
                    } else {
                        -7.dp.toPx()
                    }
                    val markerW = (popSpreadPx * 0.48f).coerceAtLeast(6.dp.toPx())
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(upCenterPx - markerW * 0.5f, markerY),
                        size = Size(markerW, markerThick),
                        cornerRadius = CornerRadius(markerThick, markerThick)
                    )
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(downCenterPx - markerW * 0.5f, markerY),
                        size = Size(markerW, markerThick),
                        cornerRadius = CornerRadius(markerThick, markerThick)
                    )
                    if (settings.enablePowerButtonPop) {
                        val pMarkerW = (powerSpreadPx * 0.48f).coerceAtLeast(6.dp.toPx())
                        drawRoundRect(
                            color = powerMarkerColor,
                            topLeft = Offset(powerCenterPx - pMarkerW * 0.5f, markerY),
                            size = Size(pMarkerW, markerThick),
                            cornerRadius = CornerRadius(markerThick, markerThick)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PreviewLegendDot(
                color = Color.Black,
                label = if (popAllowed) "Volume Pops" else "Pops Off",
                onClick = { if (popAllowed) onPreviewTap(PreviewTarget.POP_BOTH) }
            )
            if (settings.enablePowerButtonPop && popAllowed) {
                PreviewLegendDot(
                    color = secondaryAccent,
                    label = "Power Pop",
                    onClick = { onPreviewTap(PreviewTarget.POP_POWER) }
                )
            }
            PreviewLegendDot(
                color = activeAccent,
                label = when {
                    !settings.enableVolumeSliderHud -> "HUD Off"
                    !hudAllowed -> "System Panel"
                    else -> "Slider HUD"
                },
                onClick = { if (hudAllowed) onPreviewTap(PreviewTarget.HUD) }
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewPop(
    popPath: Path,
    canvasWidth: Float,
    canvasHeight: Float,
    centerPx: Float,
    spreadPx: Float,
    protrusionPx: Float,
    screenEdge: ScreenEdge,
    highlight: Boolean,
    accent: Color,
    strokeWidth: Float
) {
    val startPx = centerPx - spreadPx * 0.5f
    when (screenEdge) {
        ScreenEdge.RIGHT,
        ScreenEdge.LEFT -> {
            val left = if (screenEdge == ScreenEdge.RIGHT) canvasWidth - protrusionPx else 0f
            translate(left = left, top = startPx) {
                buildEdgePopPath(
                    path = popPath,
                    canvasWidth = protrusionPx,
                    canvasHeight = spreadPx,
                    protrusionPx = protrusionPx,
                    screenEdge = screenEdge
                )
                drawPath(path = popPath, color = Color.Black)
            }
        }

        ScreenEdge.TOP,
        ScreenEdge.BOTTOM -> {
            val top = if (screenEdge == ScreenEdge.BOTTOM) canvasHeight - protrusionPx else 0f
            translate(left = startPx, top = top) {
                buildEdgePopPath(
                    path = popPath,
                    canvasWidth = spreadPx,
                    canvasHeight = protrusionPx,
                    protrusionPx = protrusionPx,
                    screenEdge = screenEdge
                )
                drawPath(path = popPath, color = Color.Black)
            }
        }
    }
}

@Composable
private fun PreviewLegendDot(
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun computePreviewCenter(
    portraitFraction: Float,
    rotation: Int,
    axisLengthPx: Float,
    elementSpanPx: Float
): Float {
    val baseFraction = EdgeOrientationMapper.resolveAxisFraction(portraitFraction, rotation)
    val rawCenter = baseFraction * axisLengthPx
    val half = elementSpanPx * 0.5f
    if (axisLengthPx <= elementSpanPx) return axisLengthPx * 0.5f
    return rawCenter.coerceIn(half, axisLengthPx - half)
}

private fun previewEdgePoint(edge: ScreenEdge, fractionAlongEdge: Float): Pair<Float, Float> {
    val f = fractionAlongEdge.coerceIn(0f, 1f)
    return when (edge) {
        ScreenEdge.LEFT -> Pair(0.06f, f)
        ScreenEdge.RIGHT -> Pair(0.94f, f)
        ScreenEdge.TOP -> Pair(f, 0.06f)
        ScreenEdge.BOTTOM -> Pair(f, 0.94f)
    }
}
