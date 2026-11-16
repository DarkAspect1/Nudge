package com.nudge.edge.model

import android.app.NotificationManager
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

enum class EdgeSide {
    LEFT,
    RIGHT
}

enum class ScreenEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}

enum class BezelEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}

fun BezelEdge.toScreenEdge(): ScreenEdge = when (this) {
    BezelEdge.LEFT -> ScreenEdge.LEFT
    BezelEdge.RIGHT -> ScreenEdge.RIGHT
    BezelEdge.TOP -> ScreenEdge.TOP
    BezelEdge.BOTTOM -> ScreenEdge.BOTTOM
}

fun ScreenEdge.toBezelEdge(): BezelEdge = when (this) {
    ScreenEdge.LEFT -> BezelEdge.LEFT
    ScreenEdge.RIGHT -> BezelEdge.RIGHT
    ScreenEdge.TOP -> BezelEdge.TOP
    ScreenEdge.BOTTOM -> BezelEdge.BOTTOM
}

enum class HudTriggerMode {
    SIMULTANEOUS,
    SECOND_PRESS,
    SWIPE_FROM_POP
}

enum class PreviewTarget {
    NONE,
    EDGE_POP_VOL_UP,
    EDGE_POP_VOL_DOWN,
    EDGE_POP_POWER,
    EDGE_POP_ALL,
    VOLUME_SLIDER,
    AUDIO_MIXER,
    DND_PANEL,
    ACTIONS_PANEL,
    POP_UP,
    POP_DOWN,
    POP_BOTH,
    POP_POWER,
    HUD,
    DND,
    MODES,
    MIXER
}

enum class EdgePopType {
    VOL_UP,
    VOL_DOWN,
    POWER
}

typealias ButtonType = EdgePopType

enum class HardwareTrigger(val displayName: String) {
    NONE("None"),
    BOTH_VOLUME_SHORT("Both Volume Buttons"),
    DOUBLE_TAP_VOL_UP("Double-Tap Volume Up"),
    DOUBLE_TAP_VOL_DOWN("Double-Tap Volume Down"),
    LONG_PRESS_POWER("Hold Power Button"),
    CUSTOM_BTN_SINGLE("Custom Button (Single Press)"),
    CUSTOM_BTN_DOUBLE("Custom Button (Double Press)"),
    CUSTOM_BTN_LONG("Custom Button (Long Press)")
}

enum class DndModeType(val displayName: String) {
    SILENT_RING("Silent / Ring"),
    DND_ON_OFF("DND On / Off")
}

data class CustomMode(
    val id: String,
    val label: String,
    val notificationKeyword: String,
    val iconKey: String,
    val colorHex: Long,
    val dndMode: String = "UNCHANGED",
    val ringerMode: String = "UNCHANGED"
) {
    val keyword: String get() = notificationKeyword
    val name: String get() = label
}

val DEFAULT_CUSTOM_MODES = listOf(
    CustomMode(
        id = "normal",
        label = "NORMAL",
        notificationKeyword = "NUDGE_MODE_NORMAL",
        iconKey = "Notifications",
        colorHex = 0xFFFFFFFFL,
        dndMode = "OFF",
        ringerMode = "NORMAL"
    ),
    CustomMode(
        id = "silent",
        label = "SILENT",
        notificationKeyword = "NUDGE_MODE_SILENT",
        iconKey = "NotificationsOff",
        colorHex = 0xFFFF453AL,
        dndMode = "OFF",
        ringerMode = "SILENT"
    ),
    CustomMode(
        id = "sleep",
        label = "SLEEP",
        notificationKeyword = "NUDGE_MODE_SLEEP",
        iconKey = "Bedtime",
        colorHex = 0xFF7D7AFFL,
        dndMode = "ALARMS",
        ringerMode = "SILENT"
    ),
    CustomMode(
        id = "work",
        label = "WORK",
        notificationKeyword = "NUDGE_MODE_WORK",
        iconKey = "Work",
        colorHex = 0xFFBF5AF2L,
        dndMode = "PRIORITY",
        ringerMode = "VIBRATE"
    ),
    CustomMode(
        id = "personal",
        label = "PERSONAL",
        notificationKeyword = "NUDGE_MODE_PERSONAL",
        iconKey = "Person",
        colorHex = 0xFF5AC8FAL,
        dndMode = "PRIORITY",
        ringerMode = "VIBRATE"
    )
)

fun serializeCustomModes(modes: List<CustomMode>): String {
    val array = JSONArray()
    for (m in modes) {
        val obj = JSONObject()
        obj.put("id", m.id)
        obj.put("label", m.label)
        obj.put("notificationKeyword", m.notificationKeyword)
        obj.put("iconKey", m.iconKey)
        obj.put("colorHex", m.colorHex)
        obj.put("dndMode", m.dndMode)
        obj.put("ringerMode", m.ringerMode)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeCustomModes(json: String?): List<CustomMode> {
    if (json.isNullOrBlank()) return DEFAULT_CUSTOM_MODES
    return runCatching {
        val array = JSONArray(json)
        val list = mutableListOf<CustomMode>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                CustomMode(
                    id = obj.optString("id", "mode_$i"),
                    label = obj.optString("label", "MODE"),
                    notificationKeyword = obj.optString("notificationKeyword", "NUDGE_MODE_${obj.optString("label", "MODE")}"),
                    iconKey = obj.optString("iconKey", "Notifications"),
                    colorHex = obj.optLong("colorHex", 0xFFFFFFFFL),
                    dndMode = obj.optString("dndMode", "UNCHANGED"),
                    ringerMode = obj.optString("ringerMode", "UNCHANGED")
                )
            )
        }
        if (list.isNotEmpty()) list else DEFAULT_CUSTOM_MODES
    }.getOrDefault(DEFAULT_CUSTOM_MODES)
}

enum class AppMode(
    val title: String,
    val keyword: String,
    val colorHex: Long,
    val dndFilter: Int,
    val ringerMode: Int
) {
    NORMAL(
        "NORMAL",
        "NUDGE_MODE_NORMAL",
        0xFFFFFFFFL,
        NotificationManager.INTERRUPTION_FILTER_ALL,
        AudioManager.RINGER_MODE_NORMAL
    ),
    SILENT(
        "SILENT",
        "NUDGE_MODE_SILENT",
        0xFFFF453AL,
        NotificationManager.INTERRUPTION_FILTER_ALL,
        AudioManager.RINGER_MODE_SILENT
    ),
    SLEEP(
        "SLEEP",
        "NUDGE_MODE_SLEEP",
        0xFF7D7AFFL,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        AudioManager.RINGER_MODE_SILENT
    ),
    WORK(
        "WORK",
        "NUDGE_MODE_WORK",
        0xFFBF5AF2L,
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        AudioManager.RINGER_MODE_VIBRATE
    ),
    PERSONAL(
        "PERSONAL",
        "NUDGE_MODE_PERSONAL",
        0xFF5AC8FAL,
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        AudioManager.RINGER_MODE_VIBRATE
    )
}

data class NudgeSettings(
    val isEdgePopMasterEnabled: Boolean = true,
    val popEdgeSide: EdgeSide = EdgeSide.RIGHT,
    val volumeUpYFraction: Float = 0.27f,
    val volumeDownYFraction: Float = 0.37f,
    val popWidthDp: Float = 14f,
    val popHeightDp: Float = 72f,
    val popAutoHideMs: Long = 200L,
    val edgePopFluidityPercent: Int = 75,
    val enableEdgePopInLandscape: Boolean = true,
    val enablePowerButtonPop: Boolean = true,
    val powerButtonYFraction: Float = 0.50f,
    val powerPopWidthDp: Float = 14f,
    val powerPopHeightDp: Float = 72f,
    val enableVolumeSliderHud: Boolean = true,
    val showSystemPanelWhenHudOff: Boolean = false,
    val volumeStep: Int = 7,
    val isPerAppVolumeEnabled: Boolean = false,
    val perAppVolumeDisplayMode: String = "ALONGSIDE_SLIDER",
    val updateCheckFrequency: String = "DAILY",
    val lastUpdateCheckTimestampMs: Long = 0L,
    val cachedLatestReleaseJson: String = "",
    val cachedReleaseVersionTag: String = "",
    val cachedReleaseChangelog: String = "",
    val cachedReleaseApkUrl: String = "",
    val cachedReleaseApkSize: Long = 0L,
    val volumeSliderFluidityPercent: Int = 75,
    val showVolumeCounter: Boolean = false,
    val hudSideSynced: Boolean = true,
    val hudEdgeSide: EdgeSide = EdgeSide.RIGHT,
    val hudYFraction: Float = 0.34f,
    val hudHeightDp: Float = 155f,
    val hudWidthDp: Float = 45f,
    val hudCornerRadiusDp: Float = 15f,
    val hudAutoHideMs: Long = 1800L,
    val hudTriggerMode: HudTriggerMode = HudTriggerMode.SIMULTANEOUS,
    val enableSliderHudInLandscape: Boolean = true,
    val landscapeHudEdgeSide: ScreenEdge = ScreenEdge.RIGHT,
    val landscapeHudYFraction: Float = 0.50f,
    val showSystemPanelInLandscape: Boolean = true,
    val floatingMode: Boolean = false,
    val hudEdgeInsetDp: Float = 0f,
    val hapticsEnabled: Boolean = true,
    val edgePopHapticsEnabled: Boolean = true,
    val volumeSliderHapticsEnabled: Boolean = true,
    val dndPanelHapticsEnabled: Boolean = true,
    val actionsPanelHapticsEnabled: Boolean = true,
    val lightMode: Boolean = false,

    val audioMixerPopupWidthDp: Float = 228f,
    val audioMixerPopupHeightDp: Float = 250f,
    val audioMixerPillWidthDp: Float = 38f,
    val audioMixerPillHeightDp: Float = 130f,
    val audioMixerPillSpacingDp: Float = 10f,
    val isAudioMixerAttachedToSlider: Boolean = false,
    val audioMixerXPercent: Float = 50f,
    val audioMixerYPercent: Float = 50f,
    val isAlwaysShowCallSliderInMixer: Boolean = true,
    val isAudioMixerManuallySized: Boolean = false,
    val audioMixerScalePercent: Int = 100,

    val enableDndToggle: Boolean = true,
    val dndModeType: DndModeType = DndModeType.SILENT_RING,
    val dndTrigger: HardwareTrigger = HardwareTrigger.NONE,
    val dndDoubleTapTimeoutMs: Long = 250L,
    val dndTimeoutMs: Long = 1500L,
    val dndPortraitEdge: BezelEdge = BezelEdge.RIGHT,
    val dndPortraitOffsetPercent: Float = 0.50f,
    val dndEnableLandscape: Boolean = true,
    val dndLandscapeEdge: BezelEdge = BezelEdge.TOP,
    val dndLandscapeOffsetPercent: Float = 0.50f,
    val dndThicknessDp: Float = 42f,
    val dndLengthDp: Float = 108f,
    val dndIconSizeDp: Float = 24f,
    val dndUnfocusedOffsetDp: Float = 38f,
    val dndPanelFluidityPercent: Int = 80,
    val showDndLabel: Boolean = true,

    val enableActionsPanel: Boolean = true,
    val modesTrigger: HardwareTrigger = HardwareTrigger.NONE,
    val modesDoubleTapTimeoutMs: Long = 250L,
    val simultaneousPressTimeoutMs: Long = 120L,
    val enableModesNotificationBridge: Boolean = true,
    val modesTimeoutMs: Long = 2000L,
    val modesPortraitEdge: BezelEdge = BezelEdge.RIGHT,
    val modesPortraitOffsetPercent: Float = 0.50f,
    val modesEnableLandscape: Boolean = true,
    val modesLandscapeEdge: BezelEdge = BezelEdge.TOP,
    val modesLandscapeOffsetPercent: Float = 0.50f,
    val modesThicknessDp: Float = 44f,
    val modesLengthDp: Float = 148f,
    val actionsIconSizeDp: Float = 24f,
    val actionsIconSpacingDp: Float = 32f,
    val actionsPanelFluidityPercent: Int = 80,
    val showActionsLabel: Boolean = true,
    val customModes: List<CustomMode> = DEFAULT_CUSTOM_MODES,
    val powerAssistantConfigured: Boolean = false,

    val customButtonKeyCode: Int = 0,
    val customButtonScanCode: Int = 0,
    val customButtonLabel: String = "",

    val isHideAccessibilityEnabled: Boolean = false,
    val cloakedPackages: Set<String> = emptySet(),
    val isAutoKeepAliveViaShizuku: Boolean = true,
    val isShizukuWatchdogEnabled: Boolean = false
) {
    val effectiveHudEdgeSide: EdgeSide
        get() = if (hudSideSynced) popEdgeSide else hudEdgeSide

    val isMasterHapticsEnabled: Boolean get() = hapticsEnabled
    val effectiveEdgePopHaptics: Boolean get() = hapticsEnabled && edgePopHapticsEnabled
    val effectiveVolumeSliderHaptics: Boolean get() = hapticsEnabled && volumeSliderHapticsEnabled
    val effectiveDndPanelHaptics: Boolean get() = hapticsEnabled && dndPanelHapticsEnabled
    val effectiveActionsPanelHaptics: Boolean get() = hapticsEnabled && actionsPanelHapticsEnabled
    val edgePopTimeoutMs: Long get() = popAutoHideMs

    val enableModesCarousel: Boolean get() = enableActionsPanel
    val enableModesBuiltInPresets: Boolean get() = false
    val dndWidthDp: Float get() = dndThicknessDp
    val dndHeightDp: Float get() = dndLengthDp
    val dndEdgeSide: EdgeSide get() = if (dndPortraitEdge == BezelEdge.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
    val dndYFraction: Float get() = dndPortraitOffsetPercent

    val modesWidthDp: Float get() = modesThicknessDp
    val modesHeightDp: Float get() = modesLengthDp
    val modesEdgeSide: EdgeSide get() = if (modesPortraitEdge == BezelEdge.LEFT) EdgeSide.LEFT else EdgeSide.RIGHT
    val modesYFraction: Float get() = modesPortraitOffsetPercent

    val mixerPopupWidthDp: Float get() = audioMixerPopupWidthDp
    val mixerPopupHeightDp: Float get() = audioMixerPopupHeightDp
    val mixerPillWidthDp: Float get() = audioMixerPillWidthDp
    val mixerPillHeightDp: Float get() = audioMixerPillHeightDp
    val mixerPillSpacingDp: Float get() = audioMixerPillSpacingDp
    val mixerAttachToSlider: Boolean get() = isAudioMixerAttachedToSlider
    val mixerPositionXPercent: Float get() = audioMixerXPercent
    val mixerPositionYPercent: Float get() = audioMixerYPercent

    companion object {
        const val MIN_POP_WIDTH_DP = 4f
        const val MAX_POP_WIDTH_DP = 40f
        const val MIN_POP_HEIGHT_DP = 24f
        const val MAX_POP_HEIGHT_DP = 200f
        const val MIN_POP_AUTO_HIDE_MS = 100L
        const val MAX_POP_AUTO_HIDE_MS = 1500L
        const val POP_AUTO_HIDE_STEP_MS = 50L
        const val MIN_VOLUME_STEP = 1
        const val MAX_VOLUME_STEP = 10
        const val MIN_HUD_HEIGHT_DP = 100f
        const val MAX_HUD_HEIGHT_DP = 260f
        const val MIN_HUD_WIDTH_DP = 28f
        const val MAX_HUD_WIDTH_DP = 64f
        const val MIN_HUD_CORNER_RADIUS_DP = 0f
        const val MAX_HUD_CORNER_RADIUS_DP = 32f
        const val MIN_HUD_EDGE_INSET_DP = 0f
        const val MAX_HUD_EDGE_INSET_DP = 48f
        const val MIN_AUTO_HIDE_MS = 250L
        const val MAX_AUTO_HIDE_MS = 5000L
        const val AUTO_HIDE_STEP_MS = 50L

        const val MIN_DOUBLE_TAP_TIMEOUT_MS = 1L
        const val MAX_DOUBLE_TAP_TIMEOUT_MS = 500L
        const val DOUBLE_TAP_TIMEOUT_STEP_MS = 1L

        const val MIN_SIMULTANEOUS_TIMEOUT_MS = 1L
        const val MAX_SIMULTANEOUS_TIMEOUT_MS = 500L
        const val SIMULTANEOUS_TIMEOUT_STEP_MS = 1L

        const val MIN_DND_TIMEOUT_MS = 500L
        const val MAX_DND_TIMEOUT_MS = 4000L
        const val DND_TIMEOUT_STEP_MS = 100L
        const val MIN_DND_WIDTH_DP = 10f
        const val MAX_DND_WIDTH_DP = 72f
        const val MIN_DND_HEIGHT_DP = 40f
        const val MAX_DND_HEIGHT_DP = 240f
        const val MIN_DND_UNFOCUSED_OFFSET_DP = 14f
        const val MAX_DND_UNFOCUSED_OFFSET_DP = 90f

        const val MIN_MODES_TIMEOUT_MS = 800L
        const val MAX_MODES_TIMEOUT_MS = 5000L
        const val MODES_TIMEOUT_STEP_MS = 100L
        const val MIN_MODES_WIDTH_DP = 10f
        const val MAX_MODES_WIDTH_DP = 72f
        const val MIN_MODES_HEIGHT_DP = 40f
        const val MAX_MODES_HEIGHT_DP = 240f

        const val MIN_ACTIONS_ICON_SPACING_DP = 16f
        const val MAX_ACTIONS_ICON_SPACING_DP = 64f

        const val MIN_ICON_SIZE_DP = 10f
        const val MAX_ICON_SIZE_DP = 48f

        const val MIN_BEZEL_Y_FRACTION = 0.10f
        const val MAX_BEZEL_Y_FRACTION = 0.90f

        const val MIN_BEZEL_OFFSET_PERCENT = 0.10f
        const val MAX_BEZEL_OFFSET_PERCENT = 0.90f

        const val MIN_BEZEL_THICKNESS_DP = 10f
        const val MAX_BEZEL_THICKNESS_DP = 72f

        const val MIN_BEZEL_LENGTH_DP = 40f
        const val MAX_BEZEL_LENGTH_DP = 240f

        const val MIN_AUDIO_MIXER_POPUP_WIDTH_DP = 160f
        const val MAX_AUDIO_MIXER_POPUP_WIDTH_DP = 380f
        const val MIN_AUDIO_MIXER_POPUP_HEIGHT_DP = 170f
        const val MAX_AUDIO_MIXER_POPUP_HEIGHT_DP = 420f
        const val MIN_AUDIO_MIXER_PILL_WIDTH_DP = 24f
        const val MAX_AUDIO_MIXER_PILL_WIDTH_DP = 72f
        const val MIN_AUDIO_MIXER_PILL_HEIGHT_DP = 70f
        const val MAX_AUDIO_MIXER_PILL_HEIGHT_DP = 240f
        const val MIN_AUDIO_MIXER_PILL_SPACING_DP = 2f
        const val MAX_AUDIO_MIXER_PILL_SPACING_DP = 36f
        const val MIN_AUDIO_MIXER_PERCENT = 0f
        const val MAX_AUDIO_MIXER_PERCENT = 100f
        const val MIN_AUDIO_MIXER_SCALE_PERCENT = 50
        const val MAX_AUDIO_MIXER_SCALE_PERCENT = 150

        fun clampAudioMixerScalePercent(value: Int): Int =
            value.coerceIn(MIN_AUDIO_MIXER_SCALE_PERCENT, MAX_AUDIO_MIXER_SCALE_PERCENT)

        const val MIN_MIXER_POPUP_WIDTH_DP = MIN_AUDIO_MIXER_POPUP_WIDTH_DP
        const val MAX_MIXER_POPUP_WIDTH_DP = MAX_AUDIO_MIXER_POPUP_WIDTH_DP
        const val MIN_MIXER_POPUP_HEIGHT_DP = MIN_AUDIO_MIXER_POPUP_HEIGHT_DP
        const val MAX_MIXER_POPUP_HEIGHT_DP = MAX_AUDIO_MIXER_POPUP_HEIGHT_DP
        const val MIN_MIXER_PILL_WIDTH_DP = MIN_AUDIO_MIXER_PILL_WIDTH_DP
        const val MAX_MIXER_PILL_WIDTH_DP = MAX_AUDIO_MIXER_PILL_WIDTH_DP
        const val MIN_MIXER_PILL_HEIGHT_DP = MIN_AUDIO_MIXER_PILL_HEIGHT_DP
        const val MAX_MIXER_PILL_HEIGHT_DP = MAX_AUDIO_MIXER_PILL_HEIGHT_DP
        const val MIN_MIXER_PILL_SPACING_DP = MIN_AUDIO_MIXER_PILL_SPACING_DP
        const val MAX_MIXER_PILL_SPACING_DP = MAX_AUDIO_MIXER_PILL_SPACING_DP

        fun clampAudioMixerPopupWidth(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_POPUP_WIDTH_DP, MAX_AUDIO_MIXER_POPUP_WIDTH_DP)

        fun clampAudioMixerPopupHeight(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_POPUP_HEIGHT_DP, MAX_AUDIO_MIXER_POPUP_HEIGHT_DP)

        fun clampAudioMixerPillWidth(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_PILL_WIDTH_DP, MAX_AUDIO_MIXER_PILL_WIDTH_DP)

        fun clampAudioMixerPillHeight(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_PILL_HEIGHT_DP, MAX_AUDIO_MIXER_PILL_HEIGHT_DP)

        fun clampAudioMixerPillSpacing(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_PILL_SPACING_DP, MAX_AUDIO_MIXER_PILL_SPACING_DP)

        fun clampAudioMixerPercent(value: Float): Float =
            value.coerceIn(MIN_AUDIO_MIXER_PERCENT, MAX_AUDIO_MIXER_PERCENT)

        fun clampMixerPopupWidth(value: Float): Float = clampAudioMixerPopupWidth(value)
        fun clampMixerPopupHeight(value: Float): Float = clampAudioMixerPopupHeight(value)
        fun clampMixerPillWidth(value: Float): Float = clampAudioMixerPillWidth(value)
        fun clampMixerPillHeight(value: Float): Float = clampAudioMixerPillHeight(value)
        fun clampMixerPillSpacing(value: Float): Float = clampAudioMixerPillSpacing(value)

        fun clampFraction(value: Float): Float = value.coerceIn(0f, 1f)

        fun clampPopWidth(value: Float): Float =
            value.coerceIn(MIN_POP_WIDTH_DP, MAX_POP_WIDTH_DP)

        fun clampPopHeight(value: Float): Float =
            value.coerceIn(MIN_POP_HEIGHT_DP, MAX_POP_HEIGHT_DP)

        fun clampPopAutoHideMs(value: Long): Long =
            value.coerceIn(MIN_POP_AUTO_HIDE_MS, MAX_POP_AUTO_HIDE_MS)

        fun clampVolumeStep(value: Int): Int =
            value.coerceIn(MIN_VOLUME_STEP, MAX_VOLUME_STEP)

        fun clampHudHeight(value: Float): Float =
            value.coerceIn(MIN_HUD_HEIGHT_DP, MAX_HUD_HEIGHT_DP)

        fun clampHudWidth(value: Float): Float =
            value.coerceIn(MIN_HUD_WIDTH_DP, MAX_HUD_WIDTH_DP)

        fun clampHudCornerRadius(value: Float): Float =
            value.coerceIn(MIN_HUD_CORNER_RADIUS_DP, MAX_HUD_CORNER_RADIUS_DP)

        fun clampHudEdgeInset(value: Float): Float =
            value.coerceIn(MIN_HUD_EDGE_INSET_DP, MAX_HUD_EDGE_INSET_DP)

        fun clampAutoHideMs(value: Long): Long =
            value.coerceIn(MIN_AUTO_HIDE_MS, MAX_AUTO_HIDE_MS)

        fun clampDoubleTapTimeoutMs(value: Long): Long =
            value.coerceIn(MIN_DOUBLE_TAP_TIMEOUT_MS, MAX_DOUBLE_TAP_TIMEOUT_MS)

        fun clampSimultaneousTimeoutMs(value: Long): Long =
            value.coerceIn(MIN_SIMULTANEOUS_TIMEOUT_MS, MAX_SIMULTANEOUS_TIMEOUT_MS)

        fun clampActionsIconSpacingDp(value: Float): Float =
            value.coerceIn(MIN_ACTIONS_ICON_SPACING_DP, MAX_ACTIONS_ICON_SPACING_DP)

        fun clampDndTimeoutMs(value: Long): Long =
            value.coerceIn(MIN_DND_TIMEOUT_MS, MAX_DND_TIMEOUT_MS)

        fun clampDndWidth(value: Float): Float =
            value.coerceIn(MIN_DND_WIDTH_DP, MAX_DND_WIDTH_DP)

        fun clampDndHeight(value: Float): Float =
            value.coerceIn(MIN_DND_HEIGHT_DP, MAX_DND_HEIGHT_DP)

        fun clampModesTimeoutMs(value: Long): Long =
            value.coerceIn(MIN_MODES_TIMEOUT_MS, MAX_MODES_TIMEOUT_MS)

        fun clampModesWidth(value: Float): Float =
            value.coerceIn(MIN_MODES_WIDTH_DP, MAX_MODES_WIDTH_DP)

        fun clampModesHeight(value: Float): Float =
            value.coerceIn(MIN_MODES_HEIGHT_DP, MAX_MODES_HEIGHT_DP)

        fun clampBezelYFraction(value: Float): Float =
            value.coerceIn(MIN_BEZEL_Y_FRACTION, MAX_BEZEL_Y_FRACTION)

        fun clampBezelOffsetPercent(value: Float): Float =
            value.coerceIn(MIN_BEZEL_OFFSET_PERCENT, MAX_BEZEL_OFFSET_PERCENT)

        fun clampBezelThicknessDp(value: Float): Float =
            value.coerceIn(MIN_BEZEL_THICKNESS_DP, MAX_BEZEL_THICKNESS_DP)

        fun clampBezelLengthDp(value: Float): Float =
            value.coerceIn(MIN_BEZEL_LENGTH_DP, MAX_BEZEL_LENGTH_DP)

        fun clampIconSizeDp(value: Float): Float =
            value.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)

        fun clampDndUnfocusedOffsetDp(value: Float): Float =
            value.coerceIn(MIN_DND_UNFOCUSED_OFFSET_DP, MAX_DND_UNFOCUSED_OFFSET_DP)

        fun clampFluidityPercent(value: Int): Int =
            value.coerceIn(0, 100)

        fun clampCurvaturePercent(value: Int): Int = clampFluidityPercent(value)

        fun clampDeviceMaxVolumeSteps(value: Int): Int =
            value.coerceIn(5, 300)

        fun stepFractionByDp(
            currentFraction: Float,
            deltaDp: Float,
            screenHeightDp: Float
        ): Float {
            if (screenHeightDp <= 0f) return clampFraction(currentFraction)
            val deltaFraction = deltaDp / screenHeightDp
            return clampFraction(currentFraction + deltaFraction)
        }
    }
}
