package com.nudge.edge.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nudge.edge.data.ActiveMediaApp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
    val customAppIcon: Drawable? = null,
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
    flareRadiusPx: Float
) {
    path.reset()
    if (width <= 0f || height <= 0f) return
    val r = cornerRadiusPx.coerceIn(4f, minOf(width, height) * 0.4f)
    val flare = flareRadiusPx.coerceIn(8f, height * 0.35f)

    when (screenEdge) {
        ScreenEdge.RIGHT -> {
            path.moveTo(width, 0f)
            path.cubicTo(
                width, flare * 0.45f,
                width - flare * 0.45f, flare,
                width - flare, flare
            )
            path.lineTo(r, flare)
            path.cubicTo(0f, flare, 0f, flare + r, 0f, flare + r)
            path.lineTo(0f, height - flare - r)
            path.cubicTo(0f, height - flare, r, height - flare, r, height - flare)
            path.lineTo(width - flare, height - flare)
            path.cubicTo(
                width - flare * 0.45f, height - flare,
                width, height - flare * 0.45f,
                width, height
            )
            path.lineTo(width, 0f)
            path.close()
        }
        ScreenEdge.LEFT -> {
            path.moveTo(0f, 0f)
            path.cubicTo(
                0f, flare * 0.45f,
                flare * 0.45f, flare,
                flare, flare
            )
            path.lineTo(width - r, flare)
            path.cubicTo(width, flare, width, flare + r, width, flare + r)
            path.lineTo(width, height - flare - r)
            path.cubicTo(width, height - flare, width - r, height - flare, width - r, height - flare)
            path.lineTo(flare, height - flare)
            path.cubicTo(
                flare * 0.45f, height - flare,
                0f, height - flare * 0.45f,
                0f, height
            )
            path.lineTo(0f, 0f)
            path.close()
        }
        ScreenEdge.TOP -> {
            path.moveTo(0f, 0f)
            path.cubicTo(
                flare * 0.45f, 0f,
                flare, flare * 0.45f,
                flare, flare
            )
            path.lineTo(flare, height - r)
            path.cubicTo(flare, height, flare + r, height, flare + r, height)
            path.lineTo(width - flare - r, height)
            path.cubicTo(width - flare, height, width - flare, height - r, width - flare, height - r)
            path.lineTo(width - flare, flare)
            path.cubicTo(
                width - flare, flare * 0.45f,
                width - flare * 0.45f, 0f,
                width, 0f
            )
            path.lineTo(0f, 0f)
            path.close()
        }
        ScreenEdge.BOTTOM -> {
            path.moveTo(0f, height)
            path.cubicTo(
                flare * 0.45f, height,
                flare, height - flare * 0.45f,
                flare, height - flare
            )
            path.lineTo(flare, r)
            path.cubicTo(flare, 0f, flare + r, 0f, flare + r, 0f)
            path.lineTo(width - flare - r, 0f)
            path.cubicTo(width - flare, 0f, width - flare, r, width - flare, r)
            path.lineTo(width - flare, height - flare)
            path.cubicTo(
                width - flare, height - flare * 0.45f,
                width - flare * 0.45f, height,
                width, height
            )
            path.lineTo(0f, height)
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
    onAppVolumeChanged: ((Int, Int, Int) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val expansionProgress = remember { Animatable(if (isVisible) 1f else 0f) }

    var callFraction by remember {
        mutableFloatStateOf(callFractionExternal ?: volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL))
    }
    var ringFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_RING))
    }
    var notificationFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION))
    }
    var alarmFraction by remember {
        mutableFloatStateOf(volumeController.getStreamFraction(AudioManager.STREAM_ALARM))
    }

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

    val containerColor = if (lightMode) Color(0xFFEFEFF4) else Color.Black
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
        coroutineScope.launch {
            expansionProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            )
            isClosing = false
            currentOnDismiss()
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            isClosing = false
            callFraction = callFractionExternal ?: volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL)
            ringFraction = volumeController.getStreamFraction(AudioManager.STREAM_RING)
            notificationFraction = volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
            alarmFraction = volumeController.getStreamFraction(AudioManager.STREAM_ALARM)
            pointerDownActive = false
            interactionEpoch++
            expansionProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            isClosing = false
            pointerDownActive = false
            if (expansionProgress.value > 0.01f) {
                expansionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    LaunchedEffect(musicFractionExternal, callFractionExternal) {
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
                    onIconTap = {
                        val next = if (callFraction > 0.005f) {
                            lastCallOnFraction = callFraction
                            0f
                        } else {
                            lastCallOnFraction.coerceAtLeast(0.25f)
                        }
                        volumeController.setStreamFraction(AudioManager.STREAM_VOICE_CALL, next)
                        callFraction = volumeController.getStreamFraction(AudioManager.STREAM_VOICE_CALL)
                        onCallFractionChanged?.invoke(callFraction)
                        if (hapticsEnabled) volumeController.triggerHaptic()
                    },
                    onValueChange = { value ->
                        if (value > 0.01f) lastCallOnFraction = value
                        callFraction = value
                        volumeController.setStreamFraction(AudioManager.STREAM_VOICE_CALL, value)
                        onCallFractionChanged?.invoke(value)
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
                isPrimary = !isCallActive,
                maxVolumeLimit = mediaMaxVolumeLimit,
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
                    if (value > 0.01f) lastRingOnFraction = value
                    ringFraction = value
                    volumeController.setStreamFraction(AudioManager.STREAM_RING, value)
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
                onIconTap = {
                    val next = if (notificationFraction > 0.005f) {
                        lastNotifyOnFraction = notificationFraction
                        0f
                    } else {
                        lastNotifyOnFraction.coerceAtLeast(0.25f)
                    }
                    volumeController.setStreamFraction(AudioManager.STREAM_NOTIFICATION, next)
                    notificationFraction = volumeController.getStreamFraction(AudioManager.STREAM_NOTIFICATION)
                    if (hapticsEnabled) volumeController.triggerHaptic()
                },
                onValueChange = { value ->
                    if (value > 0.01f) lastNotifyOnFraction = value
                    notificationFraction = value
                    volumeController.setStreamFraction(AudioManager.STREAM_NOTIFICATION, value)
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
                    if (value > 0.01f) lastAlarmOnFraction = value
                    alarmFraction = value
                    volumeController.setStreamFraction(AudioManager.STREAM_ALARM, value)
                }
            )
        )

        activeMediaApps.forEach { app ->
            list.add(
                MixerChannelItem(
                    id = "app_${app.uid}",
                    label = app.appLabel,
                    streamIcon = MixerStreamIcon.MEDIA,
                    fraction = (app.currentSteps.toFloat() / app.maxVolumeLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                    isPrimary = false,
                    maxVolumeLimit = app.maxVolumeLimit,
                    customAppIcon = app.appIcon,
                    onIconTap = {
                        val next = if (app.currentSteps > 0) 0 else app.maxVolumeLimit
                        onAppVolumeChanged?.invoke(app.uid, next, app.maxVolumeLimit)
                        if (hapticsEnabled) volumeController.triggerHaptic()
                    },
                    onValueChange = { value ->
                        val targetSteps = (value * app.maxVolumeLimit).roundToInt().coerceIn(0, app.maxVolumeLimit)
                        onAppVolumeChanged?.invoke(app.uid, targetSteps, app.maxVolumeLimit)
                    }
                )
            )
        }

        list
    }

    val orderedChannels = remember(allChannels, sliderEdge) {
        val primary = allChannels.firstOrNull { it.isPrimary } ?: allChannels.first()
        val rest = allChannels.filter { it != primary }
        if (sliderEdge == ScreenEdge.RIGHT) {
            rest + primary
        } else {
            listOf(primary) + rest
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

    BoxWithConstraints(
        modifier = modalModifier
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val numChannels = orderedChannels.size

        val channelSpacingDp = 10f
        val horizontalPaddingDp = 24f
        val contentWidthDp = (numChannels * sliderWidthDp) + ((numChannels - 1) * channelSpacingDp) + horizontalPaddingDp
        val effectivePillHeightDp = computeHudDrawnPillSpanDp(sliderHeightDp)
        val attachedContentHeightDp = effectivePillHeightDp + 50f
        val contentHeightDp = effectivePillHeightDp + 58f

        val scale = (audioMixerScalePercent / 100f).coerceIn(0.5f, 1.5f)

        if (isAudioMixerAttachedToSlider) {
            val isVerticalEdge = sliderEdge == ScreenEdge.LEFT || sliderEdge == ScreenEdge.RIGHT
            val singleSliderProtrusionDp = (sliderWidthDp + HUD_CUTOUT_INNER_PAD_DP).dp
            val singleSliderLengthDp = effectivePillHeightDp.dp

            val progress = animFraction.coerceIn(0f, 1f)
            val tailSpanDp = 34.dp

            val maxAvailableWidthDp = (screenW - 24.dp).value
            val effectiveChannelSpacingDp = if (contentWidthDp > maxAvailableWidthDp) 8f else 10f
            val availableForPills = maxAvailableWidthDp - horizontalPaddingDp - ((numChannels - 1) * effectiveChannelSpacingDp)
            val fitPillWidthDp = (availableForPills / numChannels.coerceAtLeast(1)).coerceIn(34f, sliderWidthDp)
            val effectiveContentWidthDp = (numChannels * fitPillWidthDp) + ((numChannels - 1) * effectiveChannelSpacingDp) + horizontalPaddingDp

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

                val top = (screenH * sliderYFraction) - (totalSpan / 2f)
                val left = if (sliderEdge == ScreenEdge.RIGHT) screenW - animatedProtrusion else 0.dp
                housingOffset = Offset(left.value, top.value)
            } else {
                val targetWidthDp = minOf(effectiveContentWidthDp, maxAvailableWidthDp)
                val animatedProtrusion = lerp(singleSliderProtrusionDp.value, attachedContentHeightDp, progress).dp
                val animatedBodyLength = lerp(singleSliderLengthDp.value, targetWidthDp, progress).dp
                val totalSpan = animatedBodyLength + tailSpanDp * 2f

                currentHousingWidthDp = totalSpan
                currentHousingHeightDp = animatedProtrusion

                val left = (screenW * sliderXFraction) - (totalSpan / 2f)
                val top = if (sliderEdge == ScreenEdge.BOTTOM) screenH - animatedProtrusion else 0.dp
                housingOffset = Offset(left.value, top.value)
            }

            val housingPath = remember { Path() }
            val bezelShape = remember(housingPath, currentHousingWidthDp, currentHousingHeightDp) {
                object : Shape {
                    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                        return Outline.Generic(housingPath)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = housingOffset.x.dp, y = housingOffset.y.dp)
                    .size(currentHousingWidthDp, currentHousingHeightDp)
                    .graphicsLayer {
                        transformOrigin = when (sliderEdge) {
                            ScreenEdge.LEFT -> TransformOrigin(0f, 0.5f)
                            ScreenEdge.RIGHT -> TransformOrigin(1f, 0.5f)
                            ScreenEdge.TOP -> TransformOrigin(0.5f, 0f)
                            ScreenEdge.BOTTOM -> TransformOrigin(0.5f, 1f)
                        }
                        scaleX = scale
                        scaleY = scale
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
                        flareRadiusPx = 22.dp.toPx()
                    )

                    drawIntoCanvas { canvas ->
                        val shadowPaint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb((0.45f * 255).toInt(), 0, 0, 0)
                            maskFilter = BlurMaskFilter(8.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.nativeCanvas.drawPath(housingPath.asAndroidPath(), shadowPaint)
                    }
                    drawPath(housingPath, Color.Black)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(bezelShape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = if (sliderEdge == ScreenEdge.LEFT) 14.dp else 12.dp,
                                end = if (sliderEdge == ScreenEdge.RIGHT) 14.dp else 12.dp,
                                top = 26.dp,
                                bottom = 20.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Mixer",
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
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

                        val attachedScrollState = rememberScrollState()
                        LaunchedEffect(orderedChannels.size, isVisible) {
                            if (isVisible && sliderEdge == ScreenEdge.RIGHT && effectiveContentWidthDp > maxAvailableWidthDp) {
                                attachedScrollState.scrollTo(attachedScrollState.maxValue)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (effectiveContentWidthDp > maxAvailableWidthDp) Modifier.horizontalScroll(attachedScrollState) else Modifier),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            orderedChannels.forEachIndexed { index, channel ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.width(effectiveChannelSpacingDp.dp))
                                }
                                val isPrimary = channel.isPrimary
                                val pillAlpha = if (isPrimary) 1f else ((progress - 0.20f) / 0.80f).coerceIn(0f, 1f)
                                val slideDir = if (sliderEdge == ScreenEdge.RIGHT) -1f else 1f
                                val pillTranslationX = if (isPrimary) 0f else (1f - progress) * slideDir * 32f

                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        alpha = pillAlpha
                                        translationX = pillTranslationX
                                    }
                                ) {
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
                                        customAppIcon = channel.customAppIcon,
                                        onIconTap = channel.onIconTap,
                                        onValueChange = channel.onValueChange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val maxFloatingW = screenW - 16.dp
            val cardW = minOf(contentWidthDp.dp, maxFloatingW)
            val cardH = (effectivePillHeightDp + 76f).dp
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
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        val clamped = animFraction.coerceIn(0f, 1.05f)
                        val enterScale = (0.90f + 0.10f * clamped) * scale
                        scaleX = enterScale
                        scaleY = enterScale
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
                color = containerColor,
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            fontWeight = FontWeight.Bold,
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

                    val floatingScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(floatingScrollState),
                        horizontalArrangement = if (contentWidthDp.dp <= cardW - 24.dp) Arrangement.Center else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        allChannels.forEachIndexed { index, channel ->
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
                                customAppIcon = channel.customAppIcon,
                                onIconTap = channel.onIconTap,
                                onValueChange = channel.onValueChange
                            )
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
    customAppIcon: Drawable? = null,
    onIconTap: () -> Unit,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnIconTap by rememberUpdatedState(onIconTap)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(fraction.coerceIn(0f, 1f)) }

    val isMuted = fraction <= 0.005f

    val mediaPainter = rememberVectorPainter(NudgeIcons.BeamedMusicNote)
    val callPainter = rememberVectorPainter(if (isMuted) Icons.Rounded.PhoneMissed else Icons.Rounded.Call)
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

    val targetNormalizedVolume = if (isScrubbing) scrubFraction else fraction.coerceIn(0f, 1f)
    val animatedVolume by animateFloatAsState(
        targetValue = targetNormalizedVolume,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SmoothMixerVolumeScrub"
    )
    val clamped = animatedVolume.coerceIn(0f, 1f)

    val iconTapThresholdPx = with(density) { (pillWidthDp * 0.90f).dp.toPx() }
    val tapSlopPx = with(density) { 5.dp.toPx() }

    Column(
        modifier = modifier.width(pillWidthDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(pillWidthDp.dp, pillHeightDp.dp)
                .pointerInput(pillHeightDp) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val totalH = size.height.toFloat()
                        val inIconZone = startPos.y >= (totalH - iconTapThresholdPx)
                        val touchSlop = viewConfiguration.touchSlop
                        var isScrubbingGesture = false
                        var isHorizontalDrag = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = change.position.x - startPos.x
                                val dy = change.position.y - startPos.y

                                if (!change.pressed) {
                                    if (!isHorizontalDrag && !isScrubbingGesture) {
                                        if (inIconZone) {
                                            currentOnIconTap()
                                        } else {
                                            val target = ((totalH - startPos.y) / totalH).coerceIn(0f, 1f)
                                            scrubFraction = target
                                            currentOnValueChange(target)
                                        }
                                        change.consume()
                                    }
                                    break
                                }

                                if (!isScrubbingGesture && !isHorizontalDrag) {
                                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                                        isHorizontalDrag = true
                                    } else if (abs(dy) > touchSlop && abs(dy) >= abs(dx)) {
                                        isScrubbingGesture = true
                                        isScrubbing = true
                                    }
                                }

                                if (isScrubbingGesture) {
                                    val updated = ((totalH - change.position.y) / totalH).coerceIn(0f, 1f)
                                    scrubFraction = updated
                                    currentOnValueChange(updated)
                                    change.consume()
                                }
                            }
                        } finally {
                            if (isScrubbing) {
                                isScrubbing = false
                            }
                        }
                    }
                }
        ) {
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
                drawPath(path = pillPath, color = VerticalPillTrack)

                val fillTop = (pillH - pillH * clamped).coerceIn(0f, pillH)
                val currentSteps = (clamped * maxVolumeLimit).roundToInt().coerceIn(0, maxVolumeLimit)
                val counterText = "$currentSteps"
                val counterTextSize = (pillW * 0.32f).coerceIn(10.dp.toPx(), 13.dp.toPx())
                val counterCenterY = minOf(pillW * 0.45f, 18.dp.toPx())

                clipPath(pillPath) {
                    if (clamped > 0.002f) {
                        drawRect(
                            color = VerticalPillFill,
                            topLeft = Offset(0f, fillTop),
                            size = Size(pillW, pillH - fillTop)
                        )
                    }

                    val iconSize = (pillW * 0.52f).coerceIn(16.dp.toPx(), 26.dp.toPx())
                    val iconCenter = Offset(
                        x = pillW * 0.5f,
                        y = pillH - minOf(pillW * 0.50f, 22.dp.toPx())
                    )

                    if (fillTop > 0f) {
                        clipRect(0f, 0f, pillW, fillTop) {
                            val tintColor = iconTintOverride ?: if (isMuted) VerticalIconOffGray else VerticalIconOnDark
                            drawIntoCanvas { canvas ->
                                val textPaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = counterTextSize
                                    color = VerticalIconOnDark.toArgb()
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                }
                                val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                canvas.nativeCanvas.drawText(counterText, pillW * 0.5f, baseline, textPaint)
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
                            val tintColor = if (isMuted) VerticalIconOffGray else VerticalIconOnWhite
                            drawIntoCanvas { canvas ->
                                val textPaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = counterTextSize
                                    color = VerticalIconOnWhite.toArgb()
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                }
                                val baseline = counterCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
                                canvas.nativeCanvas.drawText(counterText, pillW * 0.5f, baseline, textPaint)
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

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
