package com.nudge.edge.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.HudTriggerMode
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.ScreenEdge
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PopSetupTab(
    settings: NudgeSettings,
    portraitAxisDp: Float,
    repository: SettingsRepository
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EdgeSideSelectorCard(
            title = "Portrait Edge Side",
            subtitle = "Matches your hardware button side",
            selectedSide = settings.popEdgeSide,
            onSideSelected = repository::setPopEdgeSide
        )

        StepperSliderCard(
            title = "Volume Up Y-Axis",
            valueLabel = formatYAxisLabel(settings.volumeUpYFraction, portraitAxisDp),
            value = settings.volumeUpYFraction,
            valueRange = 0f..1f,
            onValueChange = repository::setVolumeUpYFraction,
            onStepDown = {
                repository.setVolumeUpYFraction(
                    NudgeSettings.stepFractionByDp(
                        settings.volumeUpYFraction,
                        -1f,
                        portraitAxisDp
                    )
                )
            },
            onStepUp = {
                repository.setVolumeUpYFraction(
                    NudgeSettings.stepFractionByDp(
                        settings.volumeUpYFraction,
                        1f,
                        portraitAxisDp
                    )
                )
            },
            stepDescription = "1 dp step"
        )

        StepperSliderCard(
            title = "Volume Down Y-Axis",
            valueLabel = formatYAxisLabel(settings.volumeDownYFraction, portraitAxisDp),
            value = settings.volumeDownYFraction,
            valueRange = 0f..1f,
            onValueChange = repository::setVolumeDownYFraction,
            onStepDown = {
                repository.setVolumeDownYFraction(
                    NudgeSettings.stepFractionByDp(
                        settings.volumeDownYFraction,
                        -1f,
                        portraitAxisDp
                    )
                )
            },
            onStepUp = {
                repository.setVolumeDownYFraction(
                    NudgeSettings.stepFractionByDp(
                        settings.volumeDownYFraction,
                        1f,
                        portraitAxisDp
                    )
                )
            },
            stepDescription = "1 dp step"
        )

        StepperSliderCard(
            title = "Volume Pop Width",
            valueLabel = "${settings.popWidthDp.roundToInt()} dp",
            value = settings.popWidthDp,
            valueRange = NudgeSettings.MIN_POP_WIDTH_DP..NudgeSettings.MAX_POP_WIDTH_DP,
            onValueChange = { repository.setPopWidthDp(it.roundToInt().toFloat()) },
            onStepDown = { repository.setPopWidthDp(settings.popWidthDp - 1f) },
            onStepUp = { repository.setPopWidthDp(settings.popWidthDp + 1f) },
            stepDescription = "1 dp step"
        )

        StepperSliderCard(
            title = "Volume Pop Height",
            valueLabel = "${settings.popHeightDp.roundToInt()} dp",
            value = settings.popHeightDp,
            valueRange = NudgeSettings.MIN_POP_HEIGHT_DP..NudgeSettings.MAX_POP_HEIGHT_DP,
            onValueChange = { repository.setPopHeightDp(it.roundToInt().toFloat()) },
            onStepDown = { repository.setPopHeightDp(settings.popHeightDp - 1f) },
            onStepUp = { repository.setPopHeightDp(settings.popHeightDp + 1f) },
            stepDescription = "1 dp step"
        )

        StepperSliderCard(
            title = "Edge Pop Timeout",
            valueLabel = formatTimeoutLabel(settings.popAutoHideMs),
            value = settings.popAutoHideMs.toFloat(),
            valueRange = NudgeSettings.MIN_POP_AUTO_HIDE_MS.toFloat()..NudgeSettings.MAX_POP_AUTO_HIDE_MS.toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw / NudgeSettings.POP_AUTO_HIDE_STEP_MS.toFloat()).roundToInt() *
                    NudgeSettings.POP_AUTO_HIDE_STEP_MS)
                repository.setPopAutoHideMs(snapped)
            },
            onStepDown = {
                repository.setPopAutoHideMs(
                    settings.popAutoHideMs - NudgeSettings.POP_AUTO_HIDE_STEP_MS
                )
            },
            onStepUp = {
                repository.setPopAutoHideMs(
                    settings.popAutoHideMs + NudgeSettings.POP_AUTO_HIDE_STEP_MS
                )
            },
            stepDescription = "50 ms step"
        )

        PowerButtonPopCard(
            settings = settings,
            portraitAxisDp = portraitAxisDp,
            repository = repository
        )

        OrientationSettingsCard(
            settings = settings,
            repository = repository
        )
    }
}

@Composable
private fun PowerButtonPopCard(
    settings: NudgeSettings,
    portraitAxisDp: Float,
    repository: SettingsRepository
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Power Button Pop",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { repository.setEnablePowerButtonPop(!settings.enablePowerButtonPop) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Power Button Pop",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Triggers pop on power press or wake",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = settings.enablePowerButtonPop,
                        onCheckedChange = repository::setEnablePowerButtonPop
                    )
                }
            }

            AnimatedVisibility(
                visible = settings.enablePowerButtonPop,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    InlineStepperSlider(
                        title = "Power Button Y-Axis",
                        valueLabel = formatYAxisLabel(settings.powerButtonYFraction, portraitAxisDp),
                        value = settings.powerButtonYFraction,
                        valueRange = 0f..1f,
                        onValueChange = repository::setPowerButtonYFraction,
                        onStepDown = {
                            repository.setPowerButtonYFraction(
                                NudgeSettings.stepFractionByDp(
                                    settings.powerButtonYFraction,
                                    -1f,
                                    portraitAxisDp
                                )
                            )
                        },
                        onStepUp = {
                            repository.setPowerButtonYFraction(
                                NudgeSettings.stepFractionByDp(
                                    settings.powerButtonYFraction,
                                    1f,
                                    portraitAxisDp
                                )
                            )
                        }
                    )

                    InlineStepperSlider(
                        title = "Power Pop Width",
                        valueLabel = "${settings.powerPopWidthDp.roundToInt()} dp",
                        value = settings.powerPopWidthDp,
                        valueRange = NudgeSettings.MIN_POP_WIDTH_DP..NudgeSettings.MAX_POP_WIDTH_DP,
                        onValueChange = {
                            repository.setPowerPopWidthDp(it.roundToInt().toFloat())
                        },
                        onStepDown = {
                            repository.setPowerPopWidthDp(settings.powerPopWidthDp - 1f)
                        },
                        onStepUp = {
                            repository.setPowerPopWidthDp(settings.powerPopWidthDp + 1f)
                        }
                    )

                    InlineStepperSlider(
                        title = "Power Pop Height",
                        valueLabel = "${settings.powerPopHeightDp.roundToInt()} dp",
                        value = settings.powerPopHeightDp,
                        valueRange = NudgeSettings.MIN_POP_HEIGHT_DP..NudgeSettings.MAX_POP_HEIGHT_DP,
                        onValueChange = {
                            repository.setPowerPopHeightDp(it.roundToInt().toFloat())
                        },
                        onStepDown = {
                            repository.setPowerPopHeightDp(settings.powerPopHeightDp - 1f)
                        },
                        onStepUp = {
                            repository.setPowerPopHeightDp(settings.powerPopHeightDp + 1f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrientationSettingsCard(
    settings: NudgeSettings,
    repository: SettingsRepository
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ScreenRotation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Orientation Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = {
                    repository.setEnableEdgePopInLandscape(!settings.enableEdgePopInLandscape)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Edge Pop in Landscape",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = settings.enableEdgePopInLandscape,
                        onCheckedChange = repository::setEnableEdgePopInLandscape
                    )
                }
            }
        }
    }
}

@Composable
fun SliderHudTab(
    settings: NudgeSettings,
    portraitAxisDp: Float,
    landscapeAxisDp: Float,
    musicMaxVolume: Int,
    repository: SettingsRepository
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            onClick = { repository.setEnableVolumeSliderHud(!settings.enableVolumeSliderHud) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Volume Slider HUD",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.enableVolumeSliderHud,
                    onCheckedChange = repository::setEnableVolumeSliderHud
                )
            }
        }

        AnimatedVisibility(
            visible = !settings.enableVolumeSliderHud,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                onClick = {
                    repository.setShowSystemPanelWhenHudOff(!settings.showSystemPanelWhenHudOff)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Standard System Volume Slider",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.showSystemPanelWhenHudOff,
                        onCheckedChange = repository::setShowSystemPanelWhenHudOff
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = settings.enableVolumeSliderHud,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HUD Edge Anchor",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.hudSideSynced) {
                                        "Synced (${settings.popEdgeSide.name.lowercase().replaceFirstChar { it.uppercase() }})"
                                    } else {
                                        "Independent edge anchor"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Switch(
                                    checked = settings.hudSideSynced,
                                    onCheckedChange = repository::setHudSideSynced
                                )
                            }
                        }
                        EdgeSideSegmentedButtons(
                            selectedSide = settings.effectiveHudEdgeSide,
                            onSideSelected = repository::setHudEdgeSide
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { repository.setFloatingMode(!settings.floatingMode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (settings.floatingMode) {
                                    "Now: Standalone floating pill"
                                } else {
                                    "Now: Edge-merged bezel notch"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.floatingMode,
                            onCheckedChange = repository::setFloatingMode
                        )
                    }
                }

                StepperSliderCard(
                    title = "Edge Inset",
                    valueLabel = "${settings.hudEdgeInsetDp.roundToInt()} dp",
                    value = settings.hudEdgeInsetDp,
                    valueRange = NudgeSettings.MIN_HUD_EDGE_INSET_DP..NudgeSettings.MAX_HUD_EDGE_INSET_DP,
                    onValueChange = { repository.setHudEdgeInsetDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp - 1f) },
                    onStepUp = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp + 1f) },
                    stepDescription = "1 dp step"
                )

                StepperSliderCard(
                    title = "HUD Y-Axis Position",
                    valueLabel = formatYAxisLabel(settings.hudYFraction, portraitAxisDp),
                    value = settings.hudYFraction,
                    valueRange = 0f..1f,
                    onValueChange = repository::setHudYFraction,
                    onStepDown = {
                        repository.setHudYFraction(
                            NudgeSettings.stepFractionByDp(
                                settings.hudYFraction,
                                -1f,
                                portraitAxisDp
                            )
                        )
                    },
                    onStepUp = {
                        repository.setHudYFraction(
                            NudgeSettings.stepFractionByDp(
                                settings.hudYFraction,
                                1f,
                                portraitAxisDp
                            )
                        )
                    },
                    stepDescription = "1 dp step"
                )

                StepperSliderCard(
                    title = "HUD Pill Height",
                    valueLabel = "${settings.hudHeightDp.roundToInt()} dp",
                    value = settings.hudHeightDp,
                    valueRange = NudgeSettings.MIN_HUD_HEIGHT_DP..NudgeSettings.MAX_HUD_HEIGHT_DP,
                    onValueChange = { repository.setHudHeightDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudHeightDp(settings.hudHeightDp - 1f) },
                    onStepUp = { repository.setHudHeightDp(settings.hudHeightDp + 1f) },
                    stepDescription = "1 dp step"
                )

                StepperSliderCard(
                    title = "HUD Pill Width",
                    valueLabel = "${settings.hudWidthDp.roundToInt()} dp",
                    value = settings.hudWidthDp,
                    valueRange = NudgeSettings.MIN_HUD_WIDTH_DP..NudgeSettings.MAX_HUD_WIDTH_DP,
                    onValueChange = { repository.setHudWidthDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setHudWidthDp(settings.hudWidthDp - 1f) },
                    onStepUp = { repository.setHudWidthDp(settings.hudWidthDp + 1f) },
                    stepDescription = "1 dp fixed width"
                )

                val maxCornerDp = (settings.hudWidthDp * 0.5f).coerceAtLeast(1f)
                val effectiveCornerDp = settings.hudCornerRadiusDp.coerceIn(
                    NudgeSettings.MIN_HUD_CORNER_RADIUS_DP,
                    maxCornerDp
                )
                StepperSliderCard(
                    title = "HUD Corner Radius",
                    valueLabel = "${effectiveCornerDp.roundToInt()} dp",
                    value = effectiveCornerDp,
                    valueRange = NudgeSettings.MIN_HUD_CORNER_RADIUS_DP..maxCornerDp,
                    onValueChange = { raw ->
                        val snapped = if (raw >= maxCornerDp - 0.35f) {
                            maxCornerDp
                        } else {
                            raw.roundToInt().toFloat().coerceIn(
                                NudgeSettings.MIN_HUD_CORNER_RADIUS_DP,
                                maxCornerDp
                            )
                        }
                        repository.setHudCornerRadiusDp(snapped)
                    },
                    onStepDown = {
                        repository.setHudCornerRadiusDp(
                            (effectiveCornerDp - 1f).coerceAtLeast(NudgeSettings.MIN_HUD_CORNER_RADIUS_DP)
                        )
                    },
                    onStepUp = {
                        repository.setHudCornerRadiusDp(minOf(effectiveCornerDp + 1f, maxCornerDp))
                    },
                    stepDescription = "0 to ${maxCornerDp.roundToInt()} dp (1 dp step)"
                )

                TriggerModeSelectorCard(
                    selectedMode = settings.hudTriggerMode,
                    onModeSelected = repository::setHudTriggerMode
                )

                StepperSliderCard(
                    title = "Auto-Hide Timeout",
                    valueLabel = formatTimeoutLabel(settings.hudAutoHideMs),
                    value = settings.hudAutoHideMs.toFloat(),
                    valueRange = NudgeSettings.MIN_AUTO_HIDE_MS.toFloat()..NudgeSettings.MAX_AUTO_HIDE_MS.toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / NudgeSettings.AUTO_HIDE_STEP_MS.toFloat()).roundToInt() *
                            NudgeSettings.AUTO_HIDE_STEP_MS)
                        repository.setHudAutoHideMs(snapped)
                    },
                    onStepDown = {
                        repository.setHudAutoHideMs(
                            settings.hudAutoHideMs - NudgeSettings.AUTO_HIDE_STEP_MS
                        )
                    },
                    onStepUp = {
                        repository.setHudAutoHideMs(
                            settings.hudAutoHideMs + NudgeSettings.AUTO_HIDE_STEP_MS
                        )
                    },
                    stepDescription = "50 ms step"
                )
            }
        }

        StepperSliderCard(
            title = "Volume Step",
            valueLabel = "${settings.volumeStep} ${if (settings.volumeStep == 1) "step" else "steps"}",
            value = settings.volumeStep.toFloat(),
            valueRange = NudgeSettings.MIN_VOLUME_STEP.toFloat()..NudgeSettings.MAX_VOLUME_STEP.toFloat(),
            onValueChange = { repository.setVolumeStep(it.roundToInt()) },
            onStepDown = { repository.setVolumeStep(settings.volumeStep - 1) },
            onStepUp = { repository.setVolumeStep(settings.volumeStep + 1) },
            stepDescription = null
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Detected Device Scale: 0 \u2013 $musicMaxVolume steps",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Landscape Volume Options",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (settings.enableVolumeSliderHud) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = {
                            repository.setEnableSliderHudInLandscape(!settings.enableSliderHudInLandscape)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Custom Slider",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.enableSliderHudInLandscape,
                                onCheckedChange = repository::setEnableSliderHudInLandscape
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.enableSliderHudInLandscape,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val landscapeIsVerticalEdge =
                            settings.landscapeHudEdgeSide == ScreenEdge.LEFT ||
                                settings.landscapeHudEdgeSide == ScreenEdge.RIGHT
                        val landscapePositionAxisDp = if (landscapeIsVerticalEdge) {
                            landscapeAxisDp
                        } else {
                            portraitAxisDp
                        }
                        val landscapePositionTitle = if (landscapeIsVerticalEdge) {
                            "Vertical Position"
                        } else {
                            "Horizontal Position"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Slider Edge",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    ScreenEdgeSegmentedButtons(
                                        selectedEdge = settings.landscapeHudEdgeSide,
                                        onEdgeSelected = repository::setLandscapeHudEdgeSide
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    InlineStepperSlider(
                                        title = landscapePositionTitle,
                                        valueLabel = formatYAxisLabel(
                                            settings.landscapeHudYFraction,
                                            landscapePositionAxisDp
                                        ),
                                        value = settings.landscapeHudYFraction,
                                        valueRange = 0f..1f,
                                        onValueChange = repository::setLandscapeHudYFraction,
                                        onStepDown = {
                                            repository.setLandscapeHudYFraction(
                                                NudgeSettings.stepFractionByDp(
                                                    settings.landscapeHudYFraction,
                                                    -1f,
                                                    landscapePositionAxisDp
                                                )
                                            )
                                        },
                                        onStepUp = {
                                            repository.setLandscapeHudYFraction(
                                                NudgeSettings.stepFractionByDp(
                                                    settings.landscapeHudYFraction,
                                                    1f,
                                                    landscapePositionAxisDp
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !settings.enableVolumeSliderHud || !settings.enableSliderHudInLandscape,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = {
                            repository.setShowSystemPanelInLandscape(!settings.showSystemPanelInLandscape)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show System Volume Slider",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = settings.showSystemPanelInLandscape,
                                onCheckedChange = repository::setShowSystemPanelInLandscape
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemTab(
    settings: NudgeSettings,
    serviceActive: Boolean,
    repository: SettingsRepository,
    onOpenAccessibilitySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (serviceActive) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessibilityNew,
                            contentDescription = null,
                            tint = if (serviceActive) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (serviceActive) {
                                "Accessibility Service Active"
                            } else {
                                "Enable Accessibility Service"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (serviceActive) {
                                "Hardware keys intercepted globally"
                            } else {
                                "Required for key & overlay access"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        text = if (serviceActive) {
                            "Manage Accessibility Settings"
                        } else {
                            "Open Accessibility Settings"
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = { repository.setLightMode(!settings.lightMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Light Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Light theme for app & mixer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.lightMode,
                    onCheckedChange = repository::setLightMode
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = { repository.setHapticsEnabled(!settings.hapticsEnabled) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Vibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Haptics on Button Press",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Haptic click on keys & gestures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = repository::setHapticsEnabled
                )
            }
        }
    }
}

@Composable
private fun EdgeSideSelectorCard(
    title: String,
    subtitle: String,
    selectedSide: EdgeSide,
    onSideSelected: (EdgeSide) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            EdgeSideSegmentedButtons(
                selectedSide = selectedSide,
                onSideSelected = onSideSelected
            )
        }
    }
}

@Composable
private fun EdgeSideSegmentedButtons(
    selectedSide: EdgeSide,
    onSideSelected: (EdgeSide) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EdgeSide.entries.forEach { side ->
            SelectablePillButton(
                label = if (side == EdgeSide.LEFT) "Left Edge" else "Right Edge",
                selected = selectedSide == side,
                onClick = { onSideSelected(side) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScreenEdgeSegmentedButtons(
    selectedEdge: ScreenEdge,
    onEdgeSelected: (ScreenEdge) -> Unit
) {
    val rows = listOf(
        listOf(ScreenEdge.LEFT to "Left", ScreenEdge.RIGHT to "Right"),
        listOf(ScreenEdge.TOP to "Top", ScreenEdge.BOTTOM to "Bottom")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowEdges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowEdges.forEach { (edge, label) ->
                    SelectablePillButton(
                        label = label,
                        selected = selectedEdge == edge,
                        onClick = { onEdgeSelected(edge) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectablePillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun TriggerModeSelectorCard(
    selectedMode: HudTriggerMode,
    onModeSelected: (HudTriggerMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HUD Trigger Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            TriggerOptionRow(
                selected = selectedMode == HudTriggerMode.SIMULTANEOUS,
                title = "Simultaneous with Edge Pop",
                description = "Show slider HUD immediately",
                onClick = { onModeSelected(HudTriggerMode.SIMULTANEOUS) }
            )
            TriggerOptionRow(
                selected = selectedMode == HudTriggerMode.SECOND_PRESS,
                title = "Second Press",
                description = "First press pops; second opens HUD",
                onClick = { onModeSelected(HudTriggerMode.SECOND_PRESS) }
            )
            TriggerOptionRow(
                selected = selectedMode == HudTriggerMode.SWIPE_FROM_POP,
                title = "Swipe Inward from Edge Pop",
                description = "Press for Edge Pop, swipe in for HUD",
                onClick = { onModeSelected(HudTriggerMode.SWIPE_FROM_POP) }
            )
        }
    }
}

@Composable
private fun TriggerOptionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InlineStepperSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onStepDown,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease $title",
                    modifier = Modifier.size(16.dp)
                )
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = onStepUp,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $title",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StepperSliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    stepDescription: String? = null
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!stepDescription.isNullOrEmpty()) {
                        Text(
                            text = stepDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onStepDown,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Decrease $title",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalIconButton(
                    onClick = onStepUp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Increase $title",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatYAxisLabel(fraction: Float, axisDp: Float): String {
    val percent = (fraction * 100f).roundToInt()
    val dpPos = (fraction * axisDp).roundToInt()
    return "$percent% · $dpPos dp"
}

private fun formatTimeoutLabel(timeoutMs: Long): String {
    return if (timeoutMs < 1000L) {
        "$timeoutMs ms"
    } else {
        String.format(Locale.US, "%.2f s", timeoutMs / 1000f)
    }
}
