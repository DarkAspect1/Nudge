package com.nudge.edge.ui.settings

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.AppMode
import com.nudge.edge.model.BezelEdge
import com.nudge.edge.model.DEFAULT_CUSTOM_MODES
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.LivePreviewController
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.model.toScreenEdge
import com.nudge.edge.ui.components.ActionsPanelOverlay
import com.nudge.edge.ui.components.DndSilentBezelOverlay
import com.nudge.edge.ui.components.EdgePopStripOverlay
import com.nudge.edge.ui.components.QuickVolumeModal
import com.nudge.edge.ui.components.VolumeSliderHud
import com.nudge.edge.ui.components.computeActionsOuterSpanDp
import com.nudge.edge.ui.components.computeDndOuterSpanDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class SettingsTab(
    val label: String,
    val icon: ImageVector
) {
    POP_SETUP("Edge Pop", Icons.Rounded.RadioButtonChecked),
    SLIDER_HUD("Slider HUD", Icons.AutoMirrored.Rounded.VolumeUp),
    MODES("Actions", Icons.Rounded.Tune),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
fun MainScreen(
    settings: NudgeSettings,
    previewTarget: PreviewTarget,
    serviceActive: Boolean,
    musicVolumeFraction: Float,
    musicMaxVolume: Int,
    realScreenWidthDp: Float,
    realScreenHeightDp: Float,
    displayRotation: Int,
    repository: SettingsRepository,
    volumeController: VolumeController,
    onVolumeFractionUpdated: (Float) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    showAccessibilityPrompt: Boolean = false,
    onDismissAccessibilityPrompt: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.POP_SETUP) }
    var localModalVisible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                localModalVisible = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val popScrollState = rememberScrollState()
    val hudScrollState = rememberScrollState()
    val modesScrollState = rememberScrollState()
    val systemScrollState = rememberScrollState()
    val portraitAxisDp = maxOf(realScreenWidthDp, realScreenHeightDp)
    val landscapeAxisDp = minOf(realScreenWidthDp, realScreenHeightDp)
    val isLandscapeScreen = realScreenWidthDp > realScreenHeightDp

    val previewLandscape by repository.previewLandscape.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SettingsCardPane(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            settings = settings,
            portraitAxisDp = portraitAxisDp,
            landscapeAxisDp = landscapeAxisDp,
            musicMaxVolume = musicMaxVolume,
            serviceActive = serviceActive,
            repository = repository,
            popScrollState = popScrollState,
            hudScrollState = hudScrollState,
            modesScrollState = modesScrollState,
            systemScrollState = systemScrollState,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            cardShape = RoundedCornerShape(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )

        if (!serviceActive) {
            LocalFallbackEdgePreviewLayer(
                settings = settings,
                previewTarget = previewTarget,
                previewLandscape = previewLandscape,
                musicVolumeFraction = musicVolumeFraction,
                realScreenWidthDp = realScreenWidthDp,
                realScreenHeightDp = realScreenHeightDp,
                displayRotation = displayRotation,
                onScrubFraction = { fraction ->
                    val prevStep = volumeController.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val nextStep = volumeController.setMusicVolumeFraction(fraction)
                    onVolumeFractionUpdated(fraction)
                    if (settings.hapticsEnabled && nextStep != prevStep) {
                        volumeController.triggerHaptic(strong = false)
                    }
                },
                onScrubStateChanged = { scrubbing ->
                    if (scrubbing) {
                        repository.requestLivePreview(PreviewTarget.HUD, durationMs = 10_000L)
                    } else {
                        onVolumeFractionUpdated(volumeController.getMusicVolumeFraction())
                        repository.requestLivePreview(PreviewTarget.HUD)
                    }
                },
                onOpenMenu = {
                    if (settings.hapticsEnabled) {
                        volumeController.triggerHaptic(strong = true)
                    }
                    localModalVisible = true
                }
            )

            val isMixerPreview = previewTarget == PreviewTarget.MIXER || previewTarget == PreviewTarget.AUDIO_MIXER
            val isModalOpen = localModalVisible || isMixerPreview

            QuickVolumeModal(
                volumeController = volumeController,
                musicFractionExternal = musicVolumeFraction,
                sliderWidthDp = settings.hudWidthDp,
                sliderHeightDp = settings.hudHeightDp,
                sliderCornerRadiusDp = settings.hudCornerRadiusDp,
                lightMode = settings.lightMode,
                hapticsEnabled = settings.effectiveVolumeSliderHaptics,
                isAlwaysShowCallSlider = settings.isAlwaysShowCallSliderInMixer,
                isVisible = isModalOpen,
                audioMixerScalePercent = settings.audioMixerScalePercent,
                audioMixerPopupWidthDp = settings.audioMixerPopupWidthDp,
                audioMixerPopupHeightDp = settings.audioMixerPopupHeightDp,
                audioMixerPillWidthDp = settings.audioMixerPillWidthDp,
                audioMixerPillHeightDp = settings.audioMixerPillHeightDp,
                audioMixerPillSpacingDp = settings.audioMixerPillSpacingDp,
                isAudioMixerAttachedToSlider = settings.isAudioMixerAttachedToSlider,
                isAudioMixerManuallySized = settings.isAudioMixerManuallySized,
                audioMixerXPercent = settings.audioMixerXPercent,
                audioMixerYPercent = settings.audioMixerYPercent,
                sliderEdge = if (settings.effectiveHudEdgeSide == EdgeSide.RIGHT) ScreenEdge.RIGHT else ScreenEdge.LEFT,
                sliderYFraction = settings.hudYFraction,
                sliderXFraction = 0.5f,
                sliderEdgeInsetDp = if (settings.floatingMode) settings.hudEdgeInsetDp else 0f,
                onMusicFractionChanged = onVolumeFractionUpdated,
                onOpenSettings = { localModalVisible = false },
                onDismiss = {
                    localModalVisible = false
                    repository.stopLivePreview(0L)
                }
            )
        }

        if (showAccessibilityPrompt) {
            AlertDialog(
                onDismissRequest = onDismissAccessibilityPrompt,
                title = {
                    Text(
                        text = "Enable Accessibility",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Text(
                        text = "Turn on Nudge for volume button overlays.",
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                dismissButton = {
                    TextButton(onClick = onDismissAccessibilityPrompt) {
                        Text(
                            text = "Cancel",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismissAccessibilityPrompt()
                            onOpenAccessibilitySettings()
                        }
                    ) {
                        Text(
                            text = "Enable",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsCardPane(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    settings: NudgeSettings,
    portraitAxisDp: Float,
    landscapeAxisDp: Float,
    musicMaxVolume: Int,
    serviceActive: Boolean,
    repository: SettingsRepository,
    popScrollState: androidx.compose.foundation.ScrollState,
    hudScrollState: androidx.compose.foundation.ScrollState,
    modesScrollState: androidx.compose.foundation.ScrollState,
    systemScrollState: androidx.compose.foundation.ScrollState,
    onOpenAccessibilitySettings: () -> Unit,
    cardShape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeScrollState = when (selectedTab) {
                SettingsTab.POP_SETUP -> popScrollState
                SettingsTab.SLIDER_HUD -> hudScrollState
                SettingsTab.MODES -> modesScrollState
                SettingsTab.SETTINGS -> systemScrollState
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(activeScrollState)
            ) {
                when (selectedTab) {
                    SettingsTab.POP_SETUP -> PopSetupTab(
                        settings = settings,
                        portraitAxisDp = portraitAxisDp,
                        repository = repository
                    )
                    SettingsTab.SLIDER_HUD -> SliderHudTab(
                        settings = settings,
                        portraitAxisDp = portraitAxisDp,
                        landscapeAxisDp = landscapeAxisDp,
                        musicMaxVolume = musicMaxVolume,
                        repository = repository
                    )
                    SettingsTab.MODES -> ModesTab(
                        settings = settings,
                        repository = repository
                    )
                    SettingsTab.SETTINGS -> SystemTab(
                        settings = settings,
                        serviceActive = serviceActive,
                        repository = repository,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings
                    )
                }
            }

            NavigationBar {
                SettingsTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalFallbackEdgePreviewLayer(
    settings: NudgeSettings,
    previewTarget: PreviewTarget,
    previewLandscape: Boolean,
    musicVolumeFraction: Float,
    realScreenWidthDp: Float,
    realScreenHeightDp: Float,
    displayRotation: Int,
    onScrubFraction: (Float) -> Unit,
    onScrubStateChanged: (Boolean) -> Unit,
    onOpenMenu: () -> Unit
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { realScreenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { realScreenHeightDp.dp.toPx() }
    val hudSpanPx = ceil(with(density) { computeHudOuterSpanDp(settings.hudHeightDp).dp.toPx() }).toInt()

    val isRealLandscape = EdgeOrientationMapper.isLandscapeRotation(displayRotation)
    val isPreviewLandscape = previewLandscape
    val isLandscape = isRealLandscape || isPreviewLandscape
    val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)

    val popScreenEdge = EdgeOrientationMapper.resolvePhysicalEdge(
        settings.popEdgeSide,
        displayRotation
    )
    val hudScreenEdge = if (isRealLandscape) {
        settings.landscapeHudEdgeSide
    } else if (isPreviewLandscape) {
        mapLandscapeEdgeToPhysicalPortrait(settings.landscapeHudEdgeSide)
    } else {
        EdgeOrientationMapper.resolveHudScreenEdge(
            settings = settings,
            rotation = displayRotation
        )
    }

    val showUp = popAllowed && (
        previewTarget == PreviewTarget.POP_UP ||
            previewTarget == PreviewTarget.POP_BOTH ||
            previewTarget == PreviewTarget.EDGE_POP_VOL_UP ||
            previewTarget == PreviewTarget.EDGE_POP_ALL
        )
    val showDown = popAllowed && (
        previewTarget == PreviewTarget.POP_DOWN ||
            previewTarget == PreviewTarget.POP_BOTH ||
            previewTarget == PreviewTarget.EDGE_POP_VOL_DOWN ||
            previewTarget == PreviewTarget.EDGE_POP_ALL
        )
    val showPower = popAllowed &&
        settings.enablePowerButtonPop &&
        (previewTarget == PreviewTarget.POP_POWER || previewTarget == PreviewTarget.EDGE_POP_POWER)
    val showHud = hudAllowed && previewTarget == PreviewTarget.HUD

    val popAlignment = edgeBoxAlignment(popScreenEdge)
    val hudAlignment = edgeBoxAlignment(hudScreenEdge)
    val popIsVertical = popScreenEdge == ScreenEdge.LEFT || popScreenEdge == ScreenEdge.RIGHT
    val hudIsVertical = hudScreenEdge == ScreenEdge.LEFT || hudScreenEdge == ScreenEdge.RIGHT
    val popMaxBreadthDp = maxOf(settings.popWidthDp, settings.powerPopWidthDp) * 1.15f

    Box(modifier = Modifier.fillMaxSize()) {
        if (popAllowed) {
            val stripModifier = if (popIsVertical) {
                Modifier
                    .align(popAlignment)
                    .width(popMaxBreadthDp.dp)
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(popAlignment)
                    .fillMaxWidth()
                    .height(popMaxBreadthDp.dp)
            }
            EdgePopStripOverlay(
                settings = settings,
                screenEdge = popScreenEdge,
                displayRotation = displayRotation,
                upExpanded = showUp,
                downExpanded = showDown,
                powerExpanded = showPower,
                modifier = stripModifier
            )
        }

        if (showHud) {
            val fraction = if (isRealLandscape) {
                settings.landscapeHudYFraction
            } else if (isPreviewLandscape) {
                mapLandscapeOffsetToPhysicalPortrait(settings.landscapeHudEdgeSide, settings.landscapeHudYFraction)
            } else {
                EdgeOrientationMapper.resolveAxisFraction(settings.hudYFraction, displayRotation)
            }
            val offset = if (hudIsVertical) {
                val y = ((fraction * screenHeightPx).coerceIn(0f, screenHeightPx) - hudSpanPx / 2f).roundToInt().coerceIn(0, (screenHeightPx - hudSpanPx).toInt().coerceAtLeast(0))
                IntOffset(0, y)
            } else {
                val x = ((fraction * screenWidthPx).coerceIn(0f, screenWidthPx) - hudSpanPx / 2f).roundToInt().coerceIn(0, (screenWidthPx - hudSpanPx).toInt().coerceAtLeast(0))
                IntOffset(x, 0)
            }
            Box(
                modifier = Modifier
                    .align(hudAlignment)
                    .offset { offset }
            ) {
                VolumeSliderHud(
                    screenEdge = hudScreenEdge,
                    hudWidthDp = settings.hudWidthDp,
                    hudHeightDp = settings.hudHeightDp,
                    hudCornerRadiusDp = settings.hudCornerRadiusDp,
                    floatingMode = settings.floatingMode,
                    edgeInsetDp = settings.hudEdgeInsetDp,
                    volumeFraction = musicVolumeFraction,
                    isVisible = true,
                    volumeSliderFluidityPercent = settings.volumeSliderFluidityPercent,
                    onVolumeScrub = onScrubFraction,
                    onScrubStateChanged = onScrubStateChanged,
                    onOpenMenu = onOpenMenu,
                    interactive = true
                )
            }
        }

        val livePreviewState by LivePreviewController.state.collectAsState()
        val effectiveTarget = if (livePreviewState.activeTarget != PreviewTarget.NONE) {
            livePreviewState.activeTarget
        } else {
            previewTarget
        }

        if (effectiveTarget == PreviewTarget.DND || effectiveTarget == PreviewTarget.DND_PANEL) {
            val dndScreenEdge = if (isRealLandscape) {
                settings.dndLandscapeEdge.toScreenEdge()
            } else if (isPreviewLandscape) {
                mapLandscapeEdgeToPhysicalPortrait(settings.dndLandscapeEdge.toScreenEdge())
            } else {
                settings.dndPortraitEdge.toScreenEdge()
            }
            val baseDndOffset = if (isRealLandscape) {
                settings.dndLandscapeOffsetPercent
            } else if (isPreviewLandscape) {
                mapLandscapeOffsetToPhysicalPortrait(settings.dndLandscapeEdge.toScreenEdge(), settings.dndLandscapeOffsetPercent)
            } else {
                settings.dndPortraitOffsetPercent
            }
            val dndOffsetPercent = livePreviewState.overrideOffsetPercent?.let { it / 100f } ?: baseDndOffset
            val dndWidth = livePreviewState.overrideWidthDp ?: settings.dndWidthDp
            val dndHeight = livePreviewState.overrideHeightDp ?: settings.dndHeightDp
            val dndUnfocusedOffset = livePreviewState.overrideUnfocusedOffsetDp ?: settings.dndUnfocusedOffsetDp
            val isVertical = dndScreenEdge == ScreenEdge.LEFT || dndScreenEdge == ScreenEdge.RIGHT
            val spanPx = ceil(with(density) { computeDndOuterSpanDp(dndHeight, settings.dndPanelFluidityPercent).dp.toPx() }).toInt()
            val offset = if (isVertical) {
                val y = ((dndOffsetPercent * screenHeightPx).coerceIn(0f, screenHeightPx) - spanPx / 2f).roundToInt().coerceIn(0, (screenHeightPx - spanPx).toInt().coerceAtLeast(0))
                IntOffset(0, y)
            } else {
                val x = ((dndOffsetPercent * screenWidthPx).coerceIn(0f, screenWidthPx) - spanPx / 2f).roundToInt().coerceIn(0, (screenWidthPx - spanPx).toInt().coerceAtLeast(0))
                IntOffset(x, 0)
            }
            Box(
                modifier = Modifier
                    .align(edgeBoxAlignment(dndScreenEdge))
                    .offset { offset }
            ) {
                DndSilentBezelOverlay(
                    screenEdge = dndScreenEdge,
                    overlayWidthDp = dndWidth,
                    overlayHeightDp = dndHeight,
                    iconSizeDp = settings.dndIconSizeDp,
                    unfocusedOffsetDp = dndUnfocusedOffset,
                    fluidityPercent = settings.dndPanelFluidityPercent,
                    modeType = settings.dndModeType,
                    isSilentOrDndActive = true,
                    isVisible = true,
                    showLabel = settings.showDndLabel
                )
            }
        }

        if (effectiveTarget == PreviewTarget.MODES || effectiveTarget == PreviewTarget.ACTIONS_PANEL) {
            val modesScreenEdge = if (isRealLandscape) {
                settings.modesLandscapeEdge.toScreenEdge()
            } else if (isPreviewLandscape) {
                mapLandscapeEdgeToPhysicalPortrait(settings.modesLandscapeEdge.toScreenEdge())
            } else {
                settings.modesPortraitEdge.toScreenEdge()
            }
            val baseModesOffset = if (isRealLandscape) {
                settings.modesLandscapeOffsetPercent
            } else if (isPreviewLandscape) {
                mapLandscapeOffsetToPhysicalPortrait(settings.modesLandscapeEdge.toScreenEdge(), settings.modesLandscapeOffsetPercent)
            } else {
                settings.modesPortraitOffsetPercent
            }
            val modesOffsetPercent = livePreviewState.overrideOffsetPercent?.let { it / 100f } ?: baseModesOffset
            val modesThickness = livePreviewState.overrideWidthDp ?: settings.modesThicknessDp
            val modesLength = livePreviewState.overrideHeightDp ?: settings.modesLengthDp
            val isVertical = modesScreenEdge == ScreenEdge.LEFT || modesScreenEdge == ScreenEdge.RIGHT
            val spanPx = ceil(with(density) { computeActionsOuterSpanDp(modesLength, settings.actionsPanelFluidityPercent).dp.toPx() }).toInt()
            val offset = if (isVertical) {
                val y = ((modesOffsetPercent * screenHeightPx).coerceIn(0f, screenHeightPx) - spanPx / 2f).roundToInt().coerceIn(0, (screenHeightPx - spanPx).toInt().coerceAtLeast(0))
                IntOffset(0, y)
            } else {
                val x = ((modesOffsetPercent * screenWidthPx).coerceIn(0f, screenWidthPx) - spanPx / 2f).roundToInt().coerceIn(0, (screenWidthPx - spanPx).toInt().coerceAtLeast(0))
                IntOffset(x, 0)
            }
            Box(
                modifier = Modifier
                    .align(edgeBoxAlignment(modesScreenEdge))
                    .offset { offset }
            ) {
                ActionsPanelOverlay(
                    screenEdge = modesScreenEdge,
                    overlayWidthDp = modesThickness,
                    overlayHeightDp = modesLength,
                    iconSizeDp = settings.actionsIconSizeDp,
                    iconSpacingDp = settings.actionsIconSpacingDp,
                    fluidityPercent = settings.actionsPanelFluidityPercent,
                    modes = settings.customModes.ifEmpty { DEFAULT_CUSTOM_MODES },
                    selectedModeId = settings.customModes.firstOrNull()?.id ?: "normal",
                    isVisible = true,
                    showLabel = settings.showActionsLabel,
                    onModeSelected = {},
                    onStepUp = {},
                    onStepDown = {}
                )
            }
        }
    }
}

private fun mapLandscapeEdgeToPhysicalPortrait(landscapeEdge: ScreenEdge): ScreenEdge {
    return when (landscapeEdge) {
        ScreenEdge.RIGHT -> ScreenEdge.BOTTOM
        ScreenEdge.LEFT -> ScreenEdge.TOP
        ScreenEdge.TOP -> ScreenEdge.RIGHT
        ScreenEdge.BOTTOM -> ScreenEdge.LEFT
    }
}

private fun mapLandscapeOffsetToPhysicalPortrait(landscapeEdge: ScreenEdge, offsetPercent: Float): Float {
    val physicalEdge = mapLandscapeEdgeToPhysicalPortrait(landscapeEdge)
    val isPhysicalHorizontal = (physicalEdge == ScreenEdge.TOP || physicalEdge == ScreenEdge.BOTTOM)
    return if (isPhysicalHorizontal) {
        (1f - offsetPercent).coerceIn(0f, 1f)
    } else {
        offsetPercent.coerceIn(0f, 1f)
    }
}

private fun edgeBoxAlignment(screenEdge: ScreenEdge): Alignment {
    return when (screenEdge) {
        ScreenEdge.RIGHT -> Alignment.TopEnd
        ScreenEdge.LEFT -> Alignment.TopStart
        ScreenEdge.TOP -> Alignment.TopStart
        ScreenEdge.BOTTOM -> Alignment.BottomStart
    }
}
