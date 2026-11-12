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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.VolumeController
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.ui.components.EdgePopStripOverlay
import com.nudge.edge.ui.components.QuickVolumeModal
import com.nudge.edge.ui.components.VolumeSliderHud
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import kotlin.math.ceil

private enum class SettingsTab(
    val label: String,
    val icon: ImageVector
) {
    POP_SETUP("Pop Setup", Icons.Rounded.RadioButtonChecked),
    SLIDER_HUD("Slider HUD", Icons.AutoMirrored.Rounded.VolumeUp),
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
    val systemScrollState = rememberScrollState()
    val portraitAxisDp = maxOf(realScreenWidthDp, realScreenHeightDp)
    val landscapeAxisDp = minOf(realScreenWidthDp, realScreenHeightDp)
    val isLandscapeScreen = realScreenWidthDp > realScreenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isLandscapeScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                ) {
                    DevicePreviewHeader(
                        serviceActive = serviceActive,
                        onStatusClick = onOpenAccessibilitySettings
                    )
                    DevicePreviewCanvas(
                        settings = settings,
                        previewTarget = previewTarget,
                        musicVolumeFraction = musicVolumeFraction,
                        realScreenWidthDp = realScreenWidthDp,
                        realScreenHeightDp = realScreenHeightDp,
                        displayRotation = displayRotation,
                        onPreviewTap = { target ->
                            repository.requestLivePreview(target)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

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
                    systemScrollState = systemScrollState,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    cardShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    DevicePreviewHeader(
                        serviceActive = serviceActive,
                        onStatusClick = onOpenAccessibilitySettings
                    )
                    DevicePreviewCanvas(
                        settings = settings,
                        previewTarget = previewTarget,
                        musicVolumeFraction = musicVolumeFraction,
                        realScreenWidthDp = realScreenWidthDp,
                        realScreenHeightDp = realScreenHeightDp,
                        displayRotation = displayRotation,
                        onPreviewTap = { target ->
                            repository.requestLivePreview(target)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

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
                    systemScrollState = systemScrollState,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxWidth()
                )
            }
        }

        if (!serviceActive) {
            LocalFallbackEdgePreviewLayer(
                settings = settings,
                previewTarget = previewTarget,
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

            if (settings.enableVolumeSliderHud) {
                QuickVolumeModal(
                    volumeController = volumeController,
                    musicFractionExternal = musicVolumeFraction,
                    sliderWidthDp = settings.hudWidthDp,
                    sliderHeightDp = settings.hudHeightDp,
                    sliderCornerRadiusDp = settings.hudCornerRadiusDp,
                    lightMode = settings.lightMode,
                    hapticsEnabled = settings.hapticsEnabled,
                    isVisible = localModalVisible,
                    onMusicFractionChanged = onVolumeFractionUpdated,
                    onOpenSettings = { localModalVisible = false },
                    onDismiss = { localModalVisible = false }
                )
            }
        }

        if (showAccessibilityPrompt) {
            AlertDialog(
                onDismissRequest = onDismissAccessibilityPrompt,
                title = {
                    Text(
                        text = "Enable Accessibility",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Text(
                        text = "Turn on Nudge for volume key overlays.",
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

    val isLandscape = EdgeOrientationMapper.isLandscapeRotation(displayRotation)
    val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)

    val popScreenEdge = EdgeOrientationMapper.resolvePhysicalEdge(
        settings.popEdgeSide,
        displayRotation
    )
    val hudScreenEdge = EdgeOrientationMapper.resolveHudScreenEdge(
        settings = settings,
        rotation = displayRotation
    )

    val showUp = popAllowed && (
        previewTarget == PreviewTarget.POP_UP ||
            previewTarget == PreviewTarget.POP_BOTH
        )
    val showDown = popAllowed && (
        previewTarget == PreviewTarget.POP_DOWN ||
            previewTarget == PreviewTarget.POP_BOTH
        )
    val showPower = popAllowed &&
        settings.enablePowerButtonPop &&
        previewTarget == PreviewTarget.POP_POWER
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
            val offset = if (hudIsVertical) {
                val y = EdgeOrientationMapper.computeHudTopOrLeftPx(
                    settings = settings,
                    rotation = displayRotation,
                    axisLengthPx = screenHeightPx,
                    elementSpanPx = hudSpanPx
                )
                IntOffset(0, y)
            } else {
                val x = EdgeOrientationMapper.computeHudTopOrLeftPx(
                    settings = settings,
                    rotation = displayRotation,
                    axisLengthPx = screenWidthPx,
                    elementSpanPx = hudSpanPx
                )
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
                    onVolumeScrub = onScrubFraction,
                    onScrubStateChanged = onScrubStateChanged,
                    onOpenMenu = onOpenMenu,
                    interactive = true
                )
            }
        }
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
