package com.nudge.edge.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.nudge.edge.data.ActiveMediaApp
import com.nudge.edge.service.OverlayBlurController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.nudge.edge.model.NudgeIcons
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.nudge.edge.data.FineVolumeController
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.ScreenEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val VerticalPillTrack = Color(0xFF2C2C2E)
private val VerticalPillFill = Color(0xFFFFFFFF)
private val VerticalIconOnWhite = Color(0xFF6E6E73)
private val VerticalIconOnDark = Color(0xFFFFFFFF)
private val VerticalIconOffGray = Color(0xFF7D7D82)
private val BluetoothRed = Color(0xFFFF453A)

enum class MixerStreamIcon {
    CALL,
    BLUETOOTH,
    MEDIA,
    RING,
    NOTIFY,
    ALARM
}

private data class MixerChannelItem(
    val id: String,
    val label: String,
    val streamIcon: MixerStreamIcon,
    val fraction: Float,
    val isPrimary: Boolean,
    val maxVolumeLimit: Int = 100,
    val callMinStep: Int = 0,
    val customAppIcon: Drawable? = null,
    val streamType: Int? = null,
    val onIconTap: () -> Unit,
    val onValueChange: (Float) -> Unit
)

data class AudioMixerAutoFitValues(
    val popupWidthDp: Float,
    val popupHeightDp: Float,
    val panelWidthDp: Float,
    val panelHeightDp: Float,
    val panelSpacingDp: Float
)

fun computeAudioMixerAutoFit(
    sliderWidthDp: Float,
    sliderHeightDp: Float,
    numChannels: Int = 4,
    screenWidthDp: Float = 360f
): AudioMixerAutoFitValues {
    val baseChannelWidthDp = sliderWidthDp
    val baseChannelHeightDp = sliderHeightDp
    val baseSpacingDp = 10f
    val horizontalPaddingDp = 24f
    val verticalChromeHeightDp = 74f

    val idealPopupWidthDp = (numChannels * baseChannelWidthDp) + ((numChannels - 1) * baseSpacingDp) + horizontalPaddingDp
    val maxAvailableWidthDp = (screenWidthDp - 24f).coerceAtLeast(100f)

    val (effectivePillWidthDp, effectiveSpacingDp, effectivePopupWidthDp) = if (idealPopupWidthDp > maxAvailableWidthDp) {
        val scale = (maxAvailableWidthDp - horizontalPaddingDp) / ((numChannels * baseChannelWidthDp) + ((numChannels - 1) * baseSpacingDp)).coerceAtLeast(1f)
        Triple(
            (baseChannelWidthDp * scale).coerceAtLeast(18f),
            (baseSpacingDp * scale).coerceAtLeast(4f),
            maxAvailableWidthDp
        )
    } else {
        Triple(baseChannelWidthDp, baseSpacingDp, idealPopupWidthDp)
    }
    val effectivePillHeightDp = baseChannelHeightDp
    val effectivePopupHeightDp = baseChannelHeightDp + verticalChromeHeightDp

    return AudioMixerAutoFitValues(
        popupWidthDp = effectivePopupWidthDp,
        popupHeightDp = effectivePopupHeightDp,
        panelWidthDp = effectivePillWidthDp,
        panelHeightDp = effectivePillHeightDp,
        panelSpacingDp = effectiveSpacingDp
    )
}

fun buildAttachedMixerHousingPath(
    path: Path,
    width: Float,
    height: Float,
    screenEdge: ScreenEdge,
    cornerRadiusPx: Float,
    innerPadPx: Float = 8f,
    flareHeightPx: Float = 10f,
    flareWidthPx: Float = 18f
) {
    path.reset()
    if (width <= 0f || height <= 0f) return

    val flareH = flareHeightPx.coerceIn(4f, height * 0.25f)
    val flareW = flareWidthPx.coerceIn(8f, width * 0.45f)
    val topFlat = flareH
    val bottomFlat = height - flareH
    val flatSpan = (bottomFlat - topFlat).coerceAtLeast(10f)
    val r = cornerRadiusPx.coerceIn(4f, minOf(width - flareW, flatSpan) * 0.5f)

    when (screenEdge) {
        ScreenEdge.RIGHT -> {
            val bezelX = width
            val flareStartX = (width - flareW).coerceAtLeast(r)

            path.moveTo(bezelX, 0f)
            path.cubicTo(
                bezelX - flareW * 0.20f, topFlat * 0.70f,
                flareStartX + flareW * 0.35f, topFlat,
                flareStartX, topFlat
            )
            path.lineTo(r, topFlat)
            path.arcTo(
                rect = Rect(0f, topFlat, r * 2f, topFlat + r * 2f),
                startAngleDegrees = -90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            path.lineTo(0f, bottomFlat - r)
            path.arcTo(
                rect = Rect(0f, bottomFlat - r * 2f, r * 2f, bottomFlat),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            path.lineTo(flareStartX, bottomFlat)
            path.cubicTo(
                flareStartX + flareW * 0.35f, bottomFlat,
                bezelX - flareW * 0.20f, height - topFlat * 0.70f,
                bezelX, height
            )
            path.lineTo(bezelX, 0f)
            path.close()
        }
        ScreenEdge.LEFT -> {
            val bezelX = 0f
            val flareStartX = flareW.coerceAtMost(width - r)

            path.moveTo(bezelX, 0f)
            path.cubicTo(
                bezelX + flareW * 0.20f, topFlat * 0.70f,
                flareStartX - flareW * 0.35f, topFlat,
                flareStartX, topFlat
            )
            path.lineTo(width - r, topFlat)
            path.arcTo(
                rect = Rect(width - r * 2f, topFlat, width, topFlat + r * 2f),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            path.lineTo(width, bottomFlat - r)
            path.arcTo(
                rect = Rect(width - r * 2f, bottomFlat - r * 2f, width, bottomFlat),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            path.lineTo(flareStartX, bottomFlat)
            path.cubicTo(
                flareStartX - flareW * 0.35f, bottomFlat,
                bezelX + flareW * 0.20f, height - topFlat * 0.70f,
                bezelX, height
            )
            path.lineTo(bezelX, 0f)
            path.close()
        }
        ScreenEdge.TOP -> {
            val bezelY = 0f
            val flareStartY = flareW.coerceAtMost(height - r)
            val leftFlat = flareH
            val rightFlat = width - flareH

            path.moveTo(0f, bezelY)
            path.cubicTo(
                leftFlat * 0.70f, bezelY + flareW * 0.20f,
                leftFlat, flareStartY - flareW * 0.35f,
                leftFlat, flareStartY
            )
            path.lineTo(leftFlat, height - r)
            path.arcTo(
                rect = Rect(leftFlat, height - r * 2f, leftFlat + r * 2f, height),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            path.lineTo(rightFlat - r, height)
            path.arcTo(
                rect = Rect(rightFlat - r * 2f, height - r * 2f, rightFlat, height),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            path.lineTo(rightFlat, flareStartY)
            path.cubicTo(
                rightFlat, flareStartY - flareW * 0.35f,
                width - leftFlat * 0.70f, bezelY + flareW * 0.20f,
                width, bezelY
            )
            path.lineTo(0f, bezelY)
            path.close()
        }
        ScreenEdge.BOTTOM -> {
            val bezelY = height
            val flareStartY = (height - flareW).coerceAtLeast(r)
            val leftFlat = flareH
            val rightFlat = width - flareH

            path.moveTo(0f, bezelY)
            path.cubicTo(
                leftFlat * 0.70f, bezelY - flareW * 0.20f,
                leftFlat, flareStartY + flareW * 0.35f,
                leftFlat, flareStartY
            )
            path.lineTo(leftFlat, r)
            path.arcTo(
                rect = Rect(leftFlat, 0f, leftFlat + r * 2f, r * 2f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            path.lineTo(rightFlat - r, 0f)
            path.arcTo(
                rect = Rect(rightFlat - r * 2f, 0f, rightFlat, r * 2f),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            path.lineTo(rightFlat, flareStartY)
            path.cubicTo(
                rightFlat, flareStartY + flareW * 0.35f,
                width - leftFlat * 0.70f, bezelY - flareW * 0.20f,
                width, bezelY
            )
            path.lineTo(0f, bezelY)
            path.close()
        }
    }
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
    isBluetoothConnected: Boolean = false,
    isCallActive: Boolean = false,
    isAlwaysShowCallSlider: Boolean = true,
    callFractionExternal: Float? = null,
    onCallFractionChanged: ((Float) -> Unit)? = null,
    isVisible: Boolean = true,
    autoHideMs: Long = 5000L,
    audioMixerScalePercent: Int = 100,
    isAudioMixerAttachedToSlider: Boolean = true,
    audioMixerXPercent: Float = 50f,
    audioMixerYPercent: Float = 50f,
    sliderEdge: ScreenEdge = ScreenEdge.RIGHT,
    sliderYFraction: Float = 0.5f,
    sliderXFraction: Float = 0.5f,
    sliderCenterDp: Float? = null,
    sliderEdgeInsetDp: Float = 0f,
    volumeSliderFluidityPercent: Int = 75,
    onMusicFractionChanged: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    audioMixerPopupWidthDp: Float = 240f,
    audioMixerPopupHeightDp: Float = 220f,
    audioMixerPillWidthDp: Float = 42f,
    audioMixerPillHeightDp: Float = 120f,
    audioMixerPillSpacingDp: Float = 10f,
    isAudioMixerManuallySized: Boolean = false,
    mediaMaxVolumeLimit: Int = 100,
    callMaxVolumeLimit: Int = mediaMaxVolumeLimit,
    ringMaxVolumeLimit: Int = mediaMaxVolumeLimit,
    notifyMaxVolumeLimit: Int = mediaMaxVolumeLimit,
    alarmMaxVolumeLimit: Int = mediaMaxVolumeLimit,
    activeMediaApps: List<ActiveMediaApp> = emptyList(),
    onAppVolumeChanged: ((Int, Float) -> Unit)? = null,
    fineVolumeController: FineVolumeController? = null,
    ringFractionExternal: Float? = null,
    notificationFractionExternal: Float? = null,
    alarmFractionExternal: Float? = null,
    onStreamFractionChanged: ((Int, Float) -> Unit)? = null,
    onStreamScrubStateChanged: ((Int?, Boolean) -> Unit)? = null,
    isHoldingVolUpAtMax: Boolean = false,
    isHoldingVolDownAtMin: Boolean = false,
    pillTransparency: Int = 0,
    volumeSliderBlur: Float = 0f,
    pillBlurDp: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    val expansionProgress = remember { Animatable(if (isVisible) 1f else 0f) }

    var localCallFraction by remember(callFractionExternal) {
        mutableFloatStateOf(
            callFractionExternal
                ?: fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_VOICE_CALL)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL)
        )
    }
    var localRingFraction by remember {
        mutableFloatStateOf(
            ringFractionExternal
                ?: fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_RING)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_RING)
        )
    }
    var localNotificationFraction by remember {
        mutableFloatStateOf(
            notificationFractionExternal
                ?: fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_NOTIFICATION)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
        )
    }
    var localAlarmFraction by remember {
        mutableFloatStateOf(
            alarmFractionExternal
                ?: fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_ALARM)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
        )
    }

    val callFraction = callFractionExternal ?: localCallFraction
    val ringFraction = ringFractionExternal ?: localRingFraction
    val notificationFraction = notificationFractionExternal ?: localNotificationFraction
    val alarmFraction = alarmFractionExternal ?: localAlarmFraction

    var lastCallOnFraction by remember {
        mutableFloatStateOf(if (callFraction > 0.01f) callFraction else 0.5f)
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

    val transFraction = (pillTransparency / 100f).coerceIn(0f, 1f)
    val containerColor = if (lightMode) Color.White else Color.Black
    val titleColor = if (lightMode) Color(0xFF1C1C1E) else Color.White
    val labelColor = if (lightMode) Color(0xFF1C1C1E).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.90f)
    val percentColor = if (lightMode) Color(0xFF636366) else Color.White.copy(alpha = 0.62f)
    val settingsBg = if (lightMode) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.14f)

    val cardCornerDp = sliderCornerRadiusDp
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    var interactionEpoch by remember { mutableIntStateOf(0) }
    var pointerDownActive by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun startCollapseAndDismiss() {
        if (isClosing) return
        isClosing = true
        currentOnDismiss()
        coroutineScope.launch {
            expansionProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 420f
                )
            )
            isClosing = false
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            isClosing = false
            localCallFraction = fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_VOICE_CALL)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL)
            localRingFraction = fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_RING)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_RING)
            localNotificationFraction = fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_NOTIFICATION)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
            localAlarmFraction = fineVolumeController?.readSystemNormalizedVolume(AudioManager.STREAM_ALARM)
                ?: volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
            pointerDownActive = false
            interactionEpoch++
            if (expansionProgress.value > 0.01f) {
                expansionProgress.snapTo(0f)
            }
            expansionProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 320f
                )
            )
        } else {
            isClosing = false
            pointerDownActive = false
            if (expansionProgress.value > 0.01f) {
                expansionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 360f
                    )
                )
            }
        }
    }

    LaunchedEffect(
        musicFractionExternal,
        callFractionExternal,
        ringFractionExternal,
        notificationFractionExternal,
        alarmFractionExternal
    ) {
        if (isVisible) {
            interactionEpoch++
        }
    }

    LaunchedEffect(isVisible, interactionEpoch, pointerDownActive, autoHideMs) {
        if (isVisible && !pointerDownActive && autoHideMs > 0L) {
            delay(autoHideMs)
            startCollapseAndDismiss()
        }
    }

    val animFraction = expansionProgress.value
    if (!isVisible && animFraction <= 0.005f) {
        return
    }

    val allChannels = remember(
        isCallActive,
        isAlwaysShowCallSlider,
        isBluetoothConnected,
        callFraction,
        musicFractionExternal,
        ringFraction,
        notificationFraction,
        alarmFraction,
        hapticsEnabled,
        lastCallOnFraction,
        lastMusicOnFraction,
        lastRingOnFraction,
        lastNotifyOnFraction,
        lastAlarmOnFraction,
        activeMediaApps,
        mediaMaxVolumeLimit,
        callMaxVolumeLimit,
        ringMaxVolumeLimit,
        notifyMaxVolumeLimit,
        alarmMaxVolumeLimit
    ) {
        val list = mutableListOf<MixerChannelItem>()
        if (isCallActive || isAlwaysShowCallSlider) {
            list.add(
                MixerChannelItem(
                    id = "call",
                    label = "Call",
                    streamIcon = MixerStreamIcon.CALL,
                    fraction = callFraction.coerceIn(0f, 1f),
                    isPrimary = isCallActive,
                    maxVolumeLimit = callMaxVolumeLimit,
                    callMinStep = fineVolumeController?.let { it.getEffectiveMinIndex(AudioManager.STREAM_VOICE_CALL) * FineVolumeController.FINE_MULTIPLIER } ?: 10,
                    streamType = AudioManager.STREAM_VOICE_CALL,
                    onIconTap = {
                        val minFrac = fineVolumeController?.getStreamMinNormalizedFraction(AudioManager.STREAM_VOICE_CALL) ?: 0.10f
                        val next = if (callFraction > minFrac + 0.01f) {
                            lastCallOnFraction = callFraction
                            minFrac
                        } else {
                            lastCallOnFraction.coerceAtLeast(0.35f)
                        }
                        localCallFraction = next
                        if (onCallFractionChanged != null) {
                            onCallFractionChanged.invoke(next)
                        } else if (onStreamFractionChanged != null) {
                            onStreamFractionChanged(AudioManager.STREAM_VOICE_CALL, next)
                        } else if (fineVolumeController != null) {
                            val applied = fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_VOICE_CALL, next)
                            localCallFraction = applied
                        } else {
                            volumeController.setStreamFraction(AudioManager.STREAM_VOICE_CALL, next)
                            localCallFraction = volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL)
                        }
                        if (hapticsEnabled) volumeController.triggerHaptic()
                    },
                    onValueChange = { value ->
                        val minFrac = fineVolumeController?.getStreamMinNormalizedFraction(AudioManager.STREAM_VOICE_CALL) ?: 0.10f
                        val effectiveValue = value.coerceIn(minFrac, 1f)
                        if (effectiveValue > 0.01f) lastCallOnFraction = effectiveValue
                        localCallFraction = effectiveValue
                        if (onCallFractionChanged != null) {
                            onCallFractionChanged.invoke(effectiveValue)
                        } else if (onStreamFractionChanged != null) {
                            onStreamFractionChanged(AudioManager.STREAM_VOICE_CALL, effectiveValue)
                        } else if (fineVolumeController != null) {
                            val applied = fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_VOICE_CALL, effectiveValue)
                            localCallFraction = applied
                        } else {
                            volumeController.setStreamFraction(AudioManager.STREAM_VOICE_CALL, effectiveValue)
                        }
                    }
                )
            )
        }

        list.add(
            MixerChannelItem(
                id = "media",
                label = if (isBluetoothConnected) "Bluetooth" else "Media",
                streamIcon = if (isBluetoothConnected) MixerStreamIcon.BLUETOOTH else MixerStreamIcon.MEDIA,
                fraction = musicFractionExternal.coerceIn(0f, 1f),
                isPrimary = true,
                maxVolumeLimit = mediaMaxVolumeLimit,
                streamType = AudioManager.STREAM_MUSIC,
                onIconTap = {
                    val next = if (musicFractionExternal > 0.005f) {
                        lastMusicOnFraction = musicFractionExternal
                        0f
                    } else {
                        lastMusicOnFraction.coerceAtLeast(0.25f)
                    }
                    onMusicFractionChanged(next)
                    if (hapticsEnabled) volumeController.triggerHaptic()
                },
                onValueChange = { value ->
                    if (value > 0.01f) lastMusicOnFraction = value
                    onMusicFractionChanged(value)
                }
            )
        )

        list.add(
            MixerChannelItem(
                id = "ring",
                label = "Ring",
                streamIcon = MixerStreamIcon.RING,
                fraction = ringFraction.coerceIn(0f, 1f),
                isPrimary = false,
                maxVolumeLimit = ringMaxVolumeLimit,
                streamType = AudioManager.STREAM_RING,
                onIconTap = {
                    val next = if (ringFraction > 0.005f) {
                        lastRingOnFraction = ringFraction
                        0f
                    } else {
                        lastRingOnFraction.coerceAtLeast(0.25f)
                    }
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_RING, next)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_RING, next)
                        localRingFraction = fineVolumeController.readSystemNormalizedVolume(AudioManager.STREAM_RING)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_RING, next)
                        localRingFraction = volumeController.getStreamFraction(AudioManager.STREAM_RING)
                    }
                    if (hapticsEnabled) volumeController.triggerHaptic()
                },
                onValueChange = { value ->
                    if (value > 0.01f) lastRingOnFraction = value
                    localRingFraction = value
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_RING, value)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_RING, value)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_RING, value)
                    }
                }
            )
        )

        list.add(
            MixerChannelItem(
                id = "notify",
                label = "Notify",
                streamIcon = MixerStreamIcon.NOTIFY,
                fraction = notificationFraction.coerceIn(0f, 1f),
                isPrimary = false,
                maxVolumeLimit = notifyMaxVolumeLimit,
                streamType = AudioManager.STREAM_NOTIFICATION,
                onIconTap = {
                    val next = if (notificationFraction > 0.005f) {
                        lastNotifyOnFraction = notificationFraction
                        0f
                    } else {
                        lastNotifyOnFraction.coerceAtLeast(0.25f)
                    }
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_NOTIFICATION, next)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_NOTIFICATION, next)
                        localNotificationFraction = fineVolumeController.readSystemNormalizedVolume(AudioManager.STREAM_NOTIFICATION)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_NOTIFICATION, next)
                        localNotificationFraction = volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
                    }
                    if (hapticsEnabled) volumeController.triggerHaptic()
                },
                onValueChange = { value ->
                    if (value > 0.01f) lastNotifyOnFraction = value
                    localNotificationFraction = value
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_NOTIFICATION, value)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_NOTIFICATION, value)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_NOTIFICATION, value)
                    }
                }
            )
        )

        list.add(
            MixerChannelItem(
                id = "alarm",
                label = "Alarm",
                streamIcon = MixerStreamIcon.ALARM,
                fraction = alarmFraction.coerceIn(0f, 1f),
                isPrimary = false,
                maxVolumeLimit = alarmMaxVolumeLimit,
                streamType = AudioManager.STREAM_ALARM,
                onIconTap = {
                    val next = if (alarmFraction > 0.005f) {
                        lastAlarmOnFraction = alarmFraction
                        0f
                    } else {
                        lastAlarmOnFraction.coerceAtLeast(0.25f)
                    }
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_ALARM, next)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_ALARM, next)
                        localAlarmFraction = fineVolumeController.readSystemNormalizedVolume(AudioManager.STREAM_ALARM)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_ALARM, next)
                        localAlarmFraction = volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
                    }
                    if (hapticsEnabled) volumeController.triggerHaptic()
                },
                onValueChange = { value ->
                    if (value > 0.01f) lastAlarmOnFraction = value
                    localAlarmFraction = value
                    if (onStreamFractionChanged != null) {
                        onStreamFractionChanged(AudioManager.STREAM_ALARM, value)
                    } else if (fineVolumeController != null) {
                        fineVolumeController.setStreamFromTouchDrag(AudioManager.STREAM_ALARM, value)
                    } else {
                        volumeController.setStreamFraction(AudioManager.STREAM_ALARM, value)
                    }
                }
            )
        )

        activeMediaApps.forEach { app ->
            list.add(
                MixerChannelItem(
                    id = "app_${app.uid}",
                    label = app.appLabel,
                    streamIcon = MixerStreamIcon.MEDIA,
                    fraction = app.volumeFraction.coerceIn(0f, 1f),
                    isPrimary = false,
                    maxVolumeLimit = app.maxVolumeLimit,
                    customAppIcon = app.appIcon,
                    streamType = null,
                    onIconTap = {
                        val next = if (app.volumeFraction > 0.005f) 0f else 1f
                        onAppVolumeChanged?.invoke(app.uid, next)
                        if (hapticsEnabled) volumeController.triggerHaptic()
                    },
                    onValueChange = { value ->
                        onAppVolumeChanged?.invoke(app.uid, value.coerceIn(0f, 1f))
                    }
                )
            )
        }

        list
    }

    val primaryFirstChannels = remember(allChannels, isCallActive) {
        if (allChannels.isEmpty()) {
            emptyList()
        } else if (isCallActive) {
            val call = allChannels.firstOrNull { it.id == "call" }
            val media = allChannels.firstOrNull { it.id == "media" }
            val rest = allChannels.filter { it.id != "call" && it.id != "media" }
            val ordered = mutableListOf<MixerChannelItem>()
            if (call != null) ordered.add(call)
            if (media != null) ordered.add(media)
            ordered.addAll(rest)
            ordered
        } else {
            val media = allChannels.firstOrNull { it.id == "media" }
                ?: allChannels.firstOrNull { it.isPrimary }
                ?: allChannels.firstOrNull()
            if (media == null) {
                emptyList()
            } else {
                val call = allChannels.firstOrNull { it.id == "call" }
                val rest = allChannels.filter { channel ->
                    channel.id != media.id && (call == null || channel.id != call.id)
                }
                val ordered = mutableListOf<MixerChannelItem>()
                ordered.add(media)
                if (call != null) ordered.add(call)
                ordered.addAll(rest)
                ordered
            }
        }
    }

    val orderedChannels = remember(primaryFirstChannels, sliderEdge, isCallActive) {
        if (sliderEdge != ScreenEdge.RIGHT || primaryFirstChannels.isEmpty()) {
            primaryFirstChannels
        } else {
            if (isCallActive) {
                val call = primaryFirstChannels.firstOrNull { it.id == "call" }
                val media = primaryFirstChannels.firstOrNull { it.id == "media" }
                val rest = primaryFirstChannels.filter { it.id != "call" && it.id != "media" }
                val primarySideLast = mutableListOf<MixerChannelItem>()
                primarySideLast.addAll(rest)
                if (media != null) primarySideLast.add(media)
                if (call != null) primarySideLast.add(call)
                primarySideLast
            } else {
                val media = primaryFirstChannels.first()
                val call = primaryFirstChannels.getOrNull(1)?.takeIf { it.id == "call" }
                val rest = primaryFirstChannels.drop(if (call == null) 1 else 2)
                val primarySideLast = mutableListOf<MixerChannelItem>()
                primarySideLast.addAll(rest)
                if (call != null) primarySideLast.add(call)
                primarySideLast.add(media)
                primarySideLast
            }
        }
    }

    val modalModifier = if (isVisible) {
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * animFraction.coerceIn(0f, 1f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                startCollapseAndDismiss()
            }
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
                            startCollapseAndDismiss()
                        }
                    }
                }
            }
    } else {
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * animFraction.coerceIn(0f, 1f)))
    }

    var isAnySliderStretching by remember { mutableStateOf(false) }
    var modalStretchOriginY by remember { mutableFloatStateOf(0.5f) }
    val isHoldingVolumeBoundary = isHoldingVolUpAtMax || isHoldingVolDownAtMin
    val effectiveIsStretching = isAnySliderStretching || isHoldingVolumeBoundary
    val effectiveStretchOriginY = when {
        isHoldingVolUpAtMax -> 1f
        isHoldingVolDownAtMin -> 0f
        else -> modalStretchOriginY
    }
    val animatedModalStretchOriginY by animateFloatAsState(
        targetValue = effectiveStretchOriginY,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "modalStretchOriginY"
    )
    val modalStretchScale by animateFloatAsState(
        targetValue = if (effectiveIsStretching) 1.08f else 1.0f,
        animationSpec = if (effectiveIsStretching) {
            spring(dampingRatio = 0.75f, stiffness = 280f)
        } else {
            spring(dampingRatio = 0.80f, stiffness = 340f)
        },
        label = "modalStretchScale"
    )

    BoxWithConstraints(
        modifier = modalModifier
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val numChannels = orderedChannels.size

        val insetDp = sliderEdgeInsetDp.coerceAtLeast(0f)
        val channelSpacingDp = 10f
        val innerHousingPadDp = HUD_CUTOUT_INNER_PAD_DP
        val attachedInnerPaddingDp = 8f
        val attachedEdgePaddingDp = insetDp
        val attachedHorizontalPaddingDp = attachedInnerPaddingDp + attachedEdgePaddingDp
        val attachedContentWidthDp = (numChannels * sliderWidthDp) + ((numChannels - 1) * channelSpacingDp) + attachedHorizontalPaddingDp
        val floatingCardPadDp = 8f
        val floatingHorizontalPaddingDp = floatingCardPadDp * 2f
        val floatingContentWidthDp = (numChannels * sliderWidthDp) + ((numChannels - 1) * channelSpacingDp) + floatingHorizontalPaddingDp

        val effectivePillHeightDp = computeHudDrawnPillSpanDp(sliderHeightDp)
        val attachedContentHeightDp = effectivePillHeightDp
        val contentHeightDp = effectivePillHeightDp

        val scale = (audioMixerScalePercent / 100f).coerceIn(0.5f, 1.5f)

        if (isAudioMixerAttachedToSlider) {
            val isVerticalEdge = sliderEdge == ScreenEdge.LEFT || sliderEdge == ScreenEdge.RIGHT
            val singleSliderProtrusionDp = computeHudCutoutThicknessDp(sliderWidthDp, insetDp).dp
            val singleSliderLengthDp = effectivePillHeightDp.dp

            val progress = animFraction.coerceIn(0f, 1f)
            val flareHeightDp = 10f
            val tailSpanDp = (attachedInnerPaddingDp + flareHeightDp).dp

            val maxAvailableWidthDp = (screenW - 24.dp).value
            val effectiveChannelSpacingDp = if (attachedContentWidthDp > maxAvailableWidthDp) 8f else 10f
            val availableForPills = maxAvailableWidthDp - attachedHorizontalPaddingDp - ((numChannels - 1) * effectiveChannelSpacingDp)
            val fitPillWidthDp = (availableForPills / numChannels.coerceAtLeast(1)).coerceIn(34f, sliderWidthDp)
            val effectiveContentWidthDp = (numChannels * fitPillWidthDp) + ((numChannels - 1) * effectiveChannelSpacingDp) + attachedHorizontalPaddingDp

            val currentHousingWidthDp: androidx.compose.ui.unit.Dp
            val currentHousingHeightDp: androidx.compose.ui.unit.Dp
            val housingOffset: Offset

            if (isVerticalEdge) {
                val targetWidthDp = minOf(effectiveContentWidthDp, maxAvailableWidthDp)
                val animatedProtrusion = lerp(singleSliderProtrusionDp.value, targetWidthDp, progress).dp
                val animatedBodyLength = lerp(singleSliderLengthDp.value, attachedContentHeightDp, progress).dp
                val totalSpan = animatedBodyLength + tailSpanDp * 2f

                currentHousingWidthDp = animatedProtrusion
                currentHousingHeightDp = totalSpan

                val center = sliderCenterDp ?: (screenH.value * sliderYFraction)
                val top = (center - totalSpan.value / 2f).dp
                val left = if (sliderEdge == ScreenEdge.RIGHT) screenW - animatedProtrusion else 0.dp
                housingOffset = Offset(left.value, top.value)
            } else {
                val targetWidthDp = minOf(effectiveContentWidthDp, maxAvailableWidthDp)
                val animatedProtrusion = lerp(singleSliderProtrusionDp.value, attachedContentHeightDp, progress).dp
                val animatedBodyLength = lerp(singleSliderLengthDp.value, targetWidthDp, progress).dp
                val totalSpan = animatedBodyLength + tailSpanDp * 2f

                currentHousingWidthDp = totalSpan
                currentHousingHeightDp = animatedProtrusion

                val center = sliderCenterDp ?: (screenW.value * sliderXFraction)
                val left = (center - totalSpan.value / 2f).dp
                val top = if (sliderEdge == ScreenEdge.BOTTOM) screenH - animatedProtrusion else 0.dp
                housingOffset = Offset(left.value, top.value)
            }

            val housingPath = remember { Path() }
            val bezelShape = remember(sliderEdge, sliderCornerRadiusDp, attachedInnerPaddingDp, flareHeightDp) {
                object : Shape {
                    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                        val path = Path()
                        with(density) {
                            buildAttachedMixerHousingPath(
                                path = path,
                                width = size.width,
                                height = size.height,
                                screenEdge = sliderEdge,
                                cornerRadiusPx = sliderCornerRadiusDp.dp.toPx(),
                                innerPadPx = attachedInnerPaddingDp.dp.toPx(),
                                flareHeightPx = flareHeightDp.dp.toPx(),
                                flareWidthPx = 18.dp.toPx()
                            )
                        }
                        return Outline.Generic(path)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = housingOffset.x.dp, y = housingOffset.y.dp)
                    .size(currentHousingWidthDp, currentHousingHeightDp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        transformOrigin = when (sliderEdge) {
                            ScreenEdge.LEFT -> TransformOrigin(0f, animatedModalStretchOriginY)
                            ScreenEdge.RIGHT -> TransformOrigin(1f, animatedModalStretchOriginY)
                            ScreenEdge.TOP -> TransformOrigin(0.5f, 0f)
                            ScreenEdge.BOTTOM -> TransformOrigin(0.5f, 1f)
                        }
                        val clampedProgress = animFraction.coerceIn(0f, 1f)
                        val stretchProgress = 0.88f + 0.12f * clampedProgress
                        scaleX = scale * (if (isVerticalEdge) stretchProgress else 1f)
                        scaleY = scale * modalStretchScale * (if (isVerticalEdge) 1f else stretchProgress)
                        alpha = clampedProgress
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
                                        it.changedToDownIgnoreConsumed() || (it.previousPressed && !it.pressed)
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
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    buildAttachedMixerHousingPath(
                        path = housingPath,
                        width = size.width,
                        height = size.height,
                        screenEdge = sliderEdge,
                        cornerRadiusPx = sliderCornerRadiusDp.dp.toPx(),
                        innerPadPx = attachedInnerPaddingDp.dp.toPx(),
                        flareHeightPx = flareHeightDp.dp.toPx(),
                        flareWidthPx = 18.dp.toPx()
                    )

                    drawIntoCanvas { canvas ->
                        val shadowPaint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb((0.45f * 255).toInt(), 0, 0, 0)
                            maskFilter = BlurMaskFilter(8.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawPath(housingPath.asAndroidPath(), shadowPaint)
                    }
                    drawPath(housingPath, if (lightMode) Color.White else Color.Black)

                    if (pillTransparency > 0 || volumeSliderBlur > 0f) {
                        val pillHPx = effectivePillHeightDp.dp.toPx()
                        val pillWPx = fitPillWidthDp.dp.toPx()
                        val spacingPx = effectiveChannelSpacingDp.dp.toPx()
                        val pillRPx = sliderCornerRadiusDp.dp.toPx().coerceIn(0f, minOf(pillWPx, pillHPx) * 0.5f)
                        val totalPillsW = orderedChannels.size * pillWPx + (orderedChannels.size - 1).coerceAtLeast(0) * spacingPx

                        val startX = when (sliderEdge) {
                            ScreenEdge.RIGHT -> size.width - attachedEdgePaddingDp.dp.toPx() - totalPillsW
                            ScreenEdge.LEFT -> attachedEdgePaddingDp.dp.toPx()
                            else -> (size.width - totalPillsW) / 2f
                        }
                        val pillTop = if (isVerticalEdge) {
                            (size.height - pillHPx) / 2f
                        } else {
                            if (sliderEdge == ScreenEdge.TOP) attachedEdgePaddingDp.dp.toPx() else (size.height - attachedEdgePaddingDp.dp.toPx() - pillHPx)
                        }

                        for (i in orderedChannels.indices) {
                            val pillLeft = startX + i * (pillWPx + spacingPx)
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset(pillLeft, pillTop),
                                size = Size(pillWPx, pillHPx),
                                cornerRadius = CornerRadius(pillRPx, pillRPx),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(bezelShape),
                    contentAlignment = Alignment.Center
                ) {
                    val attachedScrollState = rememberScrollState()
                    LaunchedEffect(orderedChannels.size, isVisible) {
                        if (isVisible && sliderEdge == ScreenEdge.RIGHT && effectiveContentWidthDp > maxAvailableWidthDp) {
                            attachedScrollState.scrollTo(attachedScrollState.maxValue)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (sliderEdge == ScreenEdge.LEFT) attachedEdgePaddingDp.dp else attachedInnerPaddingDp.dp,
                                end = if (sliderEdge == ScreenEdge.RIGHT) attachedEdgePaddingDp.dp else attachedInnerPaddingDp.dp,
                                top = if (sliderEdge == ScreenEdge.TOP) attachedEdgePaddingDp.dp else attachedInnerPaddingDp.dp,
                                bottom = if (sliderEdge == ScreenEdge.BOTTOM) attachedEdgePaddingDp.dp else attachedInnerPaddingDp.dp
                            )
                            .then(if (effectiveContentWidthDp > maxAvailableWidthDp) Modifier.horizontalScroll(attachedScrollState) else Modifier),
                        horizontalArrangement = when (sliderEdge) {
                            ScreenEdge.RIGHT -> Arrangement.End
                            ScreenEdge.LEFT -> Arrangement.Start
                            else -> Arrangement.Center
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        orderedChannels.forEachIndexed { index, channel ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.width(effectiveChannelSpacingDp.dp))
                            }
                            VerticalStreamPill(
                                label = channel.label,
                                streamIcon = channel.streamIcon,
                                fraction = channel.fraction,
                                pillWidthDp = fitPillWidthDp,
                                pillHeightDp = effectivePillHeightDp,
                                pillCornerRadiusDp = sliderCornerRadiusDp,
                                labelColor = labelColor,
                                percentColor = percentColor,
                                iconTintOverride = if (channel.streamIcon == MixerStreamIcon.BLUETOOTH) BluetoothRed else null,
                                maxVolumeLimit = channel.maxVolumeLimit,
                                callMinStep = channel.callMinStep,
                                customAppIcon = channel.customAppIcon,
                                streamType = channel.streamType,
                                pillTransparency = pillTransparency,
                                volumeSliderBlur = volumeSliderBlur,
                                pillBlurDp = pillBlurDp,
                                isModalVisible = isVisible,
                                lightMode = lightMode,
                                onIconTap = channel.onIconTap,
                                onValueChange = channel.onValueChange,
                                onScrubStateChanged = { scrubbing ->
                                    onStreamScrubStateChanged?.invoke(channel.streamType, scrubbing)
                                },
                                onStretchStateChanged = { stretching, originY ->
                                    isAnySliderStretching = stretching
                                    if (stretching) modalStretchOriginY = originY
                                }
                            )
                        }
                    }
                }
            }
        } else {
            val maxFloatingW = screenW - 16.dp
            val cardW = minOf(floatingContentWidthDp.dp, maxFloatingW)
            val cardH = (effectivePillHeightDp + floatingCardPadDp * 2f).dp
            val pad = 8.dp

            val availX = (screenW - cardW).coerceAtLeast(0.dp)
            val availY = (screenH - cardH).coerceAtLeast(0.dp)
            val freeX = (availX * (audioMixerXPercent.coerceIn(0f, 100f) / 100f)).coerceIn(pad, (screenW - cardW - pad).coerceAtLeast(pad))
            val freeY = (availY * (audioMixerYPercent.coerceIn(0f, 100f) / 100f)).coerceIn(pad, (screenH - cardH - pad).coerceAtLeast(pad))

            Surface(
                modifier = Modifier
                    .offset(x = freeX, y = freeY)
                    .size(cardW, cardH)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        transformOrigin = TransformOrigin(0.5f, animatedModalStretchOriginY)
                        val clamped = animFraction.coerceIn(0f, 1f)
                        val enterScale = (0.86f + 0.14f * clamped) * scale
                        scaleX = enterScale
                        scaleY = enterScale * modalStretchScale
                        alpha = clamped
                        translationY = 16.dp.toPx() * (1f - clamped)
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
                                        it.changedToDownIgnoreConsumed() || (it.previousPressed && !it.pressed)
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
                color = if (pillTransparency > 0 || volumeSliderBlur > 0f) Color.Transparent else containerColor,
                shadowElevation = 20.dp
            ) {
                if (pillTransparency > 0 || volumeSliderBlur > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cardRadiusPx = cardCornerDp.dp.toPx()
                        drawRoundRect(
                            color = containerColor,
                            cornerRadius = CornerRadius(cardRadiusPx, cardRadiusPx)
                        )
                        val pillWPx = sliderWidthDp.dp.toPx()
                        val pillHPx = effectivePillHeightDp.dp.toPx()
                        val spacingPx = 10.dp.toPx()
                        val pillRPx = sliderCornerRadiusDp.dp.toPx().coerceIn(0f, minOf(pillWPx, pillHPx) * 0.5f)
                        val totalPillsW = primaryFirstChannels.size * pillWPx + (primaryFirstChannels.size - 1).coerceAtLeast(0) * spacingPx
                        val startX = if (floatingContentWidthDp.dp <= cardW) {
                            (size.width - totalPillsW) / 2f
                        } else {
                            floatingCardPadDp.dp.toPx()
                        }
                        val pillTop = floatingCardPadDp.dp.toPx()
                        for (i in primaryFirstChannels.indices) {
                            val pillLeft = startX + i * (pillWPx + spacingPx)
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset(pillLeft, pillTop),
                                size = Size(pillWPx, pillHPx),
                                cornerRadius = CornerRadius(pillRPx, pillRPx),
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = floatingCardPadDp.dp, vertical = floatingCardPadDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val floatingScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(floatingScrollState),
                        horizontalArrangement = if (floatingContentWidthDp.dp <= cardW) Arrangement.Center else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        primaryFirstChannels.forEachIndexed { index, channel ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            VerticalStreamPill(
                                label = channel.label,
                                streamIcon = channel.streamIcon,
                                fraction = channel.fraction,
                                pillWidthDp = sliderWidthDp,
                                pillHeightDp = effectivePillHeightDp,
                                pillCornerRadiusDp = sliderCornerRadiusDp,
                                labelColor = labelColor,
                                percentColor = percentColor,
                                iconTintOverride = if (channel.streamIcon == MixerStreamIcon.BLUETOOTH) BluetoothRed else null,
                                maxVolumeLimit = channel.maxVolumeLimit,
                                callMinStep = channel.callMinStep,
                                customAppIcon = channel.customAppIcon,
                                streamType = channel.streamType,
                                pillTransparency = pillTransparency,
                                volumeSliderBlur = volumeSliderBlur,
                                pillBlurDp = pillBlurDp,
                                isModalVisible = isVisible,
                                lightMode = lightMode,
                                onIconTap = channel.onIconTap,
                                onValueChange = channel.onValueChange,
                                onScrubStateChanged = { scrubbing ->
                                    onStreamScrubStateChanged?.invoke(channel.streamType, scrubbing)
                                },
                                onStretchStateChanged = { stretching, originY ->
                                    isAnySliderStretching = stretching
                                    if (stretching) modalStretchOriginY = originY
                                }
                            )
                        }
                        if (floatingContentWidthDp.dp > cardW) {
                            Spacer(modifier = Modifier.width(floatingCardPadDp.dp))
                        }
                    }
                }
            }
        }
    }
}

fun DrawScope.drawCustomBeamedNoteIcon(
    center: Offset,
    size: Float,
    color: Color,
    pathBuffer: Path = Path()
) {
    val s = size
    val leftHeadCenter = Offset(center.x - s * 0.22f, center.y + s * 0.28f)
    val rightHeadCenter = Offset(center.x + s * 0.22f, center.y + s * 0.18f)
    val headRadiusX = s * 0.17f
    val headRadiusY = s * 0.12f

    withTransform({
        rotate(degrees = -18f, pivot = leftHeadCenter)
    }) {
        drawOval(
            color = color,
            topLeft = Offset(leftHeadCenter.x - headRadiusX, leftHeadCenter.y - headRadiusY),
            size = Size(headRadiusX * 2f, headRadiusY * 2f)
        )
    }

    withTransform({
        rotate(degrees = -18f, pivot = rightHeadCenter)
    }) {
        drawOval(
            color = color,
            topLeft = Offset(rightHeadCenter.x - headRadiusX, rightHeadCenter.y - headRadiusY),
            size = Size(headRadiusX * 2f, headRadiusY * 2f)
        )
    }

    val stemWidth = (s * 0.085f).coerceAtLeast(1.5f)
    val leftStemRightX = leftHeadCenter.x + headRadiusX * 0.88f
    val leftStemLeftX = leftStemRightX - stemWidth
    val rightStemRightX = rightHeadCenter.x + headRadiusX * 0.88f
    val rightStemLeftX = rightStemRightX - stemWidth

    val leftBeamTopY = center.y - s * 0.28f
    val rightBeamTopY = center.y - s * 0.38f
    val beamHeight = s * 0.15f

    pathBuffer.reset()
    pathBuffer.moveTo(leftStemLeftX, leftHeadCenter.y)
    pathBuffer.lineTo(leftStemLeftX, leftBeamTopY)
    pathBuffer.lineTo(rightStemRightX, rightBeamTopY)
    pathBuffer.lineTo(rightStemRightX, rightHeadCenter.y)
    pathBuffer.lineTo(rightStemLeftX, rightHeadCenter.y)
    pathBuffer.lineTo(rightStemLeftX, rightBeamTopY + beamHeight)
    pathBuffer.lineTo(leftStemRightX, leftBeamTopY + beamHeight)
    pathBuffer.lineTo(leftStemRightX, leftHeadCenter.y)
    pathBuffer.close()

    val cornerRadiusPx = 2.5.dp.toPx().coerceAtMost(s * 0.10f)
    drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply {
            this.color = color
            this.isAntiAlias = true
            this.pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(cornerRadiusPx)
        }
        canvas.drawPath(pathBuffer, paint)
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
    iconTintOverride: Color? = null,
    maxVolumeLimit: Int = 100,
    callMinStep: Int = 0,
    customAppIcon: Drawable? = null,
    streamType: Int? = null,
    pillTransparency: Int = 0,
    volumeSliderBlur: Float = 0f,
    pillBlurDp: Float = 0f,
    isModalVisible: Boolean = true,
    lightMode: Boolean = false,
    onIconTap: () -> Unit,
    onValueChange: (Float) -> Unit,
    onScrubStateChanged: ((Boolean) -> Unit)? = null,
    onStretchStateChanged: ((Boolean, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val blurRadiusPx = OverlayBlurController.computeBlurRadiusPx(volumeSliderBlur, density.density)
    val isRuntimeBlurActive = OverlayBlurController.isRuntimeBlurActive(context)
    val isSamsungActive = OverlayBlurController.isSamsungBackendActive()

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnIconTap by rememberUpdatedState(onIconTap)
    val currentOnScrubStateChanged by rememberUpdatedState(onScrubStateChanged)

    val isCall = streamType == AudioManager.STREAM_VOICE_CALL
    val effCallMinStep = if (isCall) callMinStep.coerceAtLeast(1) else 0
    val minFraction = if (isCall && maxVolumeLimit > 0) (effCallMinStep.toFloat() / maxVolumeLimit.toFloat()).coerceIn(0f, 1f) else 0f

    var isDragging by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(fraction.coerceIn(minFraction, 1f)) }

    LaunchedEffect(fraction, isDragging) {
        if (!isDragging) {
            localValue = fraction.coerceIn(minFraction, 1f)
        }
    }

    val isMuted = if (isCall) false else fraction <= 0.005f

    val mediaPainter = rememberVectorPainter(NudgeIcons.BeamedMusicNote)
    val callPainter = rememberVectorPainter(Icons.Rounded.Call)
    val btPainter = rememberVectorPainter(Icons.Rounded.Bluetooth)
    val ringPainter = rememberVectorPainter(if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive)
    val notifyPainter = rememberVectorPainter(if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications)
    val alarmPainter = rememberVectorPainter(Icons.Rounded.Alarm)

    val activePainter = when (streamIcon) {
        MixerStreamIcon.MEDIA -> mediaPainter
        MixerStreamIcon.CALL -> callPainter
        MixerStreamIcon.BLUETOOTH -> btPainter
        MixerStreamIcon.RING -> ringPainter
        MixerStreamIcon.NOTIFY -> notifyPainter
        MixerStreamIcon.ALARM -> alarmPainter
    }

    val targetNormalizedVolume = if (isDragging) localValue else fraction.coerceIn(minFraction, 1f)
    val animatedVolume by animateFloatAsState(
        targetValue = targetNormalizedVolume,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SmoothMixerVolumeScrub"
    )
    val clamped = animatedVolume.coerceIn(minFraction, 1f)

    val iconTapThresholdPx = with(density) { (pillWidthDp * 0.90f).dp.toPx() }

    var isOverMax by remember { mutableStateOf(false) }
    var isOverMin by remember { mutableStateOf(false) }
    var lastStretchOriginY by remember { mutableFloatStateOf(0.5f) }
    if (isOverMax) {
        lastStretchOriginY = 1f
    } else if (isOverMin) {
        lastStretchOriginY = 0f
    }

    val isStretched = (isOverMax || isOverMin) && isDragging
    val stretchOriginY = if (isOverMax) 1f else 0f
    val currentOnStretchStateChanged by rememberUpdatedState(onStretchStateChanged)
    LaunchedEffect(isStretched, stretchOriginY) {
        currentOnStretchStateChanged?.invoke(isStretched, stretchOriginY)
    }

    val pillShape = RoundedCornerShape(pillCornerRadiusDp.dp)

    Box(
        modifier = modifier
            .size(pillWidthDp.dp, pillHeightDp.dp)
            .clip(pillShape)
            .pointerInput(streamType) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    val totalH = size.height.toFloat()
                    val inIconZone = startPos.y >= (totalH - iconTapThresholdPx)
                    val touchSlop = viewConfiguration.touchSlop

                    fun calculateFraction(position: Offset): Float {
                        return (1f - (position.y / totalH.coerceAtLeast(1f))).coerceIn(minFraction, 1f)
                    }

                    var isVerticalDrag = false
                    var isHorizontalScroll = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!isHorizontalScroll) {
                                    if (!isVerticalDrag) {
                                        if (inIconZone) {
                                            change.consume()
                                            currentOnIconTap()
                                        } else {
                                            change.consume()
                                            val initialFraction = calculateFraction(startPos)
                                            localValue = initialFraction
                                            currentOnValueChange(initialFraction)
                                        }
                                    } else {
                                        change.consume()
                                    }
                                }
                                break
                            }

                            if (change.positionChanged()) {
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y

                                if (!isVerticalDrag && !isHorizontalScroll) {
                                    if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        isHorizontalScroll = true
                                        break
                                    } else if (kotlin.math.abs(dy) > touchSlop && kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
                                        isVerticalDrag = true
                                        isDragging = true
                                        currentOnScrubStateChanged?.invoke(true)
                                    }
                                }

                                if (isVerticalDrag) {
                                    change.consume()
                                    val newFraction = calculateFraction(change.position)
                                    localValue = newFraction
                                    currentOnValueChange(newFraction)
                                    isOverMax = change.position.y < 0f || newFraction >= 0.999f
                                    isOverMin = change.position.y > totalH || newFraction <= minFraction + 0.001f
                                }
                            }
                        }
                    } finally {
                        if (isDragging) {
                            isDragging = false
                            isOverMax = false
                            isOverMin = false
                            currentOnScrubStateChanged?.invoke(false)
                        }
                    }
                }
            }
    ) {
        val radiusPx = with(density) {
            pillCornerRadiusDp.dp.toPx().coerceIn(0f, minOf(pillWidthDp.dp.toPx(), pillHeightDp.dp.toPx()) * 0.5f)
        }
        val pillWPx = with(density) { pillWidthDp.dp.toPx() }
        val pillHPx = with(density) { pillHeightDp.dp.toPx() }
        if (isSamsungActive && blurRadiusPx > 0) {
            AndroidView(
                factory = { ctx ->
                    View(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        OverlayBlurController.applySamsungTrackBlur(
                            view = this,
                            blurRadiusPx = blurRadiusPx,
                            cornerRadiusPx = radiusPx,
                            isHudVisible = isModalVisible,
                            widthPx = pillWPx,
                            heightPx = pillHPx
                        )
                    }
                },
                update = { view ->
                    OverlayBlurController.applySamsungTrackBlur(
                        view = view,
                        blurRadiusPx = blurRadiusPx,
                        cornerRadiusPx = radiusPx,
                        isHudVisible = isModalVisible,
                        widthPx = pillWPx,
                        heightPx = pillHPx
                    )
                },
                modifier = Modifier
                    .size(pillWidthDp.dp, pillHeightDp.dp)
                    .clip(pillShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val pillW = size.width
            val pillH = size.height
            val radius = (pillCornerRadiusDp.dp.toPx()).coerceIn(0f, minOf(pillW, pillH) * 0.5f)

            val pillPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, pillW, pillH),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                )
            }

            val transFraction = (pillTransparency / 100f).coerceIn(0f, 1f)
            val userUnfilledAlpha = (1f - transFraction).coerceIn(0f, 1f)
            val effectiveUnfilledAlpha = if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                maxOf(userUnfilledAlpha, 0.72f)
            } else {
                userUnfilledAlpha
            }

            if (pillBlurDp > 0.5f) {
                drawIntoCanvas { canvas ->
                    val blurPaint = Paint().apply {
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

            drawPath(path = pillPath, color = VerticalPillTrack.copy(alpha = effectiveUnfilledAlpha))
            if (blurRadiusPx > 0 && !isRuntimeBlurActive) {
                drawPath(
                    path = pillPath,
                    color = Color.White.copy(alpha = 0.14f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }

            val fillTop = (pillH - pillH * clamped).coerceIn(0f, pillH)
            val currentSteps = if (isCall) {
                (clamped * maxVolumeLimit).roundToInt().coerceIn(effCallMinStep, maxVolumeLimit)
            } else {
                (clamped * maxVolumeLimit).roundToInt().coerceIn(0, maxVolumeLimit)
            }
            val counterText = "$currentSteps"
            val counterTextSize = (pillW * 0.32f).coerceIn(10.dp.toPx(), 13.dp.toPx())
            val counterCenterY = minOf(pillW * 0.45f, 18.dp.toPx())

            val iconSize = (pillW * 0.52f).coerceIn(16.dp.toPx(), 26.dp.toPx())
            val iconCenter = Offset(
                x = pillW * 0.5f,
                y = pillH - minOf(pillW * 0.50f, 22.dp.toPx())
            )

            val labelTextSize = (pillW * 0.22f).coerceIn(8.5.dp.toPx(), 10.5.dp.toPx())
            val maxLabelW = (pillW - 4.dp.toPx()).coerceAtLeast(1f)
            val displayLabel = if (label.equals("Notification", ignoreCase = true) || label.equals("Notifications", ignoreCase = true)) {
                "Notif"
            } else {
                label
            }
            val labelCenterY = iconCenter.y - (iconSize * 0.5f) - 7.dp.toPx()

            clipPath(pillPath) {
                if (clamped > 0.002f) {
                    drawRect(
                        color = if (lightMode) Color.Black else VerticalPillFill,
                        topLeft = Offset(0f, fillTop),
                        size = Size(pillW, pillH - fillTop)
                    )
                }

                if (fillTop > 0f) {
                    clipRect(0f, 0f, pillW, fillTop) {
                        val onUnfilledColor = if (lightMode) Color(0xFF1C1C1E) else VerticalIconOnDark
                        val tintColor = iconTintOverride ?: if (isMuted) VerticalIconOffGray else onUnfilledColor
                        drawIntoCanvas { canvas ->
                            val textPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = counterTextSize
                                color = onUnfilledColor.toArgb()
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                            val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                            canvas.nativeCanvas.drawText(counterText, pillW * 0.5f, baseline, textPaint)
                        }
                        drawIntoCanvas { canvas ->
                            val textPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = labelTextSize
                                color = onUnfilledColor.toArgb()
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                            val fittedLabel = android.text.TextUtils.ellipsize(
                                displayLabel,
                                android.text.TextPaint(textPaint),
                                maxLabelW,
                                android.text.TextUtils.TruncateAt.END
                            ).toString()
                            val baseline = labelCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                            canvas.nativeCanvas.drawText(fittedLabel, pillW * 0.5f, baseline, textPaint)
                        }
                        if (customAppIcon != null) {
                            val bitmap = customAppIcon.toBitmap(iconSize.toInt().coerceAtLeast(1), iconSize.toInt().coerceAtLeast(1)).asImageBitmap()
                            drawImage(
                                image = bitmap,
                                topLeft = Offset(iconCenter.x - iconSize * 0.5f, iconCenter.y - iconSize * 0.5f)
                            )
                        } else if (streamIcon == MixerStreamIcon.RING) {
                            drawCustomSpeakerIcon(
                                center = iconCenter,
                                size = iconSize,
                                color = tintColor,
                                volumeFraction = if (isMuted) 0f else 0.5f,
                                pathBuffer = Path(),
                                horizontalShiftUnits = if (isMuted) 0.8f else 2.0f,
                                wave1Alpha = if (isMuted) 0f else 1f,
                                wave1Scale = 1f,
                                wave2Alpha = if (isMuted) 0f else 1f,
                                wave2Scale = 1f,
                                wave3Alpha = 0f,
                                wave3Scale = 0.85f
                            )
                        } else if (streamIcon == MixerStreamIcon.MEDIA) {
                            drawCustomBeamedNoteIcon(center = iconCenter, size = iconSize, color = tintColor)
                        } else {
                            translate(left = iconCenter.x - iconSize * 0.5f, top = iconCenter.y - iconSize * 0.5f) {
                                with(activePainter) {
                                    draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(tintColor))
                                }
                            }
                        }
                    }
                }

                if (fillTop < pillH) {
                    clipRect(0f, fillTop, pillW, pillH) {
                        val onFillColor = if (lightMode) Color.White else VerticalIconOnWhite
                        val tintColor = if (isMuted) VerticalIconOffGray else onFillColor
                        drawIntoCanvas { canvas ->
                            val textPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = counterTextSize
                                color = onFillColor.toArgb()
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                            val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                            canvas.nativeCanvas.drawText(counterText, pillW * 0.5f, baseline, textPaint)
                        }
                        drawIntoCanvas { canvas ->
                            val textPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = labelTextSize
                                color = onFillColor.toArgb()
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                            val fittedLabel = android.text.TextUtils.ellipsize(
                                displayLabel,
                                android.text.TextPaint(textPaint),
                                maxLabelW,
                                android.text.TextUtils.TruncateAt.END
                            ).toString()
                            val baseline = labelCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                            canvas.nativeCanvas.drawText(fittedLabel, pillW * 0.5f, baseline, textPaint)
                        }
                        if (customAppIcon != null) {
                            val bitmap = customAppIcon.toBitmap(iconSize.toInt().coerceAtLeast(1), iconSize.toInt().coerceAtLeast(1)).asImageBitmap()
                            drawImage(
                                image = bitmap,
                                topLeft = Offset(iconCenter.x - iconSize * 0.5f, iconCenter.y - iconSize * 0.5f)
                            )
                        } else if (streamIcon == MixerStreamIcon.RING) {
                            drawCustomSpeakerIcon(
                                center = iconCenter,
                                size = iconSize,
                                color = tintColor,
                                volumeFraction = if (isMuted) 0f else 0.5f,
                                pathBuffer = Path(),
                                horizontalShiftUnits = if (isMuted) 0.8f else 2.0f,
                                wave1Alpha = if (isMuted) 0f else 1f,
                                wave1Scale = 1f,
                                wave2Alpha = if (isMuted) 0f else 1f,
                                wave2Scale = 1f,
                                wave3Alpha = 0f,
                                wave3Scale = 0.85f
                            )
                        } else if (streamIcon == MixerStreamIcon.MEDIA) {
                            drawCustomBeamedNoteIcon(center = iconCenter, size = iconSize, color = tintColor)
                        } else {
                            translate(left = iconCenter.x - iconSize * 0.5f, top = iconCenter.y - iconSize * 0.5f) {
                                with(activePainter) {
                                    draw(size = Size(iconSize, iconSize), colorFilter = ColorFilter.tint(tintColor))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
