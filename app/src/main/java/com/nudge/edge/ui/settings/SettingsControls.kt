package com.nudge.edge.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nudge.edge.BuildConfig
import com.nudge.edge.data.GitHubUpdateManager
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.UpdateDownloadState
import com.nudge.edge.model.EdgePopStyle
import com.nudge.edge.model.EdgeSide
import com.nudge.edge.model.HudTriggerMode
import com.nudge.edge.model.LivePreviewController
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PreviewTarget
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.service.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

fun openUrlInDefaultBrowser(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = context.packageManager
        val resolveInfo = pm.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo != null && resolveInfo.activityInfo != null && resolveInfo.activityInfo.packageName != "android") {
            browserIntent.setPackage(resolveInfo.activityInfo.packageName)
        }
        context.startActivity(browserIntent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
        }
    }
}

fun openEmailClient(context: Context, email: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:$email")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Contact Email", email)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Email copied: $email", Toast.LENGTH_SHORT).show()
        }
    }
}

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
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = { repository.setIsEdgePopMasterEnabled(!settings.isEdgePopMasterEnabled) },
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
                        text = "Enable Edge Pop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Show edge animations on physical button presses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.isEdgePopMasterEnabled,
                    onCheckedChange = repository::setIsEdgePopMasterEnabled
                )
            }
        }

        EdgeSideSelectorCard(
            title = "Portrait Edge Side",
            subtitle = "Matches your hardware button side",
            selectedSide = settings.popEdgeSide,
            onSideSelected = repository::setPopEdgeSide
        )

        EdgePopStyleSelectorCard(
            settings = settings,
            repository = repository
        )

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InlineStepperSlider(
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
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                InlineStepperSlider(
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
                    }
                )

                InlineStepperSlider(
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
                    }
                )

                InlineStepperSlider(
                    title = "Volume Pop Width",
                    valueLabel = "${settings.popWidthDp.roundToInt()} dp",
                    value = settings.popWidthDp,
                    valueRange = NudgeSettings.MIN_POP_WIDTH_DP..NudgeSettings.MAX_POP_WIDTH_DP,
                    onValueChange = { repository.setPopWidthDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setPopWidthDp(settings.popWidthDp - 1f) },
                    onStepUp = { repository.setPopWidthDp(settings.popWidthDp + 1f) }
                )

                InlineStepperSlider(
                    title = "Volume Pop Height",
                    valueLabel = "${settings.popHeightDp.roundToInt()} dp",
                    value = settings.popHeightDp,
                    valueRange = NudgeSettings.MIN_POP_HEIGHT_DP..NudgeSettings.MAX_POP_HEIGHT_DP,
                    onValueChange = { repository.setPopHeightDp(it.roundToInt().toFloat()) },
                    onStepDown = { repository.setPopHeightDp(settings.popHeightDp - 1f) },
                    onStepUp = { repository.setPopHeightDp(settings.popHeightDp + 1f) }
                )
            }
        }

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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "HUD Edge Side",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        EdgeSideSegmentedButtons(
                            selectedSide = settings.effectiveHudEdgeSide,
                            onSideSelected = { side ->
                                repository.setHudEdgeSide(side)
                                repository.setHudSideSynced(false)
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Slider Style",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val attachedSelected = !settings.floatingMode
                                Surface(
                                    onClick = { repository.setFloatingMode(false) },
                                    shape = CircleShape,
                                    color = if (attachedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Attached",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (attachedSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (attachedSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                val floatingSelected = settings.floatingMode
                                Surface(
                                    onClick = { repository.setFloatingMode(true) },
                                    shape = CircleShape,
                                    color = if (floatingSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Floating",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (floatingSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (floatingSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Thin Floating HUD on Multi-Press or Hold",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Activates when clicking volume buttons 2+ times or holding volume buttons. Touching or dragging expands it back to full size.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.enableThinVolumeHud,
                                onCheckedChange = repository::setEnableThinVolumeHud
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.enableThinVolumeHud,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Main Slider in Fullscreen & Landscape",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Always show thin HUD first in fullscreen or landscape mode",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = settings.thinHudOnLandscapeFullscreen,
                                        onCheckedChange = repository::setThinHudOnLandscapeFullscreen
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                InlineStepperSlider(
                                    title = "Edge Inset",
                                    valueLabel = "${settings.thinHudEdgeInsetDp.roundToInt()} dp",
                                    value = settings.thinHudEdgeInsetDp,
                                    valueRange = 0f..48f,
                                    onValueChange = { repository.setThinHudEdgeInsetDp(it.roundToInt().toFloat()) },
                                    onStepDown = { repository.setThinHudEdgeInsetDp(settings.thinHudEdgeInsetDp - 1f) },
                                    onStepUp = { repository.setThinHudEdgeInsetDp(settings.thinHudEdgeInsetDp + 1f) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Volume Counter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Show volume level inside the slider",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.showVolumeCounter,
                                onCheckedChange = repository::setShowVolumeCounter
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Individual App Volume Control",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Control volume of currently playing apps individually (Requires Shizuku)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = settings.isPerAppVolumeEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                            runCatching { Shizuku.requestPermission(1001) }
                                        }
                                    }
                                    repository.setIsPerAppVolumeEnabled(enabled)
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.isPerAppVolumeEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Show Thin App Volume Slider",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Display app volume slider alongside thin HUD",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = settings.showThinPerAppVolumeHud,
                                        onCheckedChange = repository::setShowThinPerAppVolumeHud
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Text(
                                    text = "Show App Volume In",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isAlongside = settings.perAppVolumeDisplayMode == "ALONGSIDE_SLIDER"
                                    Surface(
                                        onClick = { repository.setPerAppVolumeDisplayMode("ALONGSIDE_SLIDER") },
                                        shape = CircleShape,
                                        color = if (isAlongside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (isAlongside) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                                            Text(
                                                text = "Alongside Slider",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isAlongside) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = { repository.setPerAppVolumeDisplayMode("MIXER_ONLY") },
                                        shape = CircleShape,
                                        color = if (!isAlongside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (!isAlongside) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).height(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                                            Text(
                                                text = "Audio Mixer Only",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (!isAlongside) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        InlineStepperSlider(
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
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        InlineStepperSlider(
                            title = "Edge Inset",
                            valueLabel = "${settings.hudEdgeInsetDp.roundToInt()} dp",
                            value = settings.hudEdgeInsetDp,
                            valueRange = NudgeSettings.MIN_HUD_EDGE_INSET_DP..NudgeSettings.MAX_HUD_EDGE_INSET_DP,
                            onValueChange = { repository.setHudEdgeInsetDp(it.roundToInt().toFloat()) },
                            onStepDown = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp - 1f) },
                            onStepUp = { repository.setHudEdgeInsetDp(settings.hudEdgeInsetDp + 1f) }
                        )

                        InlineStepperSlider(
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
                            }
                        )

                        InlineStepperSlider(
                            title = "HUD Height",
                            valueLabel = "${settings.hudHeightDp.roundToInt()} dp",
                            value = settings.hudHeightDp,
                            valueRange = NudgeSettings.MIN_HUD_HEIGHT_DP..NudgeSettings.MAX_HUD_HEIGHT_DP,
                            onValueChange = { repository.setHudHeightDp(it.roundToInt().toFloat()) },
                            onStepDown = { repository.setHudHeightDp(settings.hudHeightDp - 1f) },
                            onStepUp = { repository.setHudHeightDp(settings.hudHeightDp + 1f) }
                        )

                        InlineStepperSlider(
                            title = "HUD Pill Width",
                            valueLabel = "${settings.hudWidthDp.roundToInt()} dp",
                            value = settings.hudWidthDp,
                            valueRange = NudgeSettings.MIN_HUD_WIDTH_DP..NudgeSettings.MAX_HUD_WIDTH_DP,
                            onValueChange = { repository.setHudWidthDp(it.roundToInt().toFloat()) },
                            onStepDown = { repository.setHudWidthDp(settings.hudWidthDp - 1f) },
                            onStepUp = { repository.setHudWidthDp(settings.hudWidthDp + 1f) }
                        )

                        val maxCornerDp = (settings.hudWidthDp * 0.5f).coerceAtLeast(1f)
                        val effectiveCornerDp = settings.hudCornerRadiusDp.coerceIn(
                            NudgeSettings.MIN_HUD_CORNER_RADIUS_DP,
                            maxCornerDp
                        )
                        InlineStepperSlider(
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
                            }
                        )

                        InlineStepperSlider(
                            title = "Pill Transparency",
                            valueLabel = "${settings.volumeSliderPillTransparency}%",
                            value = settings.volumeSliderPillTransparency.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { repository.setVolumeSliderPillTransparency(it.roundToInt()) },
                            onStepDown = { repository.setVolumeSliderPillTransparency((settings.volumeSliderPillTransparency - 5).coerceAtLeast(0)) },
                            onStepUp = { repository.setVolumeSliderPillTransparency((settings.volumeSliderPillTransparency + 5).coerceAtMost(100)) }
                        )

                        val blurFraction = settings.volumeSliderBlur
                        val blurReadout = if (blurFraction <= 0.001f) "Off" else "${(blurFraction * 100).roundToInt()}%"
                        InlineStepperSlider(
                            title = "Blur",
                            valueLabel = blurReadout,
                            value = blurFraction * 100f,
                            valueRange = 0f..100f,
                            onValueChange = { repository.setVolumeSliderBlur((it / 100f).coerceIn(0f, 1f)) },
                            onStepDown = { repository.setVolumeSliderBlur((blurFraction - 0.05f).coerceIn(0f, 1f)) },
                            onStepUp = { repository.setVolumeSliderBlur((blurFraction + 0.05f).coerceIn(0f, 1f)) }
                        )

                        val context = LocalContext.current
                        val isBlurActive = com.nudge.edge.service.OverlayBlurController.isRuntimeBlurActive(context)
                        if (blurFraction > 0.001f && !isBlurActive) {
                            Text(
                                text = "Window blur is unavailable or paused by Battery Saver — using frosted fallback",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }

                TriggerModeSelectorCard(
                    selectedMode = settings.hudTriggerMode,
                    onModeSelected = repository::setHudTriggerMode
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

        var localX by remember(settings.audioMixerXPercent) { mutableFloatStateOf(settings.audioMixerXPercent) }
        var localY by remember(settings.audioMixerYPercent) { mutableFloatStateOf(settings.audioMixerYPercent) }
        var localScale by remember(settings.audioMixerScalePercent) { mutableFloatStateOf(settings.audioMixerScalePercent.toFloat()) }

        AnimatedVisibility(visible = settings.enableVolumeSliderHud) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Audio Mixer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configure volume mixer overlay appearance and layout",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val attached = settings.isAudioMixerAttachedToSlider
                    Surface(
                        onClick = {
                            repository.setIsAudioMixerAttachedToSlider(true)
                            repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                        },
                        shape = CircleShape,
                        color = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Attach to Slider",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (attached) FontWeight.Bold else FontWeight.Normal,
                            color = if (attached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            repository.setIsAudioMixerAttachedToSlider(false)
                            repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                        },
                        shape = CircleShape,
                        color = if (!attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Floating",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (!attached) FontWeight.Bold else FontWeight.Normal,
                            color = if (!attached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = !settings.isAudioMixerAttachedToSlider) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InlineStepperSlider(
                            title = "Horizontal Position (X)",
                            valueLabel = "${localX.roundToInt()}%",
                            value = localX,
                            valueRange = 0f..100f,
                            onValueChange = {
                                localX = it
                                repository.setAudioMixerXPercent(it, triggerPreview = false)
                            },
                            onStepDown = {
                                val next = (settings.audioMixerXPercent - 1f).coerceIn(0f, 100f)
                                localX = next
                                repository.setAudioMixerXPercent(next, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(1500L)
                            },
                            onStepUp = {
                                val next = (settings.audioMixerXPercent + 1f).coerceIn(0f, 100f)
                                localX = next
                                repository.setAudioMixerXPercent(next, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(1500L)
                            },
                            onValueChangeFinished = {
                                repository.setAudioMixerXPercent(localX, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2500L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(2500L)
                            }
                        )
                        InlineStepperSlider(
                            title = "Vertical Position (Y)",
                            valueLabel = "${localY.roundToInt()}%",
                            value = localY,
                            valueRange = 0f..100f,
                            onValueChange = {
                                localY = it
                                repository.setAudioMixerYPercent(it, triggerPreview = false)
                            },
                            onStepDown = {
                                val next = (settings.audioMixerYPercent - 1f).coerceIn(0f, 100f)
                                localY = next
                                repository.setAudioMixerYPercent(next, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(1500L)
                            },
                            onStepUp = {
                                val next = (settings.audioMixerYPercent + 1f).coerceIn(0f, 100f)
                                localY = next
                                repository.setAudioMixerYPercent(next, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(1500L)
                            },
                            onValueChangeFinished = {
                                repository.setAudioMixerYPercent(localY, triggerPreview = false)
                                repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2500L)
                                LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                                LivePreviewController.onSliderRelease(2500L)
                            }
                        )
                    }
                }

                InlineStepperSlider(
                    title = "Mixer Scale",
                    valueLabel = "${localScale.roundToInt()}%",
                    value = localScale,
                    valueRange = 50f..150f,
                    onValueChange = { percent ->
                        localScale = percent
                        val snapped = percent.roundToInt()
                        repository.setAudioMixerScalePercent(snapped, triggerPreview = false)
                    },
                    onStepDown = {
                        val next = (settings.audioMixerScalePercent - 1).coerceIn(50, 150)
                        localScale = next.toFloat()
                        repository.setAudioMixerScalePercent(next, triggerPreview = false)
                        repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                        LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                        LivePreviewController.onSliderRelease(1500L)
                    },
                    onStepUp = {
                        val next = (settings.audioMixerScalePercent + 1).coerceIn(50, 150)
                        localScale = next.toFloat()
                        repository.setAudioMixerScalePercent(next, triggerPreview = false)
                        repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2000L)
                        LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                        LivePreviewController.onSliderRelease(1500L)
                    },
                    onValueChangeFinished = {
                        val snapped = localScale.roundToInt()
                        repository.setAudioMixerScalePercent(snapped, triggerPreview = false)
                        repository.requestLivePreview(PreviewTarget.AUDIO_MIXER, durationMs = 2500L)
                        LivePreviewController.onSliderDrag(PreviewTarget.AUDIO_MIXER)
                        LivePreviewController.onSliderRelease(2500L)
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always Show Call Slider",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Show voice call volume channel even when not currently in a call",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.isAlwaysShowCallSliderInMixer,
                        onCheckedChange = { checked ->
                            repository.setIsAlwaysShowCallSliderInMixer(checked)
                        }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun SystemTab(
    settings: NudgeSettings,
    portraitAxisDp: Float = 0f,
    serviceActive: Boolean,
    repository: SettingsRepository,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        InstalledAppsManager.preload(context.applicationContext)
    }
    val coroutineScope = rememberCoroutineScope()
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    val checkBatteryOpt: () -> Unit = {
        isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        coroutineScope.launch {
            repeat(10) {
                checkBatteryOpt()
                if (isIgnoringBatteryOptimizations) return@launch
                delay(150L)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkBatteryOpt()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (BuildConfig.ENABLE_UPDATER) {
            val updateManager = remember {
                GitHubUpdateManager.getInstance(context, repository, coroutineScope)
            }
            val liveReleaseInfo by updateManager.releaseInfo.collectAsState()
            val downloadState by updateManager.downloadState.collectAsState()

            val displayVersion = liveReleaseInfo?.versionTag
                ?: settings.cachedReleaseVersionTag.ifBlank { "v${BuildConfig.VERSION_NAME}" }
            val isUpdateAvailable = liveReleaseInfo?.isUpdateAvailable ?: false

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = displayVersion,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isUpdateAvailable) {
                                    "Update Available: ${liveReleaseInfo?.releaseTitle ?: ""}"
                                } else {
                                    "Latest version installed"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isUpdateAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isUpdateAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isUpdateAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = if (isUpdateAvailable) "Update Available" else "Up to Date",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Check Frequency",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val frequencies = listOf(
                            "MANUAL" to "Manual Only",
                            "ON_START" to "App Launch",
                            "EVERY_6H" to "Every 6 Hours",
                            "EVERY_12H" to "Every 12 Hours",
                            "DAILY" to "Daily",
                            "WEEKLY" to "Weekly"
                        )
                        val rows = frequencies.chunked(3)
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (freqKey, label) ->
                                    val selected = settings.updateCheckFrequency == freqKey
                                    Surface(
                                        onClick = { repository.setUpdateCheckFrequency(freqKey) },
                                        shape = CircleShape,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val changelogText = liveReleaseInfo?.changelogBody
                        ?: settings.cachedReleaseChangelog
                    if (changelogText.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Changelog",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            ChangelogView(changelogText = changelogText)
                        }
                    }

                    when (val state = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = state.progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val percent = (state.progress * 100f).roundToInt()
                                    val downloadedMb = state.downloadedBytes / 1_048_576f
                                    val totalMb = state.totalBytes / 1_048_576f
                                    Text(
                                        text = "$percent% • ${"%.1f".format(downloadedMb)} / ${"%.1f".format(totalMb)} MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { updateManager.cancelDownload() },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text("Cancel", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is UpdateDownloadState.ReadyToInstall -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { updateManager.checkForUpdates(isManual = true) },
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Check Now", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { updateManager.promptInstallApk(state.apkFile) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Install Update", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {
                            if (state is UpdateDownloadState.Error) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            val isChecking = state is UpdateDownloadState.Checking
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { updateManager.checkForUpdates(isManual = true) },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape,
                                    enabled = !isChecking
                                ) {
                                    Text(
                                        text = if (isChecking) "Checking..." else "Check Now",
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isUpdateAvailable && liveReleaseInfo != null) {
                                    Button(
                                        onClick = {
                                            liveReleaseInfo?.let { updateManager.downloadAndInstallUpdate(it) }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.weight(1f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "Download Update",
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var isCloakDialogOpen by remember { mutableStateOf(false) }
        val shizukuState by ShizukuManager.shizukuState.collectAsState()

        if (isCloakDialogOpen) {
            ManageCloakedAppsDialog(
                cloakedPackages = settings.cloakedPackages,
                onAddPackage = repository::addCloakedPackage,
                onRemovePackage = repository::removeCloakedPackage,
                onDismiss = { isCloakDialogOpen = false }
            )
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = if (serviceActive) {
                                "Manage Settings"
                            } else {
                                "Grant Manually"
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            ShizukuManager.grantAccessibilityOnlyViaShizuku(context)
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        enabled = !serviceActive && shizukuState.isRunning && shizukuState.isPermissionGranted
                    ) {
                        Text(
                            text = "Auto Grant",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isIgnoringBatteryOptimizations) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isIgnoringBatteryOptimizations) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIgnoringBatteryOptimizations) {
                                "Battery Optimization Disabled"
                            } else {
                                "Battery Optimization"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isIgnoringBatteryOptimizations) {
                                "Unrestricted background execution granted"
                            } else {
                                "Allow background service to remain alive"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching {
                                batteryLauncher.launch(intent)
                            }.onFailure {
                                runCatching {
                                    batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        enabled = !isIgnoringBatteryOptimizations
                    ) {
                        Text(
                            text = if (isIgnoringBatteryOptimizations) {
                                "Disabled"
                            } else {
                                "Grant Manually"
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            ShizukuManager.grantBatteryOptimizationOnlyViaShizuku(context) {
                                checkBatteryOpt()
                            }
                            coroutineScope.launch {
                                repeat(10) {
                                    delay(200L)
                                    checkBatteryOpt()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        enabled = !isIgnoringBatteryOptimizations && shizukuState.isRunning && shizukuState.isPermissionGranted
                    ) {
                        Text(
                            text = "Auto Grant",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Shizuku Integration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (shizukuState.isRunning && shizukuState.isPermissionGranted) {
                                "Connected & Active"
                            } else if (shizukuState.isRunning) {
                                "Permission Required"
                            } else {
                                "Not Running"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuState.isRunning && shizukuState.isPermissionGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (!shizukuState.isRunning) {
                    Button(
                        onClick = { ShizukuManager.requestPermission(context) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Open Shizuku", fontWeight = FontWeight.SemiBold)
                    }
                } else if (!shizukuState.isPermissionGranted) {
                    Button(
                        onClick = { runCatching { Shizuku.requestPermission(1001) } },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Grant Shizuku Permission", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (shizukuState.isRunning && shizukuState.isPermissionGranted) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-enable all permissions",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant system permissions automatically via Shizuku",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                ShizukuManager.grantAllPermissionsViaShizuku(context) {
                                    checkBatteryOpt()
                                }
                                coroutineScope.launch {
                                    repeat(10) {
                                        delay(200L)
                                        checkBatteryOpt()
                                    }
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text("Grant All")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloaked Apps",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings.cloakedPackages.isEmpty()) {
                                    "No apps cloaked"
                                } else {
                                    "${settings.cloakedPackages.size} apps cloaked"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { isCloakDialogOpen = true },
                            shape = CircleShape
                        ) {
                            Text("Manage")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Keep-Alive via Shizuku",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically restores accessibility service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.isAutoKeepAliveViaShizuku,
                            onCheckedChange = repository::setIsAutoKeepAliveViaShizuku
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permissions Watchdog",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Periodic health check via Shizuku binder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = settings.isShizukuWatchdogEnabled,
                            onCheckedChange = repository::setIsShizukuWatchdogEnabled
                        )
                    }
                }
            }
        }

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

                AnimatedVisibility(
                    visible = settings.hapticsEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edge Pop",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.edgePopHapticsEnabled,
                                onCheckedChange = repository::setEdgePopHapticsEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Slider HUD",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.volumeSliderHapticsEnabled,
                                onCheckedChange = repository::setVolumeSliderHapticsEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Switch Modes via Sound Icon",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.soundIconModeSwitchHapticsEnabled,
                                onCheckedChange = repository::setSoundIconModeSwitchHapticsEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DND / Silent Panel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.dndPanelHapticsEnabled,
                                onCheckedChange = repository::setDndPanelHapticsEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Actions Panel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.actionsPanelHapticsEnabled,
                                onCheckedChange = repository::setActionsPanelHapticsEnabled
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nudge",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = settings.nudgeHapticsEnabled,
                                onCheckedChange = repository::setNudgeHapticsEnabled
                            )
                        }
                    }
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
                            text = if (settings.lightMode) "Light theme active" else "Dark theme active",
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "About Nudge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} • Edge Gestures & Volume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            openUrlInDefaultBrowser(context, "https://github.com/DarkAspect1/Nudge-Edge/issues")
                        },
                        modifier = Modifier.weight(1.35f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Report Issue",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            openEmailClient(context, "whenwilluplay1@gmail.com")
                        },
                        modifier = Modifier.weight(0.95f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Contact",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        openUrlInDefaultBrowser(context, "https://github.com/DarkAspect1/Nudge-Edge/releases")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Releases on GitHub",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?
)

object InstalledAppsManager {
    private val _apps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val apps: StateFlow<List<InstalledAppItem>> = _apps.asStateFlow()
    private val isLoading = AtomicBoolean(false)
    private val isLoaded = AtomicBoolean(false)

    fun preload(context: Context) {
        if (isLoaded.get() || isLoading.getAndSet(true)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = context.packageManager
                val launchIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolveList = pm.queryIntentActivities(launchIntent, 0)
                val appList = resolveList.mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    InstalledAppItem(pkg, label, icon)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
                _apps.value = appList
                isLoaded.set(true)
            } catch (_: Exception) {
            } finally {
                isLoading.set(false)
            }
        }
    }
}

@Composable
fun ManageCloakedAppsDialog(
    cloakedPackages: Set<String>,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        InstalledAppsManager.preload(context.applicationContext)
    }
    val installedApps by InstalledAppsManager.apps.collectAsState()
    var newPkg by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, searchQuery, cloakedPackages) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isBlank()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
        list.sortedWith(
            compareByDescending<InstalledAppItem> { cloakedPackages.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }

    val knownPackageNames = remember(installedApps) { installedApps.map { it.packageName }.toSet() }
    val customCloaked = remember(cloakedPackages, knownPackageNames) {
        cloakedPackages.filter { !knownPackageNames.contains(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloaked Apps", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it },
                        label = { Text("Package name") },
                        placeholder = { Text("e.g. com.banking.app") },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val clean = newPkg.trim()
                            if (clean.isNotBlank()) {
                                onAddPackage(clean)
                                newPkg = ""
                            }
                        },
                        shape = CircleShape
                    ) {
                        Text("Add")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                )

                if (installedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (customCloaked.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Custom Packages",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(customCloaked, key = { "custom_$it" }) { pkg ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { onRemovePackage(pkg) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Installed Apps (${filteredApps.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }

                        items(filteredApps, key = { it.packageName }) { app ->
                            val isCloaked = cloakedPackages.contains(app.packageName)
                            Surface(
                                onClick = {
                                    if (isCloaked) onRemovePackage(app.packageName) else onAddPackage(app.packageName)
                                },
                                shape = CircleShape,
                                color = if (isCloaked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val bitmap = remember(app.icon) {
                                        runCatching { app.icon?.toBitmap(64, 64)?.asImageBitmap() }.getOrNull()
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = app.label,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCloaked) MaterialTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = 2.dp,
                                                color = if (isCloaked) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCloaked) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun EdgePopStyleSelectorCard(
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
            Column {
                Text(
                    text = "Edge Pop Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Choose between classic rounded dome or flat-top bezel extrusion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EdgePopStyle.entries.forEach { style ->
                    SelectablePillButton(
                        label = style.displayName,
                        selected = settings.edgePopStyle == style,
                        onClick = { repository.setEdgePopStyle(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (settings.edgePopStyle == EdgePopStyle.FLAT_TOP) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "White Under-Glow",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Subtle white glow underneath Flat Top edges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.edgePopFlatTopGlowEnabled,
                        onCheckedChange = repository::setEdgePopFlatTopGlowEnabled
                    )
                }
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
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
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
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
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
    onStepUp: () -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
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
                onValueChangeFinished = onValueChangeFinished,
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

@Composable
private fun ChangelogView(
    changelogText: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val annotatedChangelog = remember(changelogText, colors) {
        buildAnnotatedChangelog(changelogText, colors)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!expanded) {
                Text(
                    text = annotatedChangelog,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "See more",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            } else {
                Text(
                    text = annotatedChangelog,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { expanded = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Collapse",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedChangelog(markdown: String, colors: ColorScheme): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.lines()
        val codeBg = colors.surfaceContainerHighest
        val codeColor = colors.primary

        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.startsWith("### ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.primary
                        )
                    ) {
                        appendInlineMarkdown(trimmed.substring(4), colors, codeBg, codeColor)
                    }
                }
                trimmed.startsWith("## ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.primary
                        )
                    ) {
                        appendInlineMarkdown(trimmed.substring(3), colors, codeBg, codeColor)
                    }
                }
                trimmed.startsWith("# ") -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.primary
                        )
                    ) {
                        appendInlineMarkdown(trimmed.substring(2), colors, codeBg, codeColor)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletPrefix = if (rawLine.startsWith("  ") || rawLine.startsWith("\t")) "    • " else "• "
                    append(bulletPrefix)
                    appendInlineMarkdown(trimmed.substring(2), colors, codeBg, codeColor)
                }
                else -> {
                    appendInlineMarkdown(rawLine, colors, codeBg, codeColor)
                }
            }
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    colors: ColorScheme,
    codeBg: Color,
    codeColor: Color
) {
    var i = 0
    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeColor
                    )
                ) {
                    append(" " + text.substring(i + 1, end) + " ")
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
