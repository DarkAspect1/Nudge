package com.nudge.edge.model

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

enum class HudTriggerMode {
    SIMULTANEOUS,
    SECOND_PRESS,
    SWIPE_FROM_POP
}

enum class PreviewTarget {
    NONE,
    POP_UP,
    POP_DOWN,
    POP_BOTH,
    POP_POWER,
    HUD
}

data class NudgeSettings(
    val popEdgeSide: EdgeSide = EdgeSide.RIGHT,
    val volumeUpYFraction: Float = 0.27f,
    val volumeDownYFraction: Float = 0.37f,
    val popWidthDp: Float = 14f,
    val popHeightDp: Float = 72f,
    val popAutoHideMs: Long = 200L,
    val enableEdgePopInLandscape: Boolean = true,
    val enablePowerButtonPop: Boolean = true,
    val powerButtonYFraction: Float = 0.50f,
    val powerPopWidthDp: Float = 14f,
    val powerPopHeightDp: Float = 72f,
    val enableVolumeSliderHud: Boolean = true,
    val showSystemPanelWhenHudOff: Boolean = false,
    val volumeStep: Int = 7,
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
    val lightMode: Boolean = false
) {
    val effectiveHudEdgeSide: EdgeSide
        get() = if (hudSideSynced) popEdgeSide else hudEdgeSide

    companion object {
        const val MIN_POP_WIDTH_DP = 4f
        const val MAX_POP_WIDTH_DP = 40f
        const val MIN_POP_HEIGHT_DP = 24f
        const val MAX_POP_HEIGHT_DP = 180f
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
