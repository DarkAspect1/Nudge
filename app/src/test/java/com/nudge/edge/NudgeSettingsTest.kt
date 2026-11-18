package com.nudge.edge

import android.view.Surface
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.AppMode
import com.nudge.edge.model.DndModeType
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgePopStyle
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.HardwareTrigger
import com.nudge.edge.model.HudTriggerMode
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.model.toScreenEdge
import com.nudge.edge.ui.components.DND_BEZEL_FILLET_DP
import com.nudge.edge.ui.components.FlatPopItem
import com.nudge.edge.ui.components.mergeFlatPopItems
import com.nudge.edge.ui.components.HUD_CUTOUT_BEZEL_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_FILLET_TAIL_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_INNER_PAD_DP
import com.nudge.edge.ui.components.HUD_CUTOUT_PILL_START_OFFSET_DP
import com.nudge.edge.ui.components.MODES_BEZEL_FILLET_DP
import com.nudge.edge.ui.components.computeDndOuterSpanDp
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudDrawnPillSpanDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import com.nudge.edge.ui.components.computeModesOuterSpanDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeSettingsTest {

    @Test
    fun defaultsMatchSpecification() {
        val defaults = NudgeSettings()
        assertEquals(EdgeSide.RIGHT, defaults.popEdgeSide)
        assertEquals(14f, defaults.popWidthDp, 0.001f)
        assertEquals(72f, defaults.popHeightDp, 0.001f)
        assertEquals(155f, defaults.hudHeightDp, 0.001f)
        assertEquals(45f, defaults.hudWidthDp, 0.001f)
        assertEquals(15f, defaults.hudCornerRadiusDp, 0.001f)
        assertEquals(1800L, defaults.hudAutoHideMs)
        assertEquals(HudTriggerMode.SIMULTANEOUS, defaults.hudTriggerMode)
        assertTrue(defaults.enableVolumeSliderHud)
        assertFalse(defaults.showSystemPanelWhenHudOff)
        assertFalse(defaults.floatingMode)
        assertEquals(0f, defaults.hudEdgeInsetDp, 0.001f)
        assertTrue(defaults.enableEdgePopInLandscape)
        assertTrue(defaults.enableSliderHudInLandscape)
        assertEquals(ScreenEdge.RIGHT, defaults.landscapeHudEdgeSide)
        assertEquals(0.50f, defaults.landscapeHudYFraction, 0.001f)
        assertTrue(defaults.showSystemPanelInLandscape)
        assertEquals(200L, defaults.popAutoHideMs)
        assertTrue(defaults.enablePowerButtonPop)
        assertEquals(0.50f, defaults.powerButtonYFraction, 0.001f)
        assertEquals(14f, defaults.powerPopWidthDp, 0.001f)
        assertEquals(72f, defaults.powerPopHeightDp, 0.001f)
        assertEquals(com.nudge.edge.model.EdgePopStyle.ROUNDED_TOP, defaults.edgePopStyle)
        assertFalse(defaults.edgePopFlatTopGlowEnabled)
        assertTrue(defaults.hudSideSynced)
        assertTrue(defaults.hapticsEnabled)
        assertTrue(defaults.edgePopHapticsEnabled)
        assertTrue(defaults.volumeSliderHapticsEnabled)
        assertTrue(defaults.soundIconModeSwitchHapticsEnabled)
        assertTrue(defaults.dndPanelHapticsEnabled)
        assertTrue(defaults.actionsPanelHapticsEnabled)
        assertTrue(defaults.nudgeHapticsEnabled)
        assertFalse(defaults.mixerAttachToSlider)
        assertEquals(50f, defaults.mixerPositionXPercent, 0.001f)
        assertEquals(50f, defaults.mixerPositionYPercent, 0.001f)
        assertEquals(228f, defaults.mixerPopupWidthDp, 0.001f)
        assertEquals(250f, defaults.mixerPopupHeightDp, 0.001f)
        assertEquals(38f, defaults.mixerPillWidthDp, 0.001f)
        assertEquals(130f, defaults.mixerPillHeightDp, 0.001f)
        assertEquals(10f, defaults.mixerPillSpacingDp, 0.001f)
        assertFalse(defaults.lightMode)
        assertTrue(defaults.isEdgePopMasterEnabled)

        assertTrue(defaults.enableDndToggle)
        assertEquals(DndModeType.SILENT_RING, defaults.dndModeType)
        assertEquals(HardwareTrigger.NONE, defaults.dndTrigger)
        assertEquals(250L, defaults.dndDoubleTapTimeoutMs)
        assertEquals(1500L, defaults.dndTimeoutMs)
        assertEquals(com.nudge.edge.model.BezelEdge.RIGHT, defaults.dndPortraitEdge)
        assertEquals(0.50f, defaults.dndPortraitOffsetPercent, 0.001f)
        assertEquals(com.nudge.edge.model.BezelEdge.TOP, defaults.dndLandscapeEdge)
        assertEquals(0.50f, defaults.dndLandscapeOffsetPercent, 0.001f)
        assertEquals(42f, defaults.dndThicknessDp, 0.001f)
        assertEquals(108f, defaults.dndLengthDp, 0.001f)
        assertEquals(24f, defaults.dndIconSizeDp, 0.001f)
        assertEquals(38f, defaults.dndUnfocusedOffsetDp, 0.001f)
        assertEquals(80, defaults.dndPanelFluidityPercent)

        assertTrue(defaults.enableActionsPanel)
        assertEquals(HardwareTrigger.NONE, defaults.modesTrigger)
        assertEquals(250L, defaults.modesDoubleTapTimeoutMs)
        assertEquals(120L, defaults.simultaneousPressTimeoutMs)
        assertEquals(24f, defaults.actionsIconSizeDp, 0.001f)
        assertEquals(32f, defaults.actionsIconSpacingDp, 0.001f)
        assertEquals(80, defaults.actionsPanelFluidityPercent)
        assertFalse(defaults.isHideAccessibilityEnabled)
        assertTrue(defaults.cloakedPackages.isEmpty())
        assertTrue(defaults.isAutoKeepAliveViaShizuku)
        assertFalse(defaults.isShizukuWatchdogEnabled)
        assertTrue(defaults.enableModesNotificationBridge)
        assertFalse(defaults.enableModesBuiltInPresets)
        assertEquals(2000L, defaults.modesTimeoutMs)
        assertEquals(com.nudge.edge.model.BezelEdge.RIGHT, defaults.modesPortraitEdge)
        assertEquals(0.50f, defaults.modesPortraitOffsetPercent, 0.001f)
        assertEquals(com.nudge.edge.model.BezelEdge.TOP, defaults.modesLandscapeEdge)
        assertEquals(0.50f, defaults.modesLandscapeOffsetPercent, 0.001f)
        assertEquals(44f, defaults.modesThicknessDp, 0.001f)
        assertEquals(148f, defaults.modesLengthDp, 0.001f)
        assertEquals(5, defaults.customModes.size)
        assertEquals(75, defaults.edgePopFluidityPercent)
        assertEquals(75, defaults.volumeSliderFluidityPercent)
        assertFalse(defaults.isPerAppVolumeEnabled)
        assertEquals("ALONGSIDE_SLIDER", defaults.perAppVolumeDisplayMode)
        assertTrue(defaults.isAlwaysShowCallSliderInMixer)
        assertFalse(defaults.showVolumeCounter)
        assertEquals(0, defaults.volumeSliderPillTransparency)
        assertEquals(0f, defaults.volumeSliderBlur, 0.001f)
        assertEquals(0f, defaults.volumeSliderPillBlurDp, 0.001f)
        assertFalse(defaults.useShizukuForDnd)
        assertFalse(defaults.enablePersistentNudges)
        assertEquals(1, defaults.persistentNudges.size)
        assertEquals(14f, defaults.nudgeWidthDp, 0.001f)
        assertEquals(64f, defaults.nudgeHeightDp, 0.001f)
        assertTrue(defaults.nudgeGlowEnabled)
        assertEquals(50f, defaults.nudgeMenuWidthDp, 0.001f)
        assertEquals(300f, defaults.nudgeMenuHeightDp, 0.001f)
        assertEquals(0f, defaults.nudgeMenuXFraction, 0.001f)
        assertEquals(0.50f, defaults.nudgeMenuYFraction, 0.001f)
        assertEquals(ScreenEdge.RIGHT, defaults.nudgeMenuEdge)
    }

    @Test
    fun effectiveHudSideFollowsSyncSetting() {
        val synced = NudgeSettings(
            popEdgeSide = EdgeSide.LEFT,
            hudSideSynced = true,
            hudEdgeSide = EdgeSide.RIGHT
        )
        assertEquals(EdgeSide.LEFT, synced.effectiveHudEdgeSide)

        val independent = synced.copy(hudSideSynced = false)
        assertEquals(EdgeSide.RIGHT, independent.effectiveHudEdgeSide)
    }

    @Test
    fun clampingRespectsAllBounds() {
        assertEquals(0f, NudgeSettings.clampFraction(-0.4f), 0.0001f)
        assertEquals(1f, NudgeSettings.clampFraction(1.4f), 0.0001f)
        assertEquals(4f, NudgeSettings.clampPopWidth(1f), 0.001f)
        assertEquals(40f, NudgeSettings.clampPopWidth(60f), 0.001f)
        assertEquals(24f, NudgeSettings.clampPopHeight(10f), 0.001f)
        assertEquals(200f, NudgeSettings.clampPopHeight(240f), 0.001f)
        assertEquals(100L, NudgeSettings.clampPopAutoHideMs(20L))
        assertEquals(1500L, NudgeSettings.clampPopAutoHideMs(5000L))
        assertEquals(100f, NudgeSettings.clampHudHeight(50f), 0.001f)
        assertEquals(260f, NudgeSettings.clampHudHeight(420f), 0.001f)
        assertEquals(28f, NudgeSettings.clampHudWidth(16f), 0.001f)
        assertEquals(64f, NudgeSettings.clampHudWidth(90f), 0.001f)
        assertEquals(0f, NudgeSettings.clampHudCornerRadius(-4f), 0.001f)
        assertEquals(32f, NudgeSettings.clampHudCornerRadius(45f), 0.001f)
        assertEquals(0f, NudgeSettings.clampHudEdgeInset(-4f), 0.001f)
        assertEquals(48f, NudgeSettings.clampHudEdgeInset(80f), 0.001f)
        assertEquals(250L, NudgeSettings.clampAutoHideMs(100L))
        assertEquals(5000L, NudgeSettings.clampAutoHideMs(9000L))

        assertEquals(500L, NudgeSettings.clampDndTimeoutMs(100L))
        assertEquals(4000L, NudgeSettings.clampDndTimeoutMs(8000L))
        assertEquals(10f, NudgeSettings.clampDndWidth(5f), 0.001f)
        assertEquals(72f, NudgeSettings.clampDndWidth(100f), 0.001f)
        assertEquals(40f, NudgeSettings.clampDndHeight(20f), 0.001f)
        assertEquals(240f, NudgeSettings.clampDndHeight(300f), 0.001f)

        assertEquals(800L, NudgeSettings.clampModesTimeoutMs(200L))
        assertEquals(5000L, NudgeSettings.clampModesTimeoutMs(10000L))
        assertEquals(10f, NudgeSettings.clampModesWidth(5f), 0.001f)
        assertEquals(72f, NudgeSettings.clampModesWidth(90f), 0.001f)
        assertEquals(40f, NudgeSettings.clampModesHeight(20f), 0.001f)
        assertEquals(240f, NudgeSettings.clampModesHeight(400f), 0.001f)

        assertEquals(10f, NudgeSettings.clampIconSizeDp(4f), 0.001f)
        assertEquals(48f, NudgeSettings.clampIconSizeDp(50f), 0.001f)
        assertEquals(14f, NudgeSettings.clampDndUnfocusedOffsetDp(5f), 0.001f)
        assertEquals(90f, NudgeSettings.clampDndUnfocusedOffsetDp(100f), 0.001f)
        assertEquals(0, NudgeSettings.clampFluidityPercent(-10))
        assertEquals(100, NudgeSettings.clampFluidityPercent(150))
        assertEquals(5, NudgeSettings.clampDeviceMaxVolumeSteps(2))
        assertEquals(300, NudgeSettings.clampDeviceMaxVolumeSteps(500))

        assertEquals(1L, NudgeSettings.clampDoubleTapTimeoutMs(0L))
        assertEquals(500L, NudgeSettings.clampDoubleTapTimeoutMs(600L))
        assertEquals(250L, NudgeSettings.clampDoubleTapTimeoutMs(250L))

        assertEquals(1L, NudgeSettings.clampSimultaneousTimeoutMs(0L))
        assertEquals(500L, NudgeSettings.clampSimultaneousTimeoutMs(600L))
        assertEquals(120L, NudgeSettings.clampSimultaneousTimeoutMs(120L))

        assertEquals(16f, NudgeSettings.clampActionsIconSpacingDp(10f), 0.001f)
        assertEquals(64f, NudgeSettings.clampActionsIconSpacingDp(80f), 0.001f)
        assertEquals(32f, NudgeSettings.clampActionsIconSpacingDp(32f), 0.001f)

        assertEquals(0.10f, NudgeSettings.clampBezelYFraction(0.02f), 0.001f)
        assertEquals(0.90f, NudgeSettings.clampBezelYFraction(0.98f), 0.001f)

        assertEquals(160f, NudgeSettings.clampMixerPopupWidth(100f), 0.001f)
        assertEquals(380f, NudgeSettings.clampMixerPopupWidth(500f), 0.001f)
        assertEquals(170f, NudgeSettings.clampMixerPopupHeight(120f), 0.001f)
        assertEquals(420f, NudgeSettings.clampMixerPopupHeight(600f), 0.001f)
        assertEquals(24f, NudgeSettings.clampMixerPillWidth(10f), 0.001f)
        assertEquals(72f, NudgeSettings.clampMixerPillWidth(100f), 0.001f)
        assertEquals(70f, NudgeSettings.clampMixerPillHeight(40f), 0.001f)
        assertEquals(240f, NudgeSettings.clampMixerPillHeight(300f), 0.001f)
        assertEquals(2f, NudgeSettings.clampMixerPillSpacing(0f), 0.001f)
        assertEquals(36f, NudgeSettings.clampMixerPillSpacing(50f), 0.001f)
    }

    @Test
    fun customModesSerializationRoundTrip() {
        val modes = listOf(
            com.nudge.edge.model.CustomMode(
                id = "test_mode",
                label = "Gaming",
                notificationKeyword = "NUDGE_MODE_GAMING",
                iconKey = "Gamepad",
                colorHex = 0xFFFF9500L,
                dndMode = "PRIORITY",
                ringerMode = "SILENT"
            )
        )
        val serialized = com.nudge.edge.model.serializeCustomModes(modes)
        val deserialized = com.nudge.edge.model.deserializeCustomModes(serialized)
        assertEquals(1, deserialized.size)
        assertEquals("test_mode", deserialized[0].id)
        assertEquals("Gaming", deserialized[0].label)
        assertEquals("NUDGE_MODE_GAMING", deserialized[0].notificationKeyword)
        assertEquals("Gamepad", deserialized[0].iconKey)
        assertEquals(0xFFFF9500L, deserialized[0].colorHex)
        assertEquals("PRIORITY", deserialized[0].dndMode)
        assertEquals("SILENT", deserialized[0].ringerMode)
        assertEquals("Gaming", deserialized[0].name)
        assertEquals("NUDGE_MODE_GAMING", deserialized[0].keyword)
    }

    @Test
    fun bezelEdgeMapsCorrectlyToScreenEdge() {
        assertEquals(ScreenEdge.LEFT, com.nudge.edge.model.BezelEdge.LEFT.toScreenEdge())
        assertEquals(ScreenEdge.RIGHT, com.nudge.edge.model.BezelEdge.RIGHT.toScreenEdge())
        assertEquals(ScreenEdge.TOP, com.nudge.edge.model.BezelEdge.TOP.toScreenEdge())
        assertEquals(ScreenEdge.BOTTOM, com.nudge.edge.model.BezelEdge.BOTTOM.toScreenEdge())
    }

    @Test
    fun volumeSliderOverlapDetectionCalculatesCorrectly() {
        val settings = NudgeSettings(
            popEdgeSide = EdgeSide.RIGHT,
            hudSideSynced = true,
            hudEdgeSide = EdgeSide.RIGHT,
            volumeUpYFraction = 0.27f,
            volumeDownYFraction = 0.37f,
            hudYFraction = 0.30f,
            hudHeightDp = 155f,
            popHeightDp = 72f
        )
        val popEdge = EdgeOrientationMapper.resolvePhysicalEdge(settings.popEdgeSide, Surface.ROTATION_0)
        val sliderEdge = EdgeOrientationMapper.resolveHudScreenEdge(settings, Surface.ROTATION_0)
        assertEquals(ScreenEdge.RIGHT, popEdge)
        assertEquals(ScreenEdge.RIGHT, sliderEdge)

        val leftHudSettings = settings.copy(hudSideSynced = false, hudEdgeSide = EdgeSide.LEFT)
        val leftSliderEdge = EdgeOrientationMapper.resolveHudScreenEdge(leftHudSettings, Surface.ROTATION_0)
        assertEquals(ScreenEdge.LEFT, leftSliderEdge)
        assertTrue(popEdge != leftSliderEdge)
    }

    @Test
    fun oneDpStepAdjustsFractionAccurately() {
        val screenHeightDp = 800f
        val start = 0.5f
        val up = NudgeSettings.stepFractionByDp(start, 1f, screenHeightDp)
        val down = NudgeSettings.stepFractionByDp(start, -1f, screenHeightDp)
        assertEquals(0.5f + 1f / 800f, up, 0.00001f)
        assertEquals(0.5f - 1f / 800f, down, 0.00001f)
    }

    @Test
    fun physicalEdgeMapsAcrossAllRotations() {
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_0)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_0)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_180)
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_180)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.RIGHT, Surface.ROTATION_270)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolvePhysicalEdge(EdgeSide.LEFT, Surface.ROTATION_270)
        )
    }

    @Test
    fun landscapeAndSystemPanelFallbackRulesWorkCorrectly() {
        val bothOffWithSystemPanel = NudgeSettings(
            enableEdgePopInLandscape = false,
            enableSliderHudInLandscape = false,
            showSystemPanelInLandscape = true
        )
        assertFalse(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = true, bothOffWithSystemPanel))
        assertTrue(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = true, bothOffWithSystemPanel))

        val bothOffSilent = NudgeSettings(
            enableEdgePopInLandscape = false,
            enableSliderHudInLandscape = false,
            showSystemPanelInLandscape = false
        )
        assertTrue(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = true, bothOffSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = true, bothOffSilent))

        val popOnlyShowSystem = NudgeSettings(
            enableEdgePopInLandscape = true,
            enableSliderHudInLandscape = false,
            showSystemPanelInLandscape = true
        )
        assertTrue(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = true, popOnlyShowSystem))
        assertTrue(EdgeOrientationMapper.shouldShowEdgePop(isLandscape = true, popOnlyShowSystem))
        assertFalse(EdgeOrientationMapper.shouldShowSliderHud(isLandscape = true, popOnlyShowSystem))
        assertTrue(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = true, popOnlyShowSystem))

        val hudOffPortraitSilent = NudgeSettings(
            enableVolumeSliderHud = false,
            showSystemPanelWhenHudOff = false
        )
        assertTrue(EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape = false, hudOffPortraitSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSliderHud(isLandscape = false, hudOffPortraitSilent))
        assertFalse(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = false, hudOffPortraitSilent))

        val hudOffPortraitSystem = NudgeSettings(
            enableVolumeSliderHud = false,
            showSystemPanelWhenHudOff = true
        )
        assertTrue(EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape = false, hudOffPortraitSystem))
    }

    @Test
    fun axisTranslationAcrossRotationsComputesCorrectly() {
        assertEquals(0.25f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_90), 0.0001f)
        assertEquals(0.75f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_270), 0.0001f)
        assertEquals(0.75f, EdgeOrientationMapper.resolveAxisFraction(0.25f, Surface.ROTATION_180), 0.0001f)

        val xPos = EdgeOrientationMapper.computeElementTopOrLeftPx(
            portraitYFraction = 0.25f,
            rotation = Surface.ROTATION_90,
            axisLengthPx = 2000f,
            elementSpanPx = 100
        )
        assertEquals(450, xPos)
    }

    @Test
    fun dynamicDeviceVolumeScaleSteppingWorksAccurately() {

        assertEquals(8, VolumeController.computeSteppedVolume(5, 1, 3, 0, 15))
        assertEquals(15, VolumeController.computeSteppedVolume(13, 1, 5, 0, 15))
        assertEquals(0, VolumeController.computeSteppedVolume(2, -1, 5, 0, 15))

        assertEquals(55, VolumeController.computeSteppedVolume(50, 1, 5, 0, 100))

        val step5First = VolumeController.computeSteppedVolume(0, 1, 5, 0, 150)
        assertEquals(8, step5First)
        val step5Second = VolumeController.computeSteppedVolume(step5First, 1, 5, 0, 150)
        assertEquals(15, step5Second)

        val step7First = VolumeController.computeSteppedVolume(0, 1, 7, 0, 150)
        assertEquals(11, step7First)
        val step7Second = VolumeController.computeSteppedVolume(step7First, 1, 7, 0, 150)
        assertEquals(21, step7Second)

        val step10First = VolumeController.computeSteppedVolume(0, 1, 10, 0, 150)
        assertEquals(15, step10First)
        val step10Second = VolumeController.computeSteppedVolume(step10First, 1, 10, 0, 150)
        assertEquals(30, step10Second)
    }

    @Test
    fun hudFollowsSelectedEdgeInLandscapeOrientation() {
        val rightLandscape = NudgeSettings(
            enableSliderHudInLandscape = true,
            landscapeHudEdgeSide = ScreenEdge.RIGHT
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolveHudScreenEdge(rightLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.RIGHT,
            EdgeOrientationMapper.resolveHudScreenEdge(rightLandscape, Surface.ROTATION_270)
        )

        val leftLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.LEFT)
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolveHudScreenEdge(leftLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.LEFT,
            EdgeOrientationMapper.resolveHudScreenEdge(leftLandscape, Surface.ROTATION_270)
        )

        val topLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.TOP)
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolveHudScreenEdge(topLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.TOP,
            EdgeOrientationMapper.resolveHudScreenEdge(topLandscape, Surface.ROTATION_270)
        )

        val bottomLandscape = rightLandscape.copy(landscapeHudEdgeSide = ScreenEdge.BOTTOM)
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolveHudScreenEdge(bottomLandscape, Surface.ROTATION_90)
        )
        assertEquals(
            ScreenEdge.BOTTOM,
            EdgeOrientationMapper.resolveHudScreenEdge(bottomLandscape, Surface.ROTATION_270)
        )
    }

    @Test
    fun edgeMergedHudCutoutUsesZeroBezelPadAndCompactFillet() {
        assertEquals(0f, HUD_CUTOUT_BEZEL_PAD_DP, 0.001f)
        assertEquals(6.0f, HUD_CUTOUT_INNER_PAD_DP, 0.001f)
        assertEquals(22f, HUD_CUTOUT_FILLET_TAIL_DP, 0.001f)
        assertEquals(28f, HUD_CUTOUT_PILL_START_OFFSET_DP, 0.001f)

        val defaults = NudgeSettings()
        val drawnSpanDp = computeHudDrawnPillSpanDp(defaults.hudHeightDp)
        assertEquals(defaults.hudWidthDp + 6.0f, computeHudCutoutThicknessDp(defaults.hudWidthDp, defaults.hudEdgeInsetDp), 0.001f)
        assertEquals(defaults.hudWidthDp + 12f + 6.0f, computeHudCutoutThicknessDp(defaults.hudWidthDp, 12f), 0.001f)
        assertEquals(drawnSpanDp + (22f + 6.0f) * 2f, computeHudOuterSpanDp(defaults.hudHeightDp), 0.001f)
    }

    @Test
    fun dndAndModesOuterSpanFormulasMatchFilletSpec() {
        assertEquals(34f, DND_BEZEL_FILLET_DP, 0.001f)
        assertEquals(34f, MODES_BEZEL_FILLET_DP, 0.001f)

        assertEquals(108f + 68f, computeDndOuterSpanDp(108f), 0.001f)
        assertEquals(148f + 68f, computeModesOuterSpanDp(148f), 0.001f)
    }

    @Test
    fun appModesMatchSamsungKeywords() {
        assertEquals("NUDGE_MODE_NORMAL", AppMode.NORMAL.keyword)
        assertEquals("NUDGE_MODE_SILENT", AppMode.SILENT.keyword)
        assertEquals("NUDGE_MODE_SLEEP", AppMode.SLEEP.keyword)
        assertEquals("NUDGE_MODE_WORK", AppMode.WORK.keyword)
        assertEquals("NUDGE_MODE_PERSONAL", AppMode.PERSONAL.keyword)
    }

    @Test
    fun hardwareTriggersCountAndEntriesMatch10Options() {
        assertEquals(8, HardwareTrigger.entries.size)
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.NONE))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.BOTH_VOLUME_SHORT))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.DOUBLE_TAP_VOL_UP))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.DOUBLE_TAP_VOL_DOWN))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.LONG_PRESS_POWER))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.CUSTOM_BTN_SINGLE))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.CUSTOM_BTN_DOUBLE))
        assertTrue(HardwareTrigger.entries.contains(HardwareTrigger.CUSTOM_BTN_LONG))
    }

    @Test
    fun flatTopOverlap40PercentRule() {
        val pop1 = FlatPopItem(startPx = 100f, endPx = 200f, protrusionPx = 14f)
        val pop2 = FlatPopItem(startPx = 220f, endPx = 320f, protrusionPx = 14f)
        val resultSeparate = mergeFlatPopItems(listOf(pop1, pop2))
        assertEquals(2, resultSeparate.size)

        val pop3 = FlatPopItem(startPx = 100f, endPx = 200f, protrusionPx = 14f)
        val pop4 = FlatPopItem(startPx = 170f, endPx = 270f, protrusionPx = 14f)
        val resultBelow40 = mergeFlatPopItems(listOf(pop3, pop4))
        assertEquals(2, resultBelow40.size)

        val pop5 = FlatPopItem(startPx = 100f, endPx = 200f, protrusionPx = 14f)
        val pop6 = FlatPopItem(startPx = 160f, endPx = 260f, protrusionPx = 18f)
        val result40 = mergeFlatPopItems(listOf(pop5, pop6))
        assertEquals(1, result40.size)
        assertEquals(100f, result40[0].startPx, 0.001f)
        assertEquals(260f, result40[0].endPx, 0.001f)
        assertEquals(18f, result40[0].protrusionPx, 0.001f)

        val pop7 = FlatPopItem(startPx = 100f, endPx = 200f, protrusionPx = 14f)
        val pop8 = FlatPopItem(startPx = 100f, endPx = 200f, protrusionPx = 20f)
        val resultSamePos = mergeFlatPopItems(listOf(pop7, pop8))
        assertEquals(1, resultSamePos.size)
        assertEquals(100f, resultSamePos[0].startPx, 0.001f)
        assertEquals(200f, resultSamePos[0].endPx, 0.001f)
        assertEquals(20f, resultSamePos[0].protrusionPx, 0.001f)
    }

    @Test
    fun edgePopStyleEnumValues() {
        assertEquals(2, EdgePopStyle.entries.size)
        assertEquals("Rounded Top", EdgePopStyle.ROUNDED_TOP.displayName)
        assertEquals("Flat Top", EdgePopStyle.FLAT_TOP.displayName)
    }
}
