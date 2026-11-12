package com.nudge.edge.model

import android.view.Surface
import kotlin.math.roundToInt

object EdgeOrientationMapper {

    fun isLandscapeRotation(rotation: Int): Boolean {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    fun resolvePhysicalEdge(portraitSide: EdgeSide, rotation: Int): ScreenEdge {
        return when (rotation) {
            Surface.ROTATION_90 -> {
                if (portraitSide == EdgeSide.RIGHT) ScreenEdge.TOP else ScreenEdge.BOTTOM
            }
            Surface.ROTATION_180 -> {
                if (portraitSide == EdgeSide.RIGHT) ScreenEdge.LEFT else ScreenEdge.RIGHT
            }
            Surface.ROTATION_270 -> {
                if (portraitSide == EdgeSide.RIGHT) ScreenEdge.BOTTOM else ScreenEdge.TOP
            }
            else -> {
                if (portraitSide == EdgeSide.RIGHT) ScreenEdge.RIGHT else ScreenEdge.LEFT
            }
        }
    }

    fun resolveHudScreenEdge(settings: NudgeSettings, rotation: Int): ScreenEdge {
        return if (isLandscapeRotation(rotation)) {
            settings.landscapeHudEdgeSide
        } else {
            resolvePhysicalEdge(settings.effectiveHudEdgeSide, rotation)
        }
    }

    fun resolveAxisFraction(portraitYFraction: Float, rotation: Int): Float {
        val clamped = portraitYFraction.coerceIn(0f, 1f)
        return when (rotation) {
            Surface.ROTATION_180,
            Surface.ROTATION_270 -> 1f - clamped
            else -> clamped
        }
    }

    fun resolveHudAxisFraction(settings: NudgeSettings, rotation: Int): Float {
        return if (isLandscapeRotation(rotation)) {
            settings.landscapeHudYFraction.coerceIn(0f, 1f)
        } else {
            resolveAxisFraction(settings.hudYFraction, rotation)
        }
    }

    fun computeElementCenterPx(
        portraitYFraction: Float,
        rotation: Int,
        axisLengthPx: Float
    ): Float {
        val baseFraction = resolveAxisFraction(portraitYFraction, rotation)
        return (baseFraction * axisLengthPx).coerceIn(0f, axisLengthPx)
    }

    fun computeElementTopOrLeftPx(
        portraitYFraction: Float,
        rotation: Int,
        axisLengthPx: Float,
        elementSpanPx: Int
    ): Int {
        val centerPx = computeElementCenterPx(
            portraitYFraction = portraitYFraction,
            rotation = rotation,
            axisLengthPx = axisLengthPx
        ).roundToInt()
        val maxOffset = (axisLengthPx.roundToInt() - elementSpanPx).coerceAtLeast(0)
        return (centerPx - elementSpanPx / 2).coerceIn(0, maxOffset)
    }

    fun computeHudTopOrLeftPx(
        settings: NudgeSettings,
        rotation: Int,
        axisLengthPx: Float,
        elementSpanPx: Int
    ): Int {
        val fraction = resolveHudAxisFraction(settings, rotation)
        val centerPx = (fraction * axisLengthPx).coerceIn(0f, axisLengthPx).roundToInt()
        val maxOffset = (axisLengthPx.roundToInt() - elementSpanPx).coerceAtLeast(0)
        return (centerPx - elementSpanPx / 2).coerceIn(0, maxOffset)
    }

    fun shouldShowEdgePop(isLandscape: Boolean, settings: NudgeSettings): Boolean {
        return !isLandscape || settings.enableEdgePopInLandscape
    }

    fun shouldShowSliderHud(isLandscape: Boolean, settings: NudgeSettings): Boolean {
        if (!settings.enableVolumeSliderHud) return false
        return !isLandscape || settings.enableSliderHudInLandscape
    }

    fun shouldShowSystemVolumePanel(isLandscape: Boolean, settings: NudgeSettings): Boolean {
        if (shouldShowSliderHud(isLandscape, settings)) return false
        return if (isLandscape) {
            if (!settings.enableVolumeSliderHud) {
                settings.showSystemPanelWhenHudOff && settings.showSystemPanelInLandscape
            } else {
                settings.showSystemPanelInLandscape
            }
        } else {
            settings.showSystemPanelWhenHudOff
        }
    }

    fun shouldConsumeVolumeKey(isLandscape: Boolean, settings: NudgeSettings): Boolean {
        if (!isLandscape) return true
        val popShown = shouldShowEdgePop(true, settings)
        val hudShown = shouldShowSliderHud(true, settings)
        if (!popShown && !hudShown) {
            return !shouldShowSystemVolumePanel(true, settings)
        }
        return true
    }
}