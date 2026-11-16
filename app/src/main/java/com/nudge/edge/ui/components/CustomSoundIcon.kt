package com.nudge.edge.ui.components

import android.graphics.CornerPathEffect
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun VolumeOrAppSoundIcon(
    fillFraction: Float,
    tintColor: Color,
    iconSize: Dp = 20.dp,
    customAppIcon: Drawable? = null,
    modifier: Modifier = Modifier
) {
    if (customAppIcon != null) {
        val imageBitmap = remember(customAppIcon) {
            customAppIcon.toBitmap(width = 96, height = 96).asImageBitmap()
        }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier
                .size(iconSize)
                .clip(CircleShape)
        )
        return
    }

    val clampedFraction = fillFraction.coerceIn(0f, 1f)
    val isMuted = clampedFraction <= 0.001f

    val wave1Alpha by animateFloatAsState(
        targetValue = if (!isMuted && clampedFraction > 0f) 1f else 0f,
        animationSpec = tween(150),
        label = "w1"
    )
    val wave2Alpha by animateFloatAsState(
        targetValue = if (clampedFraction >= 0.34f) 1f else 0f,
        animationSpec = tween(150),
        label = "w2"
    )
    val wave3Alpha by animateFloatAsState(
        targetValue = if (clampedFraction >= 0.67f) 1f else 0f,
        animationSpec = tween(150),
        label = "w3"
    )
    val muteAlpha by animateFloatAsState(
        targetValue = if (isMuted) 1f else 0f,
        animationSpec = tween(150),
        label = "mute"
    )

    Canvas(modifier = modifier.size(iconSize)) {
        val s = size.minDimension / 24f
        val xShift = -1.5f * s * muteAlpha

        translate(left = xShift, top = 0f) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = tintColor.toArgb()
                    pathEffect = CornerPathEffect(1.25f * s)
                }
                val speakerPath = android.graphics.Path().apply {
                    moveTo(3.2f * s, 9.3f * s)
                    lineTo(6.4f * s, 9.3f * s)
                    lineTo(11.0f * s, 5.4f * s)
                    lineTo(11.0f * s, 18.6f * s)
                    lineTo(6.4f * s, 14.7f * s)
                    lineTo(3.2f * s, 14.7f * s)
                    close()
                }
                drawPath(speakerPath, paint)
            }

            val strokeWidth = 1.65f * s
            val waveCenter = Offset(11.0f * s, 12.0f * s)

            if (wave1Alpha > 0.01f) {
                val r1 = 3.2f * s
                drawArc(
                    color = tintColor.copy(alpha = wave1Alpha),
                    startAngle = -36f,
                    sweepAngle = 72f,
                    useCenter = false,
                    topLeft = Offset(waveCenter.x - r1, waveCenter.y - r1),
                    size = Size(r1 * 2f, r1 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            if (wave2Alpha > 0.01f) {
                val r2 = 5.8f * s
                drawArc(
                    color = tintColor.copy(alpha = wave2Alpha),
                    startAngle = -42f,
                    sweepAngle = 84f,
                    useCenter = false,
                    topLeft = Offset(waveCenter.x - r2, waveCenter.y - r2),
                    size = Size(r2 * 2f, r2 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            if (wave3Alpha > 0.01f) {
                val r3 = 8.4f * s
                drawArc(
                    color = tintColor.copy(alpha = wave3Alpha),
                    startAngle = -48f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(waveCenter.x - r3, waveCenter.y - r3),
                    size = Size(r3 * 2f, r3 * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            if (muteAlpha > 0.01f) {
                val cx = 16.0f * s
                val cy = 12.0f * s
                val halfSpan = 2.1f * s
                drawLine(
                    color = tintColor.copy(alpha = muteAlpha),
                    start = Offset(cx - halfSpan, cy - halfSpan),
                    end = Offset(cx + halfSpan, cy + halfSpan),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tintColor.copy(alpha = muteAlpha),
                    start = Offset(cx - halfSpan, cy + halfSpan),
                    end = Offset(cx + halfSpan, cy - halfSpan),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
