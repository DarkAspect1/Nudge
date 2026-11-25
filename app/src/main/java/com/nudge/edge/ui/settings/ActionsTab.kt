package com.nudge.edge.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.model.BezelEdge
import com.nudge.edge.model.CustomMode
import com.nudge.edge.model.DndModeType
import com.nudge.edge.model.HardwareKeyTrigger
import com.nudge.edge.model.HardwareTrigger
import com.nudge.edge.model.LivePreviewController
import com.nudge.edge.model.NudgeIcons
import com.nudge.edge.model.NudgeMenuApp
import com.nudge.edge.model.NudgeSettings
import com.nudge.edge.model.PersistentNudge
import com.nudge.edge.model.PersistentNudgeAction
import com.nudge.edge.model.ScreenEdge
import com.nudge.edge.model.TriggerSource
import com.nudge.edge.service.NudgeAccessibilityService
import com.nudge.edge.service.NudgeVoiceInteractionService
import com.nudge.edge.service.ShizukuManager
import com.nudge.edge.service.ShizukuStatus
import com.nudge.edge.model.PreviewTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ModesTab(
    settings: NudgeSettings,
    repository: SettingsRepository,
    modifier: Modifier = Modifier
) = ActionsTab(settings, repository, modifier)

@Composable
fun ActionsTab(
    settings: NudgeSettings,
    repository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingMode by remember { mutableStateOf<CustomMode?>(null) }
    var isAddingNewMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        ModesPermissionsHeader(context = context, settings = settings, repository = repository)

        DndPanelCard(settings = settings, repository = repository)

        ActionsPanelCard(
            settings = settings,
            repository = repository,
            onAddMode = { isAddingNewMode = true },
            onEditMode = { editingMode = it },
            onDeleteMode = { repository.deleteCustomMode(it.id) },
            onMoveUp = { index -> repository.reorderCustomMode(index, index - 1) },
            onMoveDown = { index -> repository.reorderCustomMode(index, index + 1) }
        )

        PersistentNudgesCard(
            settings = settings,
            repository = repository
        )
    }

    if (isAddingNewMode) {
        EditCustomModeDialog(
            mode = null,
            onDismiss = { isAddingNewMode = false },
            onSave = { newMode ->
                repository.addCustomMode(newMode)
                isAddingNewMode = false
            }
        )
    }

    editingMode?.let { mode ->
        EditCustomModeDialog(
            mode = mode,
            onDismiss = { editingMode = null },
            onSave = { updated ->
                repository.updateCustomMode(updated)
                editingMode = null
            }
        )
    }
}

@Composable
private fun ModesPermissionsHeader(
    context: Context,
    settings: NudgeSettings,
    repository: SettingsRepository
) {
    val notifManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    var hasDndPermission by remember(context) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifManager.isNotificationPolicyAccessGranted
            } else true
        )
    }

    var hasPostNotifPermission by remember(context) {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    fun checkAssistantConfigured(ctx: Context): Boolean {
        val pkg = ctx.packageName
        val secureAssistant = runCatching {
            Settings.Secure.getString(ctx.contentResolver, "assistant")
        }.getOrNull().orEmpty()
        val secureVoice = runCatching {
            Settings.Secure.getString(ctx.contentResolver, "voice_interaction_service")
        }.getOrNull().orEmpty()
        val isServiceActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                VoiceInteractionService.isActiveService(
                    ctx,
                    ComponentName(ctx, NudgeVoiceInteractionService::class.java)
                )
            }.getOrDefault(false)
        } else false
        return isServiceActive || secureAssistant.contains(pkg) || secureVoice.contains(pkg)
    }

    var isAssistantConfigured by remember(context) {
        mutableStateOf(checkAssistantConfigured(context))
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPostNotifPermission = isGranted
    }

    val coroutineScope = rememberCoroutineScope()

    fun refreshPermissions() {
        hasDndPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notifManager.isNotificationPolicyAccessGranted
        } else true
        hasPostNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        val active = checkAssistantConfigured(context)
        isAssistantConfigured = active
        if (settings.powerAssistantConfigured != active) {
            repository.setPowerAssistantConfigured(active)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                coroutineScope.launch {
                    delay(150L)
                    refreshPermissions()
                    delay(300L)
                    refreshPermissions()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        val shizukuStatusForDnd by ShizukuManager.status.collectAsState()
        PermissionItemCard(
            title = "Do Not Disturb Access",
            isGranted = hasDndPermission,
            grantedText = "Granted",
            deniedText = "Denied",
            description = "Required to switch DND & Silent ringer modes without system interruption.",
            buttonText = if (hasDndPermission) "Manage in Settings" else "Grant Permission",
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            onAutoGrantClick = {
                ShizukuManager.grantDndAccessOnlyViaShizuku(context) {
                    coroutineScope.launch {
                        repeat(5) {
                            refreshPermissions()
                            delay(100L)
                        }
                    }
                }
            },
            extraContent = {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Use Shizuku for DND",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "When off, uses standard Android DND access; when on, toggles real system DND through Shizuku (recommended on Android 15+ and to avoid separate DND access permission).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useShizukuForDnd,
                        onCheckedChange = { checked ->
                            if (checked && shizukuStatusForDnd != ShizukuStatus.CONNECTED_AND_ACTIVE) {
                                if (shizukuStatusForDnd == ShizukuStatus.PERMISSION_REQUIRED) {
                                    ShizukuManager.requestPermission()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Shizuku is not running or authorized. Please start Shizuku first.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            repository.setUseShizukuForDnd(checked)
                        }
                    )
                }
                if (settings.useShizukuForDnd && shizukuStatusForDnd != ShizukuStatus.CONNECTED_AND_ACTIVE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Warning: Shizuku status is \"${shizukuStatusForDnd.displayName}\". Shizuku must be running and authorized to toggle DND.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        PermissionItemCard(
            title = "Notification Bridge",
            isGranted = hasPostNotifPermission,
            grantedText = "Granted",
            deniedText = "Denied",
            description = "Required for Automation / Routines silent notification bridge.",
            buttonText = if (hasPostNotifPermission) "Permission Granted" else "Grant Permission",
            onClick = {
                if (!hasPostNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }
            },
            onAutoGrantClick = {
                ShizukuManager.grantNotificationsOnlyViaShizuku(context) {
                    coroutineScope.launch {
                        repeat(5) {
                            refreshPermissions()
                            delay(100L)
                        }
                    }
                }
            }
        )

        PermissionItemCard(
            title = "Hold Power Button Trigger",
            isGranted = isAssistantConfigured,
            grantedText = "Configured",
            deniedText = "Not Configured",
            description = "Assign Nudge as Digital Assistant to trigger overlays on Long-Press Power.",
            buttonText = if (isAssistantConfigured) "Configure Assistant" else "Set Up Assistant",
            onClick = {
                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (!launched) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(fallbackIntent) }
                }
            },
            onAutoGrantClick = {
                ShizukuManager.grantPowerTriggerOnlyViaShizuku(context) {
                    repository.setPowerAssistantConfigured(true)
                    coroutineScope.launch {
                        repeat(5) {
                            refreshPermissions()
                            delay(100L)
                        }
                    }
                }
            },
            extraContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Use Shizuku for hold power button",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Captures long-press power via Shizuku if you prefer keeping Google Assistant or Gemini as your default digital assistant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.enableShizukuPowerHoldFallback,
                        onCheckedChange = {
                            repository.setEnableShizukuPowerHoldFallback(it)
                            if (it && ShizukuManager.hasShizukuPermission()) {
                                ShizukuManager.startInputEventMonitoring()
                            }
                        }
                    )
                }
            }
        )


    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    isGranted: Boolean,
    grantedText: String,
    deniedText: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    onAutoGrantClick: (() -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (isGranted) grantedText else deniedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted && onAutoGrantClick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = onAutoGrantClick,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Auto-Grant",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
private fun DndPanelCard(
    settings: NudgeSettings,
    repository: SettingsRepository
) {
    var isScanningCustomButton by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DND / Silent Panel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Two-state toggle for Silent/Ring or DND",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enableDndToggle,
                    onCheckedChange = repository::setEnableDndToggle
                )
            }

            AnimatedVisibility(
                visible = settings.enableDndToggle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Toggle Mode Type",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DndModeType.entries.forEach { mode ->
                                val selected = settings.dndModeType == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { repository.setDndModeType(mode) },
                                    shape = CircleShape,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    HardwareTriggerSelector(
                        title = "Hardware Trigger Button",
                        selectedTrigger = settings.dndTrigger,
                        onTriggerSelected = { trigger ->
                            repository.setDndTrigger(trigger)
                            if (trigger == HardwareTrigger.CUSTOM_BTN_SINGLE ||
                                trigger == HardwareTrigger.CUSTOM_BTN_DOUBLE ||
                                trigger == HardwareTrigger.CUSTOM_BTN_LONG
                            ) {
                                isScanningCustomButton = true
                            }
                        }
                    )

                    if (settings.dndTrigger == HardwareTrigger.BOTH_VOLUME_SHORT) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SliderRow(
                                title = "Simultaneous Press Timeout",
                                valueLabel = "${settings.simultaneousPressTimeoutMs} ms",
                                value = settings.simultaneousPressTimeoutMs.toFloat(),
                                valueRange = 1f..500f,
                                onValueChange = {
                                    val v = it.roundToInt().toLong()
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onStepDown = {
                                    val v = (settings.simultaneousPressTimeoutMs - 5L).coerceAtLeast(1L)
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onStepUp = {
                                    val v = (settings.simultaneousPressTimeoutMs + 5L).coerceAtMost(500L)
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onValueChangeFinished = {
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                                }
                            )
                            Text(
                                text = "Warning: A higher timeout prevents misfires when pressing both buttons, but delays single volume steps when all panels are closed.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (settings.dndTrigger == HardwareTrigger.DOUBLE_TAP_VOL_UP || settings.dndTrigger == HardwareTrigger.DOUBLE_TAP_VOL_DOWN) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SliderRow(
                                title = "Double-Press Timeout",
                                valueLabel = "${settings.dndDoubleTapTimeoutMs} ms",
                                value = settings.dndDoubleTapTimeoutMs.toFloat(),
                                valueRange = 1f..500f,
                                onValueChange = {
                                    val v = it.roundToInt().toLong()
                                    repository.setDndDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onStepDown = {
                                    val v = (settings.dndDoubleTapTimeoutMs - 10L).coerceAtLeast(1L)
                                    repository.setDndDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onStepUp = {
                                    val v = (settings.dndDoubleTapTimeoutMs + 10L).coerceAtMost(500L)
                                    repository.setDndDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                                },
                                onValueChangeFinished = {
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                                }
                            )
                            Text(
                                text = "Warning: The volume slider waits for this timeout before showing when double-press is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    SliderRow(
                        title = "Panel Timeout",
                        valueLabel = "${settings.dndTimeoutMs} ms",
                        value = settings.dndTimeoutMs.toFloat(),
                        valueRange = 500f..4000f,
                        onValueChange = {
                            val v = it.roundToInt().toLong()
                            repository.setDndTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.dndTimeoutMs - 100L).coerceAtLeast(500L)
                            repository.setDndTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.dndTimeoutMs + 100L).coerceAtMost(4000L)
                            repository.setDndTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = {
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                        }
                    )

                    SideSelectorRow(
                        title = "Portrait Side",
                        selectedEdge = settings.dndPortraitEdge,
                        availableEdges = listOf(BezelEdge.LEFT, BezelEdge.RIGHT),
                        onEdgeSelected = { edge ->
                            repository.setDndPortraitEdge(edge)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = false)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        }
                    )

                    SliderRow(
                        title = "Portrait Position Offset",
                        valueLabel = "${(settings.dndPortraitOffsetPercent * 100f).roundToInt()}%",
                        value = settings.dndPortraitOffsetPercent * 100f,
                        valueRange = 10f..90f,
                        onValueChange = { pct ->
                            val frac = (pct / 100f).coerceIn(0.10f, 0.90f)
                            repository.setDndPortraitOffset(frac)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = frac * 100f)
                            }
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.dndPortraitOffsetPercent - 0.01f).coerceIn(0.10f, 0.90f)
                            repository.setDndPortraitOffset(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = v * 100f)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.dndPortraitOffsetPercent + 0.01f).coerceIn(0.10f, 0.90f)
                            repository.setDndPortraitOffset(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = v * 100f)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setDndPortraitOffset((finalVal / 100f).coerceIn(0.10f, 0.90f))
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable in Landscape",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = settings.dndEnableLandscape,
                            onCheckedChange = repository::setDndEnableLandscape
                        )
                    }

                    if (settings.dndEnableLandscape) {
                        SideSelectorRow(
                            title = "Landscape Side",
                            selectedEdge = settings.dndLandscapeEdge,
                            availableEdges = BezelEdge.entries,
                            onEdgeSelected = { edge ->
                                repository.setDndLandscapeEdge(edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = true)
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.DND_PANEL, forceLandscape = true, durationMs = 1500L)
                            }
                        )

                        SliderRow(
                            title = "Landscape Position Offset",
                            valueLabel = "${(settings.dndLandscapeOffsetPercent * 100f).roundToInt()}%",
                            value = settings.dndLandscapeOffsetPercent * 100f,
                            valueRange = 10f..90f,
                            onValueChange = { pct ->
                                val frac = (pct / 100f).coerceIn(0.10f, 0.90f)
                                repository.setDndLandscapeOffset(frac)
                                LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = frac * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.DND_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = (settings.dndLandscapeOffsetPercent - 0.01f).coerceIn(0.10f, 0.90f)
                                repository.setDndLandscapeOffset(v)
                                LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.DND_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = (settings.dndLandscapeOffsetPercent + 0.01f).coerceIn(0.10f, 0.90f)
                                repository.setDndLandscapeOffset(v)
                                LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.DND_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onValueChangeFinished = { finalVal ->
                                repository.setDndLandscapeOffset((finalVal / 100f).coerceIn(0.10f, 0.90f))
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.DND_PANEL, forceLandscape = true, durationMs = 800L)
                            }
                        )
                    }

                    SliderRow(
                        title = "Pill Width",
                        valueLabel = "${settings.dndThicknessDp.roundToInt()} dp",
                        value = settings.dndThicknessDp,
                        valueRange = NudgeSettings.MIN_BEZEL_THICKNESS_DP..NudgeSettings.MAX_BEZEL_THICKNESS_DP,
                        onValueChange = { v ->
                            val snapped = v.roundToInt().toFloat()
                            repository.setDndThicknessDp(snapped)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideWidthDp = snapped)
                            }
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.dndThicknessDp - 1f).coerceIn(NudgeSettings.MIN_BEZEL_THICKNESS_DP, NudgeSettings.MAX_BEZEL_THICKNESS_DP)
                            repository.setDndThicknessDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideWidthDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.dndThicknessDp + 1f).coerceIn(NudgeSettings.MIN_BEZEL_THICKNESS_DP, NudgeSettings.MAX_BEZEL_THICKNESS_DP)
                            repository.setDndThicknessDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideWidthDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setDndThicknessDp(finalVal.roundToInt().toFloat())
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                        }
                    )

                    SliderRow(
                        title = "Pill Height",
                        valueLabel = "${settings.dndLengthDp.roundToInt()} dp",
                        value = settings.dndLengthDp,
                        valueRange = 40f..240f,
                        onValueChange = { v ->
                            val snapped = v.roundToInt().toFloat()
                            repository.setDndLengthDp(snapped)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideHeightDp = snapped)
                            }
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.dndLengthDp - 1f).coerceIn(40f, 240f)
                            repository.setDndLengthDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideHeightDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.dndLengthDp + 1f).coerceIn(40f, 240f)
                            repository.setDndLengthDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideHeightDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setDndLengthDp(finalVal.roundToInt().toFloat())
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                        }
                    )

                    SliderRow(
                        title = "Unfocused Icon Placement",
                        valueLabel = "${settings.dndUnfocusedOffsetDp.roundToInt()} dp",
                        value = settings.dndUnfocusedOffsetDp,
                        valueRange = 14f..90f,
                        onValueChange = { v ->
                            val snapped = v.roundToInt().toFloat()
                            repository.setDndUnfocusedOffsetDp(snapped)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideUnfocusedOffsetDp = snapped)
                            }
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.dndUnfocusedOffsetDp - 1f).coerceIn(14f, 90f)
                            repository.setDndUnfocusedOffsetDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideUnfocusedOffsetDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.dndUnfocusedOffsetDp + 1f).coerceIn(14f, 90f)
                            repository.setDndUnfocusedOffsetDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.DND_PANEL) { s ->
                                s.copy(overrideUnfocusedOffsetDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setDndUnfocusedOffsetDp(finalVal.roundToInt().toFloat())
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.DND_PANEL, durationMs = 800L)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Labels",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = settings.showDndLabel,
                            onCheckedChange = repository::setShowDndLabel
                        )
                    }
                }
            }
        }
    }

    if (isScanningCustomButton) {
        CustomButtonScannerDialog(
            currentButtonLabel = settings.customButtonLabel,
            onDismiss = { isScanningCustomButton = false },
            onButtonDetected = { trigger ->
                repository.setCustomHardwareButton(trigger)
                isScanningCustomButton = false
            }
        )
    }
}

@Composable
private fun ActionsPanelCard(
    settings: NudgeSettings,
    repository: SettingsRepository,
    onAddMode: () -> Unit,
    onEditMode: (CustomMode) -> Unit,
    onDeleteMode: (CustomMode) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    var isScanningCustomButton by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Modes/Actions Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Use with Automation / Routines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HowToUseActionsCard()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Actions Panel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = settings.enableActionsPanel,
                    onCheckedChange = repository::setEnableActionsPanel
                )
            }

            AnimatedVisibility(
                visible = settings.enableActionsPanel,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    HardwareTriggerSelector(
                        title = "Hardware Trigger Button",
                        selectedTrigger = settings.modesTrigger,
                        onTriggerSelected = { trigger ->
                            repository.setModesTrigger(trigger)
                            if (trigger == HardwareTrigger.CUSTOM_BTN_SINGLE ||
                                trigger == HardwareTrigger.CUSTOM_BTN_DOUBLE ||
                                trigger == HardwareTrigger.CUSTOM_BTN_LONG
                            ) {
                                isScanningCustomButton = true
                            }
                        }
                    )

                    if (settings.modesTrigger == HardwareTrigger.BOTH_VOLUME_SHORT) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SliderRow(
                                title = "Simultaneous Press Timeout",
                                valueLabel = "${settings.simultaneousPressTimeoutMs} ms",
                                value = settings.simultaneousPressTimeoutMs.toFloat(),
                                valueRange = 1f..500f,
                                onValueChange = {
                                    val v = it.roundToInt().toLong()
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onStepDown = {
                                    val v = (settings.simultaneousPressTimeoutMs - 5L).coerceAtLeast(1L)
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onStepUp = {
                                    val v = (settings.simultaneousPressTimeoutMs + 5L).coerceAtMost(500L)
                                    repository.setSimultaneousPressTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onValueChangeFinished = {
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                                }
                            )
                            Text(
                                text = "Warning: A higher timeout prevents misfires when pressing both buttons, but delays single volume steps when all panels are closed.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (settings.modesTrigger == HardwareTrigger.DOUBLE_TAP_VOL_UP || settings.modesTrigger == HardwareTrigger.DOUBLE_TAP_VOL_DOWN) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SliderRow(
                                title = "Double-Press Timeout",
                                valueLabel = "${settings.modesDoubleTapTimeoutMs} ms",
                                value = settings.modesDoubleTapTimeoutMs.toFloat(),
                                valueRange = 1f..500f,
                                onValueChange = {
                                    val v = it.roundToInt().toLong()
                                    repository.setModesDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onStepDown = {
                                    val v = (settings.modesDoubleTapTimeoutMs - 10L).coerceAtLeast(1L)
                                    repository.setModesDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onStepUp = {
                                    val v = (settings.modesDoubleTapTimeoutMs + 10L).coerceAtMost(500L)
                                    repository.setModesDoubleTapTimeoutMs(v)
                                    LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                                },
                                onValueChangeFinished = {
                                    LivePreviewController.onSliderRelease(800L)
                                    repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                                }
                            )
                            Text(
                                text = "Warning: The volume slider waits for this timeout before showing when double-press is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    SliderRow(
                        title = "Actions Timeout",
                        valueLabel = "${settings.modesTimeoutMs} ms",
                        value = settings.modesTimeoutMs.toFloat(),
                        valueRange = 800f..5000f,
                        onValueChange = {
                            val v = it.roundToInt().toLong()
                            repository.setModesTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.modesTimeoutMs - 100L).coerceAtLeast(800L)
                            repository.setModesTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.modesTimeoutMs + 100L).coerceAtMost(5000L)
                            repository.setModesTimeoutMs(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = {
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                        }
                    )

                    SideSelectorRow(
                        title = "Portrait Side",
                        selectedEdge = settings.modesPortraitEdge,
                        availableEdges = listOf(BezelEdge.LEFT, BezelEdge.RIGHT),
                        onEdgeSelected = { edge ->
                            repository.setModesPortraitEdge(edge)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = false)
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        }
                    )

                    SliderRow(
                        title = "Portrait Position Offset",
                        valueLabel = "${(settings.modesPortraitOffsetPercent * 100f).roundToInt()}%",
                        value = settings.modesPortraitOffsetPercent * 100f,
                        valueRange = 10f..90f,
                        onValueChange = { pct ->
                            val frac = (pct / 100f).coerceIn(0.10f, 0.90f)
                            repository.setModesPortraitOffset(frac)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = frac * 100f)
                            }
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.modesPortraitOffsetPercent - 0.01f).coerceIn(0.10f, 0.90f)
                            repository.setModesPortraitOffset(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = v * 100f)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.modesPortraitOffsetPercent + 0.01f).coerceIn(0.10f, 0.90f)
                            repository.setModesPortraitOffset(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = false) { s ->
                                s.copy(overrideOffsetPercent = v * 100f)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setModesPortraitOffset((finalVal / 100f).coerceIn(0.10f, 0.90f))
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable in Landscape",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = settings.modesEnableLandscape,
                            onCheckedChange = repository::setModesEnableLandscape
                        )
                    }

                    if (settings.modesEnableLandscape) {
                        SideSelectorRow(
                            title = "Landscape Side",
                            selectedEdge = settings.modesLandscapeEdge,
                            availableEdges = BezelEdge.entries,
                            onEdgeSelected = { edge ->
                                repository.setModesLandscapeEdge(edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = true)
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, forceLandscape = true, durationMs = 1500L)
                            }
                        )

                        SliderRow(
                            title = "Landscape Position Offset",
                            valueLabel = "${(settings.modesLandscapeOffsetPercent * 100f).roundToInt()}%",
                            value = settings.modesLandscapeOffsetPercent * 100f,
                            valueRange = 10f..90f,
                            onValueChange = { pct ->
                                val frac = (pct / 100f).coerceIn(0.10f, 0.90f)
                                repository.setModesLandscapeOffset(frac)
                                LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = frac * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = (settings.modesLandscapeOffsetPercent - 0.01f).coerceIn(0.10f, 0.90f)
                                repository.setModesLandscapeOffset(v)
                                LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = (settings.modesLandscapeOffsetPercent + 0.01f).coerceIn(0.10f, 0.90f)
                                repository.setModesLandscapeOffset(v)
                                LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL, isLandscape = true) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, forceLandscape = true, durationMs = 1500L)
                            },
                            onValueChangeFinished = { finalVal ->
                                repository.setModesLandscapeOffset((finalVal / 100f).coerceIn(0.10f, 0.90f))
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, forceLandscape = true, durationMs = 800L)
                            }
                        )
                    }

                    SliderRow(
                        title = "Pill Width",
                        valueLabel = "${settings.modesThicknessDp.roundToInt()} dp",
                        value = settings.modesThicknessDp,
                        valueRange = NudgeSettings.MIN_BEZEL_THICKNESS_DP..NudgeSettings.MAX_BEZEL_THICKNESS_DP,
                        onValueChange = { v ->
                            val snapped = v.roundToInt().toFloat()
                            repository.setModesThicknessDp(snapped)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideWidthDp = snapped)
                            }
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.modesThicknessDp - 1f).coerceIn(NudgeSettings.MIN_BEZEL_THICKNESS_DP, NudgeSettings.MAX_BEZEL_THICKNESS_DP)
                            repository.setModesThicknessDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideWidthDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.modesThicknessDp + 1f).coerceIn(NudgeSettings.MIN_BEZEL_THICKNESS_DP, NudgeSettings.MAX_BEZEL_THICKNESS_DP)
                            repository.setModesThicknessDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideWidthDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setModesThicknessDp(finalVal.roundToInt().toFloat())
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                        }
                    )

                    SliderRow(
                        title = "Pill Height",
                        valueLabel = "${settings.modesLengthDp.roundToInt()} dp",
                        value = settings.modesLengthDp,
                        valueRange = 40f..240f,
                        onValueChange = { v ->
                            val snapped = v.roundToInt().toFloat()
                            repository.setModesLengthDp(snapped)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideHeightDp = snapped)
                            }
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepDown = {
                            val v = (settings.modesLengthDp - 1f).coerceIn(40f, 240f)
                            repository.setModesLengthDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideHeightDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onStepUp = {
                            val v = (settings.modesLengthDp + 1f).coerceIn(40f, 240f)
                            repository.setModesLengthDp(v)
                            LivePreviewController.onSliderDrag(PreviewTarget.ACTIONS_PANEL) { s ->
                                s.copy(overrideHeightDp = v)
                            }
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 1500L)
                        },
                        onValueChangeFinished = { finalVal ->
                            repository.setModesLengthDp(finalVal.roundToInt().toFloat())
                            LivePreviewController.onSliderRelease(800L)
                            repository.requestLivePreview(PreviewTarget.ACTIONS_PANEL, durationMs = 800L)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Labels",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = settings.showActionsLabel,
                            onCheckedChange = repository::setShowActionsLabel
                        )
                    }

                    CustomModesListSection(
                        modes = settings.customModes,
                        onAddMode = onAddMode,
                        onEditMode = onEditMode,
                        onDeleteMode = onDeleteMode,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown
                    )
                }
            }
        }
    }

    if (isScanningCustomButton) {
        CustomButtonScannerDialog(
            currentButtonLabel = settings.customButtonLabel,
            onDismiss = { isScanningCustomButton = false },
            onButtonDetected = { trigger ->
                repository.setCustomHardwareButton(trigger)
                isScanningCustomButton = false
            }
        )
    }
}

@Composable
private fun HowToUseActionsCard() {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "How to Use Actions Panel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Routines & automation setup guide",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text("How to Use Actions Panel", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "How It Works",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Trigger the bezel Actions wheel using your assigned hardware shortcut (e.g. Double-Tap or Both Volume Buttons). Scroll with touch or step with volume buttons to pick an action.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "What You Can Automate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Modes & Routines: Emits a silent notification with the mode keyword that your routine can detect as an 'If' condition.\n• Tasker, MacroDroid, Automate: Receives broadcast 'com.nudge.edge.MODE_CHANGED' with extras 'mode', 'mode_id', and 'mode_name'.\n• Custom profiles: Switch presets for Work, Sleep, Gym, Gaming, or Driving.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Quick Setup Steps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. Choose your preferred Hardware Trigger Button below.\n2. Add or reorder modes in the Modes Manager.\n3. In Modes & Routines or Tasker, create a routine that listens for the notification keyword (tap any keyword below the modes in modes manager to copy it).\n4. Broadcast Step Example: In Tasker/MacroDroid, add an Intent Received event for 'com.nudge.edge.MODE_CHANGED', or test via shell command: am broadcast -a com.nudge.edge.MODE_CHANGED --es mode \"sleep\".",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    shape = CircleShape
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun CustomButtonScannerDialog(
    currentButtonLabel: String? = null,
    onDismiss: () -> Unit,
    onButtonDetected: (HardwareKeyTrigger) -> Unit
) {
    var secondsLeft by remember { mutableStateOf(10) }
    var detectedTrigger by remember { mutableStateOf<HardwareKeyTrigger?>(null) }
    val shizukuState by ShizukuManager.status.collectAsState()

    DisposableEffect(Unit) {
        NudgeAccessibilityService.isScanningCustomButton = true
        NudgeAccessibilityService.onCustomButtonScanned = { trigger ->
            detectedTrigger = trigger
        }
        onDispose {
            NudgeAccessibilityService.isScanningCustomButton = false
            NudgeAccessibilityService.onCustomButtonScanned = null
        }
    }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0 && detectedTrigger == null) {
            kotlinx.coroutines.delay(1000L)
            secondsLeft -= 1
        }
        if (detectedTrigger == null) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Detect Custom Hardware Button", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!currentButtonLabel.isNullOrBlank()) {
                    Text(
                        text = "Currently Bound: $currentButtonLabel",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (detectedTrigger == null) {
                    Text(
                        text = "Press any additional physical button on your device now (e.g. Motorola Action/Side key, Bixby, XCover, Action Button, Assistant, or Mute Switch)...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Listening... ($secondsLeft s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (shizukuState != ShizukuStatus.CONNECTED_AND_ACTIVE) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tip: OEM-exclusive keys (like Motorola AI/Side key or Bixby) require Shizuku to be active so they can be captured via getevent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                } else {
                    val trigger = detectedTrigger!!
                    val label = trigger.linuxKeyName ?: "Key"
                    val sourceLabel = if (trigger.source == TriggerSource.ACCESSIBILITY) {
                        "Accessibility (Standard)"
                    } else {
                        "Shizuku getevent (Hardware evdev)"
                    }
                    Text(
                        text = "Button Detected!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Button: $label", fontWeight = FontWeight.SemiBold)
                        Text("Source: $sourceLabel", style = MaterialTheme.typography.bodySmall)
                        if (!trigger.deviceName.isNullOrBlank()) {
                            Text("Device: ${trigger.deviceName}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (trigger.source == TriggerSource.ACCESSIBILITY) {
                            Text(
                                "KeyCode: ${trigger.androidKeyCode ?: 0} | ScanCode: ${trigger.scanCode ?: 0}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "ScanCode: ${trigger.scanCode ?: 0} (0x${trigger.scanCode?.toString(16)?.uppercase()})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (detectedTrigger != null) {
                Button(
                    onClick = {
                        onButtonDetected(detectedTrigger!!)
                    },
                    shape = CircleShape
                ) {
                    Text("Save Button")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(if (!currentButtonLabel.isNullOrBlank()) "Keep Current" else "Cancel")
                }
            }
        },
        dismissButton = {
            if (detectedTrigger != null) {
                TextButton(onClick = onDismiss) {
                    Text("Discard")
                }
            }
        }
    )
}

@Composable
private fun CustomModesListSection(
    modes: List<CustomMode>,
    onAddMode: () -> Unit,
    onEditMode: (CustomMode) -> Unit,
    onDeleteMode: (CustomMode) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Manage Modes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize icons, colors & Action keywords",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

            modes.forEachIndexed { index, mode ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(mode.colorHex).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = NudgeIcons.getIcon(mode.iconKey),
                                contentDescription = mode.label,
                                tint = Color(mode.colorHex),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Mode Keyword", mode.notificationKeyword))
                                        Toast.makeText(context, "Copied: ${mode.notificationKeyword}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = mode.notificationKeyword,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy keyword",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onMoveUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onMoveDown(index) },
                            enabled = index < modes.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onEditMode(mode) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onDeleteMode(mode) },
                            enabled = modes.size > 2,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = if (modes.size > 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onAddMode,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add New Mode")
            }
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCustomModeDialog(
    mode: CustomMode?,
    onDismiss: () -> Unit,
    onSave: (CustomMode) -> Unit
) {
    val isEdit = mode != null
    var label by remember { mutableStateOf(mode?.label ?: "") }
    var notificationKeyword by remember { mutableStateOf(mode?.notificationKeyword ?: "") }
    var userManuallyEditedKeyword by remember { mutableStateOf(isEdit) }
    var selectedColor by remember { mutableLongStateOf(mode?.colorHex ?: NudgeIcons.ACCENT_COLORS.first()) }
    var selectedIconKey by remember { mutableStateOf(mode?.iconKey ?: "Notifications") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit Mode" else "Add New Mode")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                OutlinedTextField(
                    value = label,
                    shape = CircleShape,
                    onValueChange = { newLabel ->
                        label = newLabel
                        if (!userManuallyEditedKeyword) {
                            val clean = newLabel.trim().uppercase().replace(" ", "_")
                            notificationKeyword = if (clean.isEmpty()) "" else "NUDGE_MODE_$clean"
                        }
                    },
                    label = { Text("Mode Display Name") },
                    placeholder = { Text("e.g. WORK, GYM, STUDY") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notificationKeyword,
                    shape = CircleShape,
                    onValueChange = {
                        userManuallyEditedKeyword = true
                        notificationKeyword = it
                    },
                    label = { Text("Action Trigger Keyword") },
                    placeholder = { Text("e.g. NUDGE_MODE_WORK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NudgeIcons.ACCENT_COLORS.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = if (colorHex == 0xFFFFFFFFL) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Choose Icon",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                NudgeIcons.CATEGORIES.forEach { category ->
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        category.icons.forEach { iconEntry ->
                            val isSelected = selectedIconKey == iconEntry.key
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Color(selectedColor).copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color(selectedColor) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedIconKey = iconEntry.key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconEntry.icon,
                                    contentDescription = iconEntry.name,
                                    tint = if (isSelected) Color(selectedColor) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLabel = label.trim().ifEmpty { "CUSTOM" }
                    val finalKeyword = notificationKeyword.trim().ifEmpty { "NUDGE_MODE_${finalLabel.uppercase()}" }
                    val finalId = mode?.id ?: UUID.randomUUID().toString()
                    onSave(
                        CustomMode(
                            id = finalId,
                            label = finalLabel.uppercase(),
                            notificationKeyword = finalKeyword,
                            iconKey = selectedIconKey,
                            colorHex = selectedColor,
                            dndMode = "UNCHANGED",
                            ringerMode = "UNCHANGED"
                        )
                    )
                },
                shape = CircleShape
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HardwareTriggerSelector(
    title: String,
    selectedTrigger: HardwareTrigger,
    onTriggerSelected: (HardwareTrigger) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedTrigger.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap to change trigger shortcut (7 available)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HardwareTrigger.entries.forEach { trigger ->
                        val isSelected = selectedTrigger == trigger
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTriggerSelected(trigger)
                                    showDialog = false
                                },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = trigger.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SideSelectorRow(
    title: String,
    selectedEdge: BezelEdge,
    availableEdges: List<BezelEdge> = BezelEdge.entries,
    onEdgeSelected: (BezelEdge) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableEdges.forEach { edge ->
                val isSelected = selectedEdge == edge
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEdgeSelected(edge) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = edge.name,
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    onValueChangeFinished: ((Float) -> Unit)? = null
) {
    var sliderValue by remember(value) {
        mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)

    val displayLabel = remember(valueLabel, sliderValue, value) {
        if (sliderValue == value) {
            valueLabel
        } else {
            when {
                valueLabel.endsWith(" dp") -> "${sliderValue.roundToInt()} dp"
                valueLabel.endsWith("%") -> {
                    if (valueRange.endInclusive <= 1.05f) "${(sliderValue * 100f).roundToInt()}%"
                    else "${sliderValue.roundToInt()}%"
                }
                valueLabel.endsWith(" ms") -> "${sliderValue.roundToInt()} ms"
                else -> {
                    val suffix = valueLabel.dropWhile { it.isDigit() || it == '-' || it == '.' || it == ' ' }
                    if (suffix.isNotEmpty()) "${sliderValue.roundToInt()} $suffix"
                    else "${sliderValue.roundToInt()}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
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
            androidx.compose.material3.Slider(
                value = sliderValue.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = {
                    sliderValue = it
                    currentOnValueChange(it)
                },
                onValueChangeFinished = {
                    currentOnFinished?.invoke(sliderValue)
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersistentNudgesCard(
    settings: NudgeSettings,
    repository: SettingsRepository
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Nudge",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Persistent flat-top edge triggers. Swiping from a nudge runs a selected action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enablePersistentNudges,
                    onCheckedChange = repository::setEnablePersistentNudges
                )
            }

            AnimatedVisibility(
                visible = settings.enablePersistentNudges,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SliderRow(
                        title = "Nudge Width",
                        valueLabel = "${settings.nudgeWidthDp.roundToInt()} dp",
                        value = settings.nudgeWidthDp,
                        valueRange = 4f..30f,
                        onValueChange = repository::setNudgeWidthDp,
                        onStepDown = { repository.setNudgeWidthDp(settings.nudgeWidthDp - 1f) },
                        onStepUp = { repository.setNudgeWidthDp(settings.nudgeWidthDp + 1f) }
                    )

                    SliderRow(
                        title = "Nudge Height",
                        valueLabel = "${settings.nudgeHeightDp.roundToInt()} dp",
                        value = settings.nudgeHeightDp,
                        valueRange = 40f..140f,
                        onValueChange = repository::setNudgeHeightDp,
                        onStepDown = { repository.setNudgeHeightDp(settings.nudgeHeightDp - 4f) },
                        onStepUp = { repository.setNudgeHeightDp(settings.nudgeHeightDp + 4f) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Nudge Glow",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display luminous edge glow on all nudge handles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.nudgeGlowEnabled,
                            onCheckedChange = repository::setNudgeGlowEnabled
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    settings.persistentNudges.forEachIndexed { index, nudge ->
                        NudgeConfigItemCard(
                            index = index,
                            nudge = nudge,
                            settings = settings,
                            repository = repository,
                            canDelete = settings.persistentNudges.size > 1,
                            onUpdate = { updated -> repository.updatePersistentNudge(updated) },
                            onDelete = { repository.deletePersistentNudge(nudge.id) }
                        )
                    }

                    if (settings.persistentNudges.size < 10) {
                        OutlinedButton(
                            onClick = {
                                val newId = "nudge_${System.currentTimeMillis()}"
                                val newNudge = PersistentNudge(
                                    id = newId,
                                    enabled = true,
                                    edge = if (settings.persistentNudges.size % 2 == 1) ScreenEdge.LEFT else ScreenEdge.RIGHT,
                                    yFraction = (0.25f + (settings.persistentNudges.size * 0.20f)).coerceIn(0.1f, 0.9f),
                                    displayOnLandscape = false,
                                    action = PersistentNudgeAction.VOLUME_SLIDER_HUD
                                )
                                repository.addPersistentNudge(newNudge)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Nudge (${settings.persistentNudges.size}/10)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NudgeConfigItemCard(
    index: Int,
    nudge: PersistentNudge,
    settings: NudgeSettings,
    repository: SettingsRepository,
    canDelete: Boolean,
    onUpdate: (PersistentNudge) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Nudge ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• ${nudge.action.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete Nudge",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = nudge.enabled,
                        onCheckedChange = { onUpdate(nudge.copy(enabled = it)) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Placement Edge",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ScreenEdge.LEFT to "Left", ScreenEdge.RIGHT to "Right").forEach { (edge, label) ->
                        val selected = nudge.edge == edge
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onUpdate(nudge.copy(edge = edge)) },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            SliderRow(
                title = "Y Axis Position",
                valueLabel = "${(nudge.yFraction * 100f).roundToInt()}%",
                value = nudge.yFraction * 100f,
                valueRange = 5f..95f,
                onValueChange = { onUpdate(nudge.copy(yFraction = (it / 100f).coerceIn(0.05f, 0.95f))) },
                onStepDown = { onUpdate(nudge.copy(yFraction = (nudge.yFraction - 0.05f).coerceIn(0.05f, 0.95f))) },
                onStepUp = { onUpdate(nudge.copy(yFraction = (nudge.yFraction + 0.05f).coerceIn(0.05f, 0.95f))) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Display on Landscape",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show this nudge handle when device is rotated to landscape",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = nudge.displayOnLandscape,
                    onCheckedChange = { onUpdate(nudge.copy(displayOnLandscape = it)) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Swiping Action",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                var actionExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = actionExpanded,
                    onExpandedChange = { actionExpanded = it }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .clickable { actionExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = nudge.action.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (nudge.action == PersistentNudgeAction.NUDGE_MENU || nudge.action == PersistentNudgeAction.OPEN_APP) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "FEATURED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Icon(
                                imageVector = if (actionExpanded) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val actionsList = remember {
                        listOf(
                            PersistentNudgeAction.NUDGE_MENU,
                            PersistentNudgeAction.OPEN_APP,
                            PersistentNudgeAction.OPEN_CAMERA,
                            PersistentNudgeAction.VOICE_ASSISTANT,
                            PersistentNudgeAction.VOLUME_SLIDER_HUD,
                            PersistentNudgeAction.AUDIO_MIXER,
                            PersistentNudgeAction.SHOW_SYSTEM_VOLUME,
                            PersistentNudgeAction.VOLUME_UP,
                            PersistentNudgeAction.VOLUME_DOWN,
                            PersistentNudgeAction.MEDIA_PLAY_PAUSE,
                            PersistentNudgeAction.MEDIA_NEXT,
                            PersistentNudgeAction.MEDIA_PREVIOUS,
                            PersistentNudgeAction.ACTIONS_PANEL,
                            PersistentNudgeAction.DND_PANEL,
                            PersistentNudgeAction.TOGGLE_DND,
                            PersistentNudgeAction.TOGGLE_SILENT_RING,
                            PersistentNudgeAction.NOTIFICATIONS,
                            PersistentNudgeAction.QUICK_PANEL,
                            PersistentNudgeAction.TAKE_SCREENSHOT,
                            PersistentNudgeAction.TOGGLE_FLASHLIGHT,
                            PersistentNudgeAction.TOGGLE_AUTO_ROTATE,
                            PersistentNudgeAction.BACK,
                            PersistentNudgeAction.HOME,
                            PersistentNudgeAction.RECENTS,
                            PersistentNudgeAction.LOCK_SCREEN,
                            PersistentNudgeAction.POWER_MENU
                        )
                    }

                    ExposedDropdownMenu(
                        expanded = actionExpanded,
                        onDismissRequest = { actionExpanded = false },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.clip(RoundedCornerShape(24.dp))
                    ) {
                        actionsList.forEach { action ->
                            val isSelected = nudge.action == action
                            val isFeatured = action == PersistentNudgeAction.NUDGE_MENU || action == PersistentNudgeAction.OPEN_APP
                            DropdownMenuItem(
                                leadingIcon = null,
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = action.displayName,
                                            fontWeight = if (isSelected || isFeatured) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isFeatured -> Color.White
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isFeatured) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "FEATURED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onUpdate(nudge.copy(action = action))
                                    actionExpanded = false
                                }
                            )
                            if (action == PersistentNudgeAction.OPEN_APP) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = nudge.action == PersistentNudgeAction.NUDGE_MENU,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "Nudge Menu Sizing & Position",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SliderRow(
                            title = "Menu Width",
                            valueLabel = "${settings.nudgeMenuWidthDp.roundToInt()} dp",
                            value = settings.nudgeMenuWidthDp,
                            valueRange = 20f..100f,
                            onValueChange = { v ->
                                repository.setNudgeMenuWidthDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideWidthDp = v)
                                }
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = settings.nudgeMenuWidthDp - 1f
                                repository.setNudgeMenuWidthDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideWidthDp = v)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = settings.nudgeMenuWidthDp + 1f
                                repository.setNudgeMenuWidthDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideWidthDp = v)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 800L)
                            }
                        )

                        SliderRow(
                            title = "Menu Height",
                            valueLabel = "${settings.nudgeMenuHeightDp.roundToInt()} dp",
                            value = settings.nudgeMenuHeightDp,
                            valueRange = 180f..500f,
                            onValueChange = { v ->
                                repository.setNudgeMenuHeightDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideHeightDp = v)
                                }
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = settings.nudgeMenuHeightDp - 10f
                                repository.setNudgeMenuHeightDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideHeightDp = v)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = settings.nudgeMenuHeightDp + 10f
                                repository.setNudgeMenuHeightDp(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideHeightDp = v)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 800L)
                            }
                        )

                        SliderRow(
                            title = "Y Axis Position",
                            valueLabel = "${(settings.nudgeMenuYFraction * 100).roundToInt()} %",
                            value = settings.nudgeMenuYFraction,
                            valueRange = 0.10f..0.90f,
                            onValueChange = { v ->
                                repository.setNudgeMenuYFraction(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = settings.nudgeMenuYFraction - 0.02f
                                repository.setNudgeMenuYFraction(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = settings.nudgeMenuYFraction + 0.02f
                                repository.setNudgeMenuYFraction(v)
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.NUDGE_MENU) { s ->
                                    s.copy(overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.NUDGE_MENU, durationMs = 800L)
                            }
                        )

                    }
                }

                AnimatedVisibility(
                    visible = nudge.action == PersistentNudgeAction.OPEN_APP,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    var showManageAppsDialog by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Selected Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${nudge.selectedApps.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = { showManageAppsDialog = true },
                                shape = CircleShape
                            ) {
                                Text("Manage")
                            }
                        }

                        if (showManageAppsDialog) {
                            ManageNudgeAppsDialog(
                                initialSelectedApps = nudge.selectedApps,
                                onAppsUpdated = { updatedList ->
                                    onUpdate(
                                        nudge.copy(
                                            selectedApps = updatedList,
                                            targetPackage = updatedList.firstOrNull()?.packageName ?: "",
                                            targetAppName = updatedList.firstOrNull()?.appName ?: ""
                                        )
                                    )
                                },
                                onDismiss = { showManageAppsDialog = false }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "Open Apps Menu Sizing & Position",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SliderRow(
                            title = "Menu Width",
                            valueLabel = "${nudge.menuWidthDp.roundToInt()} dp",
                            value = nudge.menuWidthDp,
                            valueRange = 20f..100f,
                            onValueChange = { v ->
                                onUpdate(nudge.copy(menuWidthDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = v, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = nudge.menuWidthDp - 1f
                                onUpdate(nudge.copy(menuWidthDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = v, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = nudge.menuWidthDp + 1f
                                onUpdate(nudge.copy(menuWidthDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = v, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 800L)
                            }
                        )

                        SliderRow(
                            title = "Menu Height",
                            valueLabel = "${nudge.menuHeightDp.roundToInt()} dp",
                            value = nudge.menuHeightDp,
                            valueRange = 180f..500f,
                            onValueChange = { v ->
                                onUpdate(nudge.copy(menuHeightDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = v, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = nudge.menuHeightDp - 10f
                                onUpdate(nudge.copy(menuHeightDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = v, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = nudge.menuHeightDp + 10f
                                onUpdate(nudge.copy(menuHeightDp = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = v, overrideOffsetPercent = nudge.menuYFraction * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 800L)
                            }
                        )

                        SliderRow(
                            title = "Y Axis Position",
                            valueLabel = "${(nudge.menuYFraction * 100).roundToInt()} %",
                            value = nudge.menuYFraction,
                            valueRange = 0.10f..0.90f,
                            onValueChange = { v ->
                                onUpdate(nudge.copy(menuYFraction = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = v * 100f)
                                }
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepDown = {
                                val v = nudge.menuYFraction - 0.02f
                                onUpdate(nudge.copy(menuYFraction = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onStepUp = {
                                val v = nudge.menuYFraction + 0.02f
                                onUpdate(nudge.copy(menuYFraction = v))
                                repository.setNudgeMenuEdge(nudge.edge)
                                LivePreviewController.onSliderDrag(PreviewTarget.OPEN_APPS_MENU) { s ->
                                    s.copy(overrideWidthDp = nudge.menuWidthDp, overrideHeightDp = nudge.menuHeightDp, overrideOffsetPercent = v * 100f)
                                }
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 1500L)
                            },
                            onValueChangeFinished = {
                                LivePreviewController.onSliderRelease(800L)
                                repository.requestLivePreview(PreviewTarget.OPEN_APPS_MENU, durationMs = 800L)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ManageNudgeAppsDialog(
    initialSelectedApps: List<NudgeMenuApp>,
    onAppsUpdated: (List<NudgeMenuApp>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        InstalledAppsManager.preload(context.applicationContext)
    }
    val installedApps by InstalledAppsManager.apps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val currentSelected = remember {
        mutableStateListOf<NudgeMenuApp>().apply { addAll(initialSelectedApps) }
    }
    val selectedPackages = remember(currentSelected) {
        derivedStateOf { currentSelected.map { it.packageName }.toSet() }
    }
    var draggingPkg by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemStepPx = with(LocalDensity.current) { (58.dp + 6.dp).toPx() }
    val swapThreshold = itemStepPx * 0.45f
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            listState.scrollToItem(0)
        }
    }

    val displayApps = remember(installedApps, currentSelected.toList(), searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isNotBlank()) {
            installedApps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        } else {
            val selectedAppItems = currentSelected.map { sel ->
                installedApps.firstOrNull { it.packageName == sel.packageName }
                    ?: InstalledAppItem(sel.packageName, sel.appName, null)
            }
            val unselectedItems = installedApps.filter { inst ->
                currentSelected.none { it.packageName == inst.packageName }
            }
            selectedAppItems + unselectedItems
        }
    }

    AlertDialog(
        onDismissRequest = {
            onAppsUpdated(currentSelected.toList())
            onDismiss()
        },
        title = { Text("Manage Apps", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

                Text(
                    text = "Hold and drag the circle to reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp)
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
                    val density = LocalDensity.current
                    val circleZoneWidthPx = with(density) { 64.dp.toPx() }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = draggingPkg == null,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .weight(1f, fill = false)
                            .pointerInput(searchQuery, currentSelected.size) {
                                if (searchQuery.isNotBlank() || currentSelected.size <= 1) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val listWidth = size.width
                                        val inZone = offset.x >= listWidth - circleZoneWidthPx
                                        val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            item.key != "reorder_top_anchor" && offset.y >= item.offset && offset.y <= item.offset + item.size
                                        }
                                        val pkg = hitItem?.key as? String
                                        if (inZone && pkg != null && selectedPackages.value.contains(pkg)) {
                                            draggingPkg = pkg
                                            dragOffsetY = 0f
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        } else {
                                            draggingPkg = null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val dragged = draggingPkg ?: return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        var curIdx = currentSelected.indexOfFirst { it.packageName == dragged }
                                        if (curIdx != -1) {
                                            dragOffsetY += dragAmount.y
                                            val currentInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == dragged }
                                            val itemHeight = currentInfo?.size?.toFloat() ?: itemStepPx
                                            val downThreshold = itemHeight * 0.55f
                                            val upThreshold = -itemHeight * 0.45f

                                            while (dragOffsetY > downThreshold && curIdx < currentSelected.lastIndex) {
                                                val nextIdx = curIdx + 1
                                                val temp = currentSelected[curIdx]
                                                currentSelected[curIdx] = currentSelected[nextIdx]
                                                currentSelected[nextIdx] = temp
                                                dragOffsetY -= itemHeight
                                                curIdx = nextIdx
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            while (dragOffsetY < upThreshold && curIdx > 0) {
                                                val prevIdx = curIdx - 1
                                                val temp = currentSelected[curIdx]
                                                currentSelected[curIdx] = currentSelected[prevIdx]
                                                currentSelected[prevIdx] = temp
                                                dragOffsetY += itemHeight
                                                curIdx = prevIdx
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingPkg = null
                                        dragOffsetY = 0f
                                        onAppsUpdated(currentSelected.toList())
                                    },
                                    onDragCancel = {
                                        draggingPkg = null
                                        dragOffsetY = 0f
                                        onAppsUpdated(currentSelected.toList())
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "reorder_top_anchor") {
                            Spacer(modifier = Modifier.height(0.dp))
                        }
                        items(displayApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackages.value.contains(app.packageName)
                            val isDraggingThis = draggingPkg == app.packageName
                            val itemModifier = if (isDraggingThis) {
                                Modifier
                                    .zIndex(10f)
                                    .graphicsLayer {
                                        translationY = dragOffsetY
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                        shadowElevation = 10f
                                    }
                            } else {
                                Modifier
                                    .zIndex(0f)
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                            }

                            Surface(
                                onClick = {
                                    if (draggingPkg == null) {
                                        if (isSelected) {
                                            currentSelected.removeAll { it.packageName == app.packageName }
                                        } else {
                                            currentSelected.add(NudgeMenuApp(app.packageName, app.label))
                                        }
                                        onAppsUpdated(currentSelected.toList())
                                    }
                                },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(itemModifier)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val bitmap = remember(app.packageName) {
                                        runCatching {
                                            (app.icon ?: context.packageManager.getApplicationIcon(app.packageName)).toBitmap(64, 64).asImageBitmap()
                                        }.getOrNull()
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

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.Transparent)
                                                .border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAppsUpdated(currentSelected.toList())
                    onDismiss()
                },
                shape = CircleShape
            ) {
                Text("Done")
            }
        }
    )
}
