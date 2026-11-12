package com.nudge.edge.ui.components

import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nudge.edge.data.VolumeController
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val VerticalPillTrack = Color(0xFF2C2C2E)
private val VerticalPillFill = Color(0xFFFFFFFF)
private val VerticalIconOnWhite = Color(0xFF6E6E73)
private val VerticalIconOnDark = Color(0xFFFFFFFF)
private val VerticalIconOffGray = Color(0xFF7D7D82)

private enum class MixerStreamIcon {
    MEDIA,
    RING,
    NOTIFY,
    ALARM
}

@Composable
fun QuickVolumeModal(
    volumeController: VolumeController,
    musicFractionExternal: Float,
    sliderWidthDp: Float,
    sliderHeightDp: Float,
    sliderCornerRadiusDp: Float,
    lightMode: Boolean,
    hapticsEnabled: Boolean,
    isVisible: Boolean = true,
    autoHideMs: Long = 5000L,
    onMusicFractionChanged: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val openProgress = remember { Animatable(if (isVisible) 1f else 0f) }

    var ringFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_RING))
    }
    var notificationFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION))
    }
    var alarmFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_ALARM))
    }

    var lastMusicOnFraction by remember {
        mutableFloatStateOf(if (musicFractionExternal > 0.01f) musicFractionExternal else 0.5f)
    }
    var lastRingOnFraction by remember {
        mutableFloatStateOf(if (ringFraction > 0.01f) ringFraction else 0.5f)
    }
    var lastNotifyOnFraction by remember {
        mutableFloatStateOf(if (notificationFraction > 0.01f) notificationFraction else 0.5f)
    }
    var lastAlarmOnFraction by remember {
        mutableFloatStateOf(if (alarmFraction > 0.01f) alarmFraction else 0.5f)
    }

    val containerColor = if (lightMode) Color(0xFFEFEFF4) else Color.Black
    val titleColor = if (lightMode) Color(0xFF1C1C1E) else Color.White
    val labelColor = if (lightMode) {
        Color(0xFF1C1C1E).copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.90f)
    }
    val percentColor = if (lightMode) {
        Color(0xFF636366)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val settingsBg = if (lightMode) {
        Color.Black.copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

    val compactPillHeightDp = (sliderHeightDp * 0.78f).coerceIn(84f, 130f)
    val cardCornerDp = (sliderCornerRadiusDp.coerceIn(0f, sliderWidthDp * 0.5f) + 8f)
        .coerceIn(14f, 24f)
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    var pointerDownActive by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            ringFraction = volumeController.getStreamFraction(AudioManager.STREAM_RING)
            notificationFraction = volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
            alarmFraction = volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
            pointerDownActive = false
            interactionEpoch++
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 680f)
            )
        } else {
            pointerDownActive = false
            openProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(isVisible, interactionEpoch, pointerDownActive, autoHideMs) {
        if (isVisible && !pointerDownActive && autoHideMs > 0L) {
            delay(autoHideMs)
            currentOnDismiss()
        }
    }

    val animFraction = openProgress.value
    if (!isVisible && animFraction <= 0.01f) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * animFraction.coerceIn(0f, 1f)))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val bounds = cardBounds
                        val downOutside = event.changes.any { change ->
                            bounds != null &&
                                !bounds.contains(change.position) &&
                                change.changedToDownIgnoreConsumed()
                        }
                        if (downOutside) {
                            event.changes.forEach { it.consume() }
                            currentOnDismiss()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val bounds = cardBounds
                        val moveOutside = event.changes.any { change ->
                            bounds != null &&
                                !bounds.contains(change.position) &&
                                !change.isConsumed &&
                                change.pressed &&
                                change.position != change.previousPosition
                        }
                        if (moveOutside) {
                            event.changes.forEach { it.consume() }
                            currentOnDismiss()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .wrapContentWidth()
                .graphicsLayer {
                    val clamped = animFraction.coerceIn(0f, 1.05f)
                    val scale = 0.90f + 0.10f * clamped
                    scaleX = scale
                    scaleY = scale
                    alpha = animFraction.coerceIn(0f, 1f)
                    translationY = 14.dp.toPx() * (1f - animFraction)
                }
                .onGloballyPositioned { coordinates ->
                    cardBounds = coordinates.boundsInRoot()
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val anyPressed = event.changes.any { it.pressed }
                            if (anyPressed != pointerDownActive) {
                                pointerDownActive = anyPressed
                            }
                            if (event.changes.any {
                                    it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()
                                }
                            ) {
                                interactionEpoch++
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(cardCornerDp.dp),
            color = containerColor,
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Mixer",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Surface(
                        onClick = onOpenSettings,
                        shape = CircleShape,
                        color = settingsBg,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Open Nudge Settings",
                                tint = titleColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    VerticalStreamPill(
                        label = "Media",
                        streamIcon = MixerStreamIcon.MEDIA,
                        fraction = musicFractionExternal.coerceIn(0f, 1f),
                        pillWidthDp = sliderWidthDp,
                        pillHeightDp = compactPillHeightDp,
                        pillCornerRadiusDp = sliderCornerRadiusDp,
                        labelColor = labelColor,
                        percentColor = percentColor,
                        onIconTap = {
                            val next = if (musicFractionExternal > 0.005f) {
                                lastMusicOnFraction = musicFractionExternal
                                0f
                            } else {
                                lastMusicOnFraction.coerceAtLeast(0.25f)
                            }
                            volumeController.setMusicVolumeFraction(next)
                            val applied = volumeController.getMusicVolumeFraction()
                            onMusicFractionChanged(applied)
                            if (hapticsEnabled) volumeController.triggerHaptic()
                        },
                        onValueChange = { value ->
                            val prevStep = volumeController.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (value > 0.01f) {
                                lastMusicOnFraction = value
                            }
                            val nextStep = volumeController.setMusicVolumeFraction(value)
                            onMusicFractionChanged(value)
                            if (hapticsEnabled && nextStep != prevStep) {
                                volumeController.triggerHaptic()
                            }
                        }
                    )

                    VerticalStreamPill(
                        label = "Ring",
                        streamIcon = MixerStreamIcon.RING,
                        fraction = ringFraction,
                        pillWidthDp = sliderWidthDp,
                        pillHeightDp = compactPillHeightDp,
                        pillCornerRadiusDp = sliderCornerRadiusDp,
                        labelColor = labelColor,
                        percentColor = percentColor,
                        onIconTap = {
                            val next = if (ringFraction > 0.005f) {
                                lastRingOnFraction = ringFraction
                                0f
                            } else {
                                lastRingOnFraction.coerceAtLeast(0.25f)
                            }
                            volumeController.setStreamFraction(AudioManager.STREAM_RING, next)
                            ringFraction = volumeController.getStreamFraction(AudioManager.STREAM_RING)
                            if (hapticsEnabled) volumeController.triggerHaptic()
                        },
                        onValueChange = { value ->
                            if (value > 0.01f) {
                                lastRingOnFraction = value
                            }
                            ringFraction = value
                            volumeController.setStreamFraction(AudioManager.STREAM_RING, value)
                        }
                    )

                    VerticalStreamPill(
                        label = "Notify",
                        streamIcon = MixerStreamIcon.NOTIFY,
                        fraction = notificationFraction,
                        pillWidthDp = sliderWidthDp,
                        pillHeightDp = compactPillHeightDp,
                        pillCornerRadiusDp = sliderCornerRadiusDp,
                        labelColor = labelColor,
                        percentColor = percentColor,
                        onIconTap = {
                            val next = if (notificationFraction > 0.005f) {
                                lastNotifyOnFraction = notificationFraction
                                0f
                            } else {
                                lastNotifyOnFraction.coerceAtLeast(0.25f)
                            }
                            volumeController.setStreamFraction(
                                AudioManager.STREAM_NOTIFICATION,
                                next
                            )
                            notificationFraction = volumeController.getStreamFraction(
                                AudioManager.STREAM_NOTIFICATION
                            )
                            if (hapticsEnabled) volumeController.triggerHaptic()
                        },
                        onValueChange = { value ->
                            if (value > 0.01f) {
                                lastNotifyOnFraction = value
                            }
                            notificationFraction = value
                            volumeController.setStreamFraction(
                                AudioManager.STREAM_NOTIFICATION,
                                value
                            )
                        }
                    )

                    VerticalStreamPill(
                        label = "Alarm",
                        streamIcon = MixerStreamIcon.ALARM,
                        fraction = alarmFraction,
                        pillWidthDp = sliderWidthDp,
                        pillHeightDp = compactPillHeightDp,
                        pillCornerRadiusDp = sliderCornerRadiusDp,
                        labelColor = labelColor,
                        percentColor = percentColor,
                        onIconTap = {
                            val next = if (alarmFraction > 0.005f) {
                                lastAlarmOnFraction = alarmFraction
                                0f
                            } else {
                                lastAlarmOnFraction.coerceAtLeast(0.25f)
                            }
                            volumeController.setStreamFraction(AudioManager.STREAM_ALARM, next)
                            alarmFraction = volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
                            if (hapticsEnabled) volumeController.triggerHaptic()
                        },
                        onValueChange = { value ->
                            if (value > 0.01f) {
                                lastAlarmOnFraction = value
                            }
                            alarmFraction = value
                            volumeController.setStreamFraction(AudioManager.STREAM_ALARM, value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalStreamPill(
    label: String,
    streamIcon: MixerStreamIcon,
    fraction: Float,
    pillWidthDp: Float,
    pillHeightDp: Float,
    pillCornerRadiusDp: Float,
    labelColor: Color,
    percentColor: Color,
    onIconTap: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val isMuted = clamped <= 0.005f
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnIconTap by rememberUpdatedState(onIconTap)
    val pillPath = remember { Path() }
    val iconPath = remember { Path() }

    val vectorPainter: VectorPainter? = when (streamIcon) {
        MixerStreamIcon.RING -> rememberVectorPainter(
            if (isMuted) Icons.Rounded.PhoneDisabled else Icons.Rounded.Call
        )
        MixerStreamIcon.NOTIFY -> rememberVectorPainter(
            if (isMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsActive
        )
        else -> null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(pillWidthDp.dp)
                .height(pillHeightDp.dp)
                .pointerInput(pillWidthDp, pillHeightDp) {
                    val tapSlop = 6.dp.toPx()
                    val iconZone = (pillWidthDp * 0.88f).coerceIn(24f, 34f).dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val tappedIcon = startY >= h - iconZone
                        var moved = false

                        if (!tappedIcon) {
                            currentOnValueChange(((h - startY) / h).coerceIn(0f, 1f))
                        }
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (tappedIcon && !moved) {
                                    currentOnIconTap()
                                }
                                change.consume()
                                break
                            }
                            if (abs(change.position.y - startY) > tapSlop) {
                                moved = true
                            }
                            if (moved || !tappedIcon) {
                                currentOnValueChange(((h - change.position.y) / h).coerceIn(0f, 1f))
                            }
                            change.consume()
                        }
                    }
                }
        ) {
            val pillW = size.width
            val pillH = size.height
            val cornerR = pillCornerRadiusDp.dp.toPx().coerceIn(0f, minOf(pillW, pillH) * 0.5f)
            pillPath.reset()
            pillPath.addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, pillW, pillH),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
            )
            drawPath(path = pillPath, color = VerticalPillTrack)

            val fillTop = (pillH * (1f - clamped)).coerceIn(0f, pillH)
            clipPath(pillPath) {
                if (clamped > 0.002f) {
                    drawRect(
                        color = VerticalPillFill,
                        topLeft = Offset(0f, fillTop),
                        size = Size(pillW, pillH - fillTop)
                    )
                }

                val iconSize = (pillW * 0.45f).coerceIn(12.dp.toPx(), 19.dp.toPx())
                val iconCenter = Offset(
                    x = pillW * 0.5f,
                    y = pillH - minOf(pillW * 0.44f, 17.dp.toPx())
                )

                val mirrorVector = streamIcon == MixerStreamIcon.RING && isMuted
                if (fillTop > 0f) {
                    clipRect(0f, 0f, pillW, fillTop) {
                        val tintColor = if (isMuted) VerticalIconOffGray else VerticalIconOnDark
                        if (vectorPainter != null) {
                            scale(
                                scaleX = if (mirrorVector) -1f else 1f,
                                scaleY = 1f,
                                pivot = iconCenter
                            ) {
                                translate(
                                    left = iconCenter.x - iconSize * 0.5f,
                                    top = iconCenter.y - iconSize * 0.5f
                                ) {
                                    with(vectorPainter) {
                                        draw(
                                            size = Size(iconSize, iconSize),
                                            colorFilter = ColorFilter.tint(tintColor)
                                        )
                                    }
                                }
                            }
                        } else {
                            drawMixerStreamIcon(
                                icon = streamIcon,
                                center = iconCenter,
                                iconSize = iconSize,
                                color = tintColor,
                                cutoutColor = VerticalPillTrack,
                                isOff = isMuted,
                                pathBuffer = iconPath
                            )
                        }
                    }
                }

                if (fillTop < pillH) {
                    clipRect(0f, fillTop, pillW, pillH) {
                        val tintColor = if (isMuted) VerticalIconOffGray else VerticalIconOnWhite
                        if (vectorPainter != null) {
                            scale(
                                scaleX = if (mirrorVector) -1f else 1f,
                                scaleY = 1f,
                                pivot = iconCenter
                            ) {
                                translate(
                                    left = iconCenter.x - iconSize * 0.5f,
                                    top = iconCenter.y - iconSize * 0.5f
                                ) {
                                    with(vectorPainter) {
                                        draw(
                                            size = Size(iconSize, iconSize),
                                            colorFilter = ColorFilter.tint(tintColor)
                                        )
                                    }
                                }
                            }
                        } else {
                            drawMixerStreamIcon(
                                icon = streamIcon,
                                center = iconCenter,
                                iconSize = iconSize,
                                color = tintColor,
                                cutoutColor = VerticalPillFill,
                                isOff = isMuted,
                                pathBuffer = iconPath
                            )
                        }
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "${(clamped * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = percentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun DrawScope.drawMixerStreamIcon(
    icon: MixerStreamIcon,
    center: Offset,
    iconSize: Float,
    color: Color,
    cutoutColor: Color,
    isOff: Boolean,
    pathBuffer: Path
) {
    val s = iconSize
    val cx = center.x
    val cy = center.y
    val stemW = (s * 0.115f).coerceAtLeast(1.3f)

    when (icon) {
        MixerStreamIcon.MEDIA -> {
            val leftCenter = Offset(cx - s * 0.21f, cy + s * 0.21f)
            val rightCenter = Offset(cx + s * 0.20f, cy + s * 0.13f)
            val headW = s * 0.28f
            val headH = s * 0.21f

            rotate(degrees = -22f, pivot = leftCenter) {
                drawOval(
                    color = color,
                    topLeft = Offset(leftCenter.x - headW * 0.5f, leftCenter.y - headH * 0.5f),
                    size = Size(headW, headH)
                )
            }
            rotate(degrees = -22f, pivot = rightCenter) {
                drawOval(
                    color = color,
                    topLeft = Offset(rightCenter.x - headW * 0.5f, rightCenter.y - headH * 0.5f),
                    size = Size(headW, headH)
                )
            }

            val leftStemX = cx - s * 0.11f
            val rightStemX = cx + s * 0.295f
            val leftTopY = cy - s * 0.24f
            val rightTopY = cy - s * 0.33f

            drawLine(
                color = color,
                start = Offset(leftStemX, cy + s * 0.19f),
                end = Offset(leftStemX, leftTopY),
                strokeWidth = stemW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(rightStemX, cy + s * 0.11f),
                end = Offset(rightStemX, rightTopY),
                strokeWidth = stemW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(leftStemX, leftTopY),
                end = Offset(rightStemX, rightTopY),
                strokeWidth = (s * 0.175f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )

            if (isOff) {
                val slashStart = Offset(cx - s * 0.37f, cy - s * 0.37f)
                val slashEnd = Offset(cx + s * 0.37f, cy + s * 0.37f)
                val slashW = (s * 0.10f).coerceAtLeast(1.3f)
                drawLine(
                    color = cutoutColor,
                    start = slashStart,
                    end = slashEnd,
                    strokeWidth = slashW * 2.15f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = slashStart,
                    end = slashEnd,
                    strokeWidth = slashW,
                    cap = StrokeCap.Round
                )
            }
        }

        MixerStreamIcon.ALARM -> {
            val dialCenter = Offset(cx, cy + s * 0.04f)
            val dialR = s * 0.32f
            val chimeR = s * 0.43f
            val chimeTopLeft = Offset(dialCenter.x - chimeR, dialCenter.y - chimeR)
            val chimeSize = Size(chimeR * 2f, chimeR * 2f)

            drawArc(
                color = color,
                startAngle = -146f,
                sweepAngle = 34f,
                useCenter = false,
                topLeft = chimeTopLeft,
                size = chimeSize,
                style = Stroke(width = stemW, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -68f,
                sweepAngle = 34f,
                useCenter = false,
                topLeft = chimeTopLeft,
                size = chimeSize,
                style = Stroke(width = stemW, cap = StrokeCap.Round)
            )

            drawCircle(
                color = color,
                radius = dialR,
                center = dialCenter
            )

            pathBuffer.reset()
            pathBuffer.moveTo(cx, cy - s * 0.12f)
            pathBuffer.lineTo(cx, dialCenter.y)
            pathBuffer.lineTo(cx + s * 0.12f, dialCenter.y)
            drawPath(
                path = pathBuffer,
                color = cutoutColor,
                style = Stroke(
                    width = (s * 0.095f).coerceAtLeast(1.2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        MixerStreamIcon.RING,
        MixerStreamIcon.NOTIFY -> Unit
    }
}