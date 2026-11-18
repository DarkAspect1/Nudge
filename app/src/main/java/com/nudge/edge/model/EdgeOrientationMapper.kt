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

    fun mapLandscapeEdgeToPhysicalPortrait(landscapeEdge: ScreenEdge, rotation: Int): ScreenEdge {
        if (rotation == Surface.ROTATION_180) {
            return when (landscapeEdge) {
                ScreenEdge.RIGHT -> ScreenEdge.TOP
                ScreenEdge.LEFT -> ScreenEdge.BOTTOM
                ScreenEdge.TOP -> ScreenEdge.LEFT
                ScreenEdge.BOTTOM -> ScreenEdge.RIGHT
            }
        }
        return when (landscapeEdge) {
            ScreenEdge.RIGHT -> ScreenEdge.BOTTOM
            ScreenEdge.LEFT -> ScreenEdge.TOP
            ScreenEdge.TOP -> ScreenEdge.RIGHT
            ScreenEdge.BOTTOM -> ScreenEdge.LEFT
        }
    }

    fun mapLandscapeOffsetToPhysicalPortrait(
        landscapeEdge: ScreenEdge,
        offsetPercent: Float,
        rotation: Int
    ): Float {
        val physicalEdge = mapLandscapeEdgeToPhysicalPortrait(landscapeEdge, rotation)
        val isHorizontal = physicalEdge == ScreenEdge.TOP || physicalEdge == ScreenEdge.BOTTOM
        val invertForRotation = rotation == Surface.ROTATION_180
        return if (isHorizontal xor invertForRotation) {
            (1f - offsetPercent).coerceIn(0f, 1f)
        } else {
            offsetPercent.coerceIn(0f, 1f)
        }
    }

    fun resolveActiveHudScreenEdge(
        settings: NudgeSettings,
        rotation: Int,
        previewLandscape: Boolean = false
    ): ScreenEdge {
        return if (isLandscapeRotation(rotation)) {
            settings.landscapeHudEdgeSide
        } else if (previewLandscape) {
            mapLandscapeEdgeToPhysicalPortrait(settings.landscapeHudEdgeSide, rotation)
        } else {
            resolvePhysicalEdge(settings.effectiveHudEdgeSide, rotation)
        }
    }

    fun resolveActiveHudAxisFraction(
        settings: NudgeSettings,
        rotation: Int,
        previewLandscape: Boolean = false
    ): Float {
        return if (isLandscapeRotation(rotation)) {
            settings.landscapeHudYFraction.coerceIn(0f, 1f)
        } else if (previewLandscape) {
            mapLandscapeOffsetToPhysicalPortrait(
                settings.landscapeHudEdgeSide,
                settings.landscapeHudYFraction,
                rotation
            )
        } else {
            resolveAxisFraction(settings.hudYFraction, rotation)
        }
    }

    fun computeActiveHudTopOrLeftPx(
        settings: NudgeSettings,
        rotation: Int,
        axisLengthPx: Float,
        elementSpanPx: Int,
        previewLandscape: Boolean = false
    ): Int {
        val fraction = resolveActiveHudAxisFraction(settings, rotation, previewLandscape)
        val centerPx = (fraction * axisLengthPx).coerceIn(0f, axisLengthPx).roundToInt()
        val maxOffset = (axisLengthPx.roundToInt() - elementSpanPx).coerceAtLeast(0)
        return (centerPx - elementSpanPx / 2).coerceIn(0, maxOffset)
    }

    fun isLandscapeLayout(rotation: Int, previewLandscape: Boolean): Boolean {
        return isLandscapeRotation(rotation) || previewLandscape
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
