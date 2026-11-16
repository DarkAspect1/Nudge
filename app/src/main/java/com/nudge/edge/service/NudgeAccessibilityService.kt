package com.nudge.edge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.nudge.edge.MainActivity
import com.nudge.edge.R
import com.nudge.edge.data.ActiveMediaApp
import com.nudge.edge.data.AppVolumeController
import com.nudge.edge.data.FineVolumeController
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.VolumeController
import com.nudge.edge.data.VolumeEngineMode
import com.nudge.edge.model.AppMode
import com.nudge.edge.model.BezelEdge
import com.nudge.edge.model.CustomMode
import com.nudge.edge.model.DEFAULT_CUSTOM_MODES
import com.nudge.edge.model.DndModeType
import com.nudge.edge.model.EdgeOrientationMapper
import com.nudge.edge.model.EdgePopType
import com.nudge.edge.model.HardwareTrigger
import com.nudge.edge.model.HudTriggerMode
import com.nudge.edge.model.LivePreviewController
import com.nudge.edge.model.LivePreviewState
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
import com.nudge.edge.ui.components.computeHudCutoutThicknessDp
import com.nudge.edge.ui.components.computeHudOuterSpanDp
import com.nudge.edge.ui.theme.NudgeTheme
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class NudgeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private lateinit var powerManager: PowerManager
    private lateinit var cameraManager: CameraManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var volumeController: VolumeController
    private val lifecycleOwner = OverlayLifecycleOwner()

    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null
    private var audioScope: CoroutineScope? = null

    private var edgePopView: ComposeView? = null
    private var hudView: ComposeView? = null
    private var modalView: View? = null
    private var swipeCatcherView: View? = null
    private var dndView: ComposeView? = null
    private var modesView: ComposeView? = null

    private var hudParams: WindowManager.LayoutParams? = null
    private var modalParams: WindowManager.LayoutParams? = null
    private var swipeCatcherParams: WindowManager.LayoutParams? = null
    private var dndParams: WindowManager.LayoutParams? = null
    private var modesParams: WindowManager.LayoutParams? = null

    private val upPopActive = MutableStateFlow(false)
    private val downPopActive = MutableStateFlow(false)
    private val powerPopActive = MutableStateFlow(false)
    private val upPulseTrigger = MutableStateFlow(0)
    private val downPulseTrigger = MutableStateFlow(0)
    private val powerPulseTrigger = MutableStateFlow(0)
    private val hudActive = MutableStateFlow(false)
    private val modalActive = MutableStateFlow(false)
    private val dndActive = MutableStateFlow(false)
    private val dndState = MutableStateFlow(false)
    private val modesActive = MutableStateFlow(false)
    private val modesSelectedModeId = MutableStateFlow("normal")
    private val actionsWheelStep = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    private val volumeFraction = MutableStateFlow(0.5f)
    private val isBluetoothAudioConnected = MutableStateFlow(false)
    private val screenSizeState = MutableStateFlow(Pair(1080, 2400))
    private val rotationState = MutableStateFlow(Surface.ROTATION_0)
    private val rotationResetEpoch = MutableStateFlow(0)

    @Volatile
    private var isVolumeSliderEnabledFast: Boolean = true

    @Volatile
    private var isCurrentAppExcludedFast: Boolean = false

    @Volatile
    private var cachedEdgePopTimeoutMs: Long = 300L

    @Volatile
    private var currentSubStepGain: Float = 1.0f

    lateinit var fineVolumeController: FineVolumeController
        private set
    lateinit var appVolumeController: AppVolumeController
        private set

    private val streamStepsMap = ConcurrentHashMap<Int, Int>()

    fun getStoredStepsForStream(streamType: Int): Int {
        return streamStepsMap.getOrPut(streamType) {
            if (::fineVolumeController.isInitialized) {
                fineVolumeController.getStreamCurrentSteps(streamType)
            } else 0
        }
    }

    fun setStoredStepsForStream(streamType: Int, steps: Int) {
        val maxLimit = if (::fineVolumeController.isInitialized) {
            fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        } else 100
        val clamped = steps.coerceIn(0, maxLimit)
        streamStepsMap[streamType] = clamped
        val frac = (clamped.toFloat() / maxLimit.toFloat()).coerceIn(0f, 1f)
        when (streamType) {
            AudioManager.STREAM_MUSIC -> {
                mediaNormalizedVolume = frac
                if (resolveActiveVolumeStream() == ActiveVolumeStream.MEDIA) {
                    volumeFraction.value = frac
                }
            }
            AudioManager.STREAM_RING -> {
                ringNormalizedVolume = frac
                if (resolveActiveVolumeStream() == ActiveVolumeStream.RING) {
                    volumeFraction.value = frac
                }
            }
            AudioManager.STREAM_VOICE_CALL -> {
                callNormalizedVolume = frac
                if (resolveActiveVolumeStream() == ActiveVolumeStream.CALL) {
                    volumeFraction.value = frac
                }
            }
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (::fineVolumeController.isInitialized && ::appVolumeController.isInitialized) {
            fineVolumeController.verifyAndResolveEngineMode()
            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
        }
    }

    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1001 && grantResult == PackageManager.PERMISSION_GRANTED) {
            if (::fineVolumeController.isInitialized && ::appVolumeController.isInitialized) {
                fineVolumeController.verifyAndResolveEngineMode()
                appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
            }
        }
    }

    private enum class ButtonType {
        VOL_UP,
        VOL_DOWN,
        POWER
    }

    private fun ButtonType.toEdgePopType(): EdgePopType = when (this) {
        ButtonType.VOL_UP -> EdgePopType.VOL_UP
        ButtonType.VOL_DOWN -> EdgePopType.VOL_DOWN
        ButtonType.POWER -> EdgePopType.POWER
    }

    private val isButtonPhysicallyHeld = ConcurrentHashMap<ButtonType, Boolean>()
    private val edgePopDismissJobs = ConcurrentHashMap<ButtonType, Job>()
    private val edgePopPanelScrollSafetyJobs = ConcurrentHashMap<ButtonType, Job>()

    private val _effectiveSettingsFallback = MutableStateFlow(NudgeSettings())
    private val _effectivePreviewTargetFallback = MutableStateFlow(PreviewTarget.NONE)
    var effectiveSettingsState: StateFlow<NudgeSettings> = _effectiveSettingsFallback
        private set
    var effectivePreviewTargetState: StateFlow<PreviewTarget> = _effectivePreviewTargetFallback
        private set

    private fun setEdgePopVisible(button: ButtonType, visible: Boolean) {
        when (button) {
            ButtonType.VOL_UP -> upPopActive.value = visible
            ButtonType.VOL_DOWN -> downPopActive.value = visible
            ButtonType.POWER -> powerPopActive.value = visible
        }
    }

    private fun scheduleEdgePopAutoDismiss(button: ButtonType, delayMs: Long = cachedEdgePopTimeoutMs) {
        edgePopDismissJobs[button]?.cancel()
        val timeout = delayMs.coerceIn(50L, 5000L)
        edgePopDismissJobs[button] = serviceScope.launch {
            delay(timeout)
            isButtonPhysicallyHeld[button] = false
            when (button) {
                ButtonType.VOL_UP -> volUpIsDown = false
                ButtonType.VOL_DOWN -> volDownIsDown = false
                ButtonType.POWER -> powerIsDown = false
            }
            setEdgePopVisible(button, false)
        }
    }

    private fun updateEdgePopForHardwareEvent(event: KeyEvent) {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ButtonType.VOL_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> ButtonType.VOL_DOWN
            KeyEvent.KEYCODE_POWER -> ButtonType.POWER
            else -> return
        }
        val currentSettings = effectiveSettingsState.value
        val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
        val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, currentSettings) &&
            currentSettings.isEdgePopMasterEnabled &&
            (button != ButtonType.POWER || currentSettings.enablePowerButtonPop)

        val isCanceled = (event.flags and KeyEvent.FLAG_CANCELED) != 0
        if (isCanceled) {
            isButtonPhysicallyHeld[button] = false
            edgePopPanelScrollSafetyJobs[button]?.cancel()
            edgePopDismissJobs[button]?.cancel()
            setEdgePopVisible(button, false)
            return
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    isButtonPhysicallyHeld[button] = true
                    val isPanelOpen = modesActive.value || dndActive.value
                    if (popAllowed && !doesVisiblePanelOverlapEdgePop(button.toEdgePopType())) {
                        setEdgePopVisible(button, true)
                        scheduleEdgePopAutoDismiss(button, cachedEdgePopTimeoutMs + 400L)
                        if (currentSettings.effectiveEdgePopHaptics) {
                            audioScope?.launch { volumeController.triggerHaptic(strong = false) }
                        }
                    }
                    if (isPanelOpen) {
                        edgePopPanelScrollSafetyJobs[button]?.cancel()
                        edgePopPanelScrollSafetyJobs[button] = serviceScope.launch {
                            delay(220L)
                            if (modesActive.value || dndActive.value) {
                                setEdgePopVisible(button, false)
                            }
                        }
                    }
                }
            }
            KeyEvent.ACTION_UP -> {
                isButtonPhysicallyHeld[button] = false
                edgePopPanelScrollSafetyJobs[button]?.cancel()
                if (popAllowed) {
                    scheduleEdgePopAutoDismiss(button, cachedEdgePopTimeoutMs)
                } else {
                    edgePopDismissJobs[button]?.cancel()
                    setEdgePopVisible(button, false)
                }
            }
        }
    }

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateBluetoothState()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateBluetoothState()
            }
        }
    } else null

    private fun updateBluetoothState() {
        if (::volumeController.isInitialized) {
            val connected = volumeController.isBluetoothAudioConnected()
            isBluetoothAudioConnected.value = connected
        }
    }

    private var upRetractJob: Job? = null
    private var downRetractJob: Job? = null
    private var powerRetractJob: Job? = null
    private var upRepeatJob: Job? = null
    private var downRepeatJob: Job? = null
    private var hudHideJob: Job? = null
    private var hudCollapseGoneJob: Job? = null
    private var modalCollapseGoneJob: Job? = null
    private var dndHideJob: Job? = null
    private var dndGoneJob: Job? = null
    private var modesHideJob: Job? = null
    private var modesGoneJob: Job? = null
    private var swipeTouchActive = false
    private var hudScrubbing = false

    private var pendingVolUpJob: Job? = null
    private var pendingVolDownJob: Job? = null
    private var pendingVolUpReleased = false
    private var pendingVolDownReleased = false

    private var lastVolUpPressTimeMs = 0L
    private var lastVolDownPressTimeMs = 0L
    private var volUpIsDown = false
    private var volDownIsDown = false
    private var chordCandidateStartTimeMs = 0L
    private var chordFired = false
    private var powerIsDown = false
    private var lastPowerDownTimeMs = 0L
    private var lastPowerComboFiredTimeMs = 0L
    private var lastVolUpReleaseTimeMs = 0L
    private var lastVolDownReleaseTimeMs = 0L
    private var customBtnDown = false
    private var customBtnDownTimeMs = 0L
    private var customBtnLongFired = false
    private var customBtnLongJob: Job? = null
    private var customBtnSinglePendingJob: Job? = null
    private var lastCustomBtnTapTimeMs = 0L
    private var lastUpTapTimeMs = 0L
    private var lastDownTapTimeMs = 0L
    private var popWatchdogJob: Job? = null

    @Volatile
    private var lastVolumePressTimeMs = 0L

    @Volatile
    private var lastUpKeyEventTimestamp = 0L

    @Volatile
    private var lastDownKeyEventTimestamp = 0L

    @Volatile
    private var lastPowerKeyEventTimestamp = 0L

    enum class ActiveVolumeStream {
        CALL,
        MEDIA,
        RING
    }

    @Volatile
    private var lastCallHardwareClickMs = 0L

    @Volatile
    private var callNormalizedVolume = 0.5f

    @Volatile
    private var mediaNormalizedVolume = 0.5f

    @Volatile
    private var ringNormalizedVolume = 0.5f

    private fun isDialerApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower.contains("dialer") || lower.contains("phone") || lower.contains("incallui") || lower.contains("telecom")
    }

    private fun isForegroundAppMediaCapable(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower.contains("youtube") || lower.contains("spotify") || lower.contains("music") ||
            lower.contains("netflix") || lower.contains("tiktok") || lower.contains("instagram") ||
            lower.contains("reels") || lower.contains("player") || lower.contains("podcast") ||
            lower.contains("twitch") || lower.contains("video") || lower.contains("chrome") ||
            lower.contains("browser")
    }

    private fun resolveActiveVolumeStreamSafe(): ActiveVolumeStream {
        return try {
            val mode = audioManager.mode
            val isInCall = (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION)
            if (isInCall) {
                val pkg = currentForegroundPackage.orEmpty()
                val isDialerOrSystem = pkg.isEmpty() ||
                    pkg.contains("incallui") || pkg.contains("dialer") ||
                    pkg.contains("telecom") || pkg.contains("phone") ||
                    pkg.contains("systemui") || pkg == packageName
                val hasActiveMediaPlayback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        audioManager.activePlaybackConfigurations.any { config ->
                            val usage = config.audioAttributes?.usage
                            (usage == android.media.AudioAttributes.USAGE_MEDIA || usage == android.media.AudioAttributes.USAGE_GAME) &&
                            config.audioAttributes?.contentType != android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                        }
                    }.getOrDefault(audioManager.isMusicActive)
                } else {
                    audioManager.isMusicActive
                }
                if (!isDialerOrSystem && hasActiveMediaPlayback && isForegroundAppMediaCapable(pkg)) {
                    ActiveVolumeStream.MEDIA
                } else {
                    ActiveVolumeStream.CALL
                }
            } else if (mode == AudioManager.MODE_RINGTONE) {
                ActiveVolumeStream.RING
            } else {
                ActiveVolumeStream.MEDIA
            }
        } catch (t: Throwable) {
            ActiveVolumeStream.MEDIA
        }
    }

    private fun resolveActiveVolumeStream(): ActiveVolumeStream = resolveActiveVolumeStreamSafe()

    @Volatile
    private var lastPowerTriggerTimeMs = 0L

    @Volatile
    private var lastProgrammaticAudioTimeMs = 0L

    @Volatile
    private var modalOpenedTimeMs = 0L

    @Volatile
    private var modalDismissedTimeMs = 0L

    @Volatile
    private var isScreenInteractive = true

    @Volatile
    private var currentForegroundPackage = ""

    @Volatile
    private var cameraPackages: Set<String> = emptySet()

    @Volatile
    private var publicCameraIds: Set<String> = emptySet()

    private val activeCameraIds = ConcurrentHashMap.newKeySet<String>()
    private val torchActiveCameraIds = ConcurrentHashMap.newKeySet<String>()
    private val holdSequence = AtomicLong(0L)

    @Volatile
    private var keyHeldDown = false
    private var consumedUpKeyDown = false
    private var consumedDownKeyDown = false
    private var consumedBackKeyDown = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!publicCameraIds.contains(cameraId)) return
            if (enabled) {
                torchActiveCameraIds.add(cameraId)
            } else {
                torchActiveCameraIds.remove(cameraId)
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            torchActiveCameraIds.remove(cameraId)
        }
    }

    private val cameraAvailabilityCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            activeCameraIds.remove(cameraId)
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (!publicCameraIds.contains(cameraId)) return
            activeCameraIds.add(cameraId)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDisplayChange()
            }
        }

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDisplayChange()
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.media.VOLUME_CHANGED_ACTION") return
            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (streamType < 0) return
            val osNewValue = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)

            audioScope?.launch {
                if (osNewValue > 0) {
                    volumeController.recordObservedStreamValue(streamType, osNewValue)
                }
                if (::fineVolumeController.isInitialized) {
                    val (minIdx, nativeRange) = fineVolumeController.getNativeHardwareRange(streamType)
                    val maxLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
                    val stored = getStoredStepsForStream(streamType)
                    val expectedNative = ((stored.toFloat() / maxLimit.toFloat()) * nativeRange).roundToInt() + minIdx
                    if (osNewValue != expectedNative) {
                        val freshSteps = fineVolumeController.getStreamCurrentSteps(streamType)
                        setStoredStepsForStream(streamType, freshSteps)
                    }
                }
                val now = SystemClock.uptimeMillis()
                val activeStreamEnum = resolveActiveVolumeStream()
                val isBtConnected = volumeController.isBluetoothAudioConnected()
                if (streamType == AudioManager.STREAM_VOICE_CALL || streamType == 0) {
                    if ((now - lastCallHardwareClickMs) > 350L) {
                        val maxLim = if (::fineVolumeController.isInitialized) fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1) else 100
                        callNormalizedVolume = (getStoredStepsForStream(AudioManager.STREAM_VOICE_CALL).toFloat() / maxLim.toFloat()).coerceIn(0f, 1f)
                        if (activeStreamEnum == ActiveVolumeStream.CALL) {
                            volumeFraction.value = callNormalizedVolume
                        }
                    }
                    return@launch
                }
                if (streamType == AudioManager.STREAM_RING) {
                    val maxLim = if (::fineVolumeController.isInitialized) fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1) else 100
                    ringNormalizedVolume = (getStoredStepsForStream(AudioManager.STREAM_RING).toFloat() / maxLim.toFloat()).coerceIn(0f, 1f)
                    if (activeStreamEnum == ActiveVolumeStream.RING) {
                        volumeFraction.value = ringNormalizedVolume
                    }
                    return@launch
                }
                if ((streamType == AudioManager.STREAM_MUSIC || streamType == 6) &&
                    !hudScrubbing &&
                    !keyHeldDown &&
                    (now - lastProgrammaticAudioTimeMs) > 250L
                ) {
                    val maxLim = if (::fineVolumeController.isInitialized) fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1) else 100
                    mediaNormalizedVolume = (getStoredStepsForStream(AudioManager.STREAM_MUSIC).toFloat() / maxLim.toFloat()).coerceIn(0f, 1f)
                    if (activeStreamEnum == ActiveVolumeStream.MEDIA) {
                        volumeFraction.value = mediaNormalizedVolume
                    }
                    if (isBtConnected) {
                        serviceScope.launch(Dispatchers.Main) {
                            val currentSettings = effectiveSettingsState.value
                            val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
                            if (EdgeOrientationMapper.shouldShowSliderHud(isLandscape, currentSettings)) {
                                handleHudTrigger(settings = currentSettings, initialPress = false)
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenIfNeeded() {
        if (!isScreenInteractive) {
            runCatching {
                if (::powerManager.isInitialized) {
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "nudge:wake"
                    )
                    wakeLock.acquire(1000L)
                }
            }
        }
    }

    private val powerScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val now = SystemClock.uptimeMillis()
            if (action == Intent.ACTION_SCREEN_OFF) {
                isScreenInteractive = false
                holdSequence.incrementAndGet()
                clearAllEdgePops()
                closeMixerAndVolumeOverlaysOnScreenOff()
            } else if (action == Intent.ACTION_SCREEN_ON) {
                isScreenInteractive = true
            } else if (action == Intent.ACTION_USER_PRESENT) {
                isScreenInteractive = true
                return
            }

            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_SCREEN_OFF) {
                val settings = effectiveSettingsState.value
                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
                val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
                if (!settings.enablePowerButtonPop || !popAllowed) return
                if (now - lastPowerTriggerTimeMs >= 260L) {
                    triggerPowerPop(holdMs = settings.popAutoHideMs, settings = settings)
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settingsRepository = SettingsRepository.getInstance(this)
        effectiveSettingsState = combine(
            settingsRepository.settings,
            LivePreviewController.state
        ) { base, live ->
            when (live.activeTarget) {
                PreviewTarget.EDGE_POP_VOL_UP -> base.copy(
                    popWidthDp = live.overrideWidthDp ?: base.popWidthDp,
                    popHeightDp = live.overrideHeightDp ?: base.popHeightDp,
                    volumeUpYFraction = (live.overrideOffsetPercent ?: (base.volumeUpYFraction * 100f)) / 100f
                )
                PreviewTarget.EDGE_POP_VOL_DOWN -> base.copy(
                    popWidthDp = live.overrideWidthDp ?: base.popWidthDp,
                    popHeightDp = live.overrideHeightDp ?: base.popHeightDp,
                    volumeDownYFraction = (live.overrideOffsetPercent ?: (base.volumeDownYFraction * 100f)) / 100f
                )
                PreviewTarget.EDGE_POP_POWER -> base.copy(
                    powerPopWidthDp = live.overrideWidthDp ?: base.powerPopWidthDp,
                    powerPopHeightDp = live.overrideHeightDp ?: base.powerPopHeightDp,
                    powerButtonYFraction = (live.overrideOffsetPercent ?: (base.powerButtonYFraction * 100f)) / 100f
                )
                PreviewTarget.EDGE_POP_ALL -> base.copy(
                    popWidthDp = live.overrideWidthDp ?: base.popWidthDp,
                    popHeightDp = live.overrideHeightDp ?: base.popHeightDp,
                    volumeUpYFraction = (live.overrideOffsetPercent ?: (base.volumeUpYFraction * 100f)) / 100f,
                    volumeDownYFraction = (live.overrideOffsetPercent ?: (base.volumeDownYFraction * 100f)) / 100f
                )
                PreviewTarget.VOLUME_SLIDER, PreviewTarget.HUD -> if (live.isLandscapePreview) {
                    base.copy(
                        hudWidthDp = live.overrideWidthDp ?: base.hudWidthDp,
                        hudHeightDp = live.overrideHeightDp ?: base.hudHeightDp,
                        hudCornerRadiusDp = live.overrideCornerRadiusDp ?: base.hudCornerRadiusDp,
                        landscapeHudYFraction = (live.overrideOffsetPercent ?: (base.landscapeHudYFraction * 100f)) / 100f
                    )
                } else {
                    base.copy(
                        hudWidthDp = live.overrideWidthDp ?: base.hudWidthDp,
                        hudHeightDp = live.overrideHeightDp ?: base.hudHeightDp,
                        hudCornerRadiusDp = live.overrideCornerRadiusDp ?: base.hudCornerRadiusDp,
                        hudYFraction = (live.overrideOffsetPercent ?: (base.hudYFraction * 100f)) / 100f
                    )
                }
                PreviewTarget.DND_PANEL, PreviewTarget.DND -> if (live.isLandscapePreview) {
                    base.copy(
                        dndThicknessDp = live.overrideWidthDp ?: base.dndThicknessDp,
                        dndLengthDp = live.overrideHeightDp ?: base.dndLengthDp,
                        dndLandscapeOffsetPercent = ((live.overrideOffsetPercent ?: (base.dndLandscapeOffsetPercent * 100f)) / 100f).coerceIn(0f, 1f),
                        dndUnfocusedOffsetDp = live.overrideUnfocusedOffsetDp ?: base.dndUnfocusedOffsetDp
                    )
                } else {
                    base.copy(
                        dndThicknessDp = live.overrideWidthDp ?: base.dndThicknessDp,
                        dndLengthDp = live.overrideHeightDp ?: base.dndLengthDp,
                        dndPortraitOffsetPercent = ((live.overrideOffsetPercent ?: (base.dndPortraitOffsetPercent * 100f)) / 100f).coerceIn(0f, 1f),
                        dndUnfocusedOffsetDp = live.overrideUnfocusedOffsetDp ?: base.dndUnfocusedOffsetDp
                    )
                }
                PreviewTarget.ACTIONS_PANEL, PreviewTarget.MODES -> if (live.isLandscapePreview) {
                    base.copy(
                        modesThicknessDp = live.overrideWidthDp ?: base.modesThicknessDp,
                        modesLengthDp = live.overrideHeightDp ?: base.modesLengthDp,
                        modesLandscapeOffsetPercent = ((live.overrideOffsetPercent ?: (base.modesLandscapeOffsetPercent * 100f)) / 100f).coerceIn(0f, 1f)
                    )
                } else {
                    base.copy(
                        modesThicknessDp = live.overrideWidthDp ?: base.modesThicknessDp,
                        modesLengthDp = live.overrideHeightDp ?: base.modesLengthDp,
                        modesPortraitOffsetPercent = ((live.overrideOffsetPercent ?: (base.modesPortraitOffsetPercent * 100f)) / 100f).coerceIn(0f, 1f)
                    )
                }
                else -> base
            }
        }.stateIn(serviceScope, SharingStarted.Eagerly, settingsRepository.settings.value)

        effectivePreviewTargetState = combine(
            settingsRepository.previewTarget,
            LivePreviewController.state
        ) { base, live ->
            if (live.activeTarget != PreviewTarget.NONE) live.activeTarget else base
        }.stateIn(serviceScope, SharingStarted.Eagerly, settingsRepository.previewTarget.value)

        volumeController = VolumeController(this)
        fineVolumeController = FineVolumeController(this, audioManager)
        appVolumeController = AppVolumeController(this, audioManager, fineVolumeController)
        fineVolumeController.verifyAndResolveEngineMode()
        appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)

        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) }
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener) }

        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL
        )
        streams.forEach { st ->
            val currentSteps = fineVolumeController.getStreamCurrentSteps(st)
            setStoredStepsForStream(st, currentSteps)
        }
        val maxLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val initialMusicSteps = getStoredStepsForStream(AudioManager.STREAM_MUSIC)
        volumeFraction.value = (initialMusicSteps.toFloat() / maxLimit.toFloat()).coerceIn(0f, 1f)
        isScreenInteractive = runCatching { powerManager.isInteractive }.getOrDefault(true)

        createRoutinesNotificationChannel()

        val audioBgHandler = ensureAudioExecutor()
        audioScope?.launch {
            registerCameraAndAudioCallbacks(audioBgHandler)
            volumeFraction.value = volumeController.getMusicVolumeFraction()
        }

        val initialDisplay = readDisplayRotationAndSize()
        rotationState.value = initialDisplay.first
        screenSizeState.value = initialDisplay.second

        displayManager.registerDisplayListener(
            displayListener,
            Handler(Looper.getMainLooper())
        )

        lifecycleOwner.performCreate()
        setupOverlayWindows()
        lifecycleOwner.onAttached()

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, volumeFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(powerScreenReceiver, powerFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, volumeFilter)
            registerReceiver(powerScreenReceiver, powerFilter)
        }

        observeOverlayLayout()

        serviceScope.launch {
            effectiveSettingsState.collect { s ->
                isVolumeSliderEnabledFast = s.enableVolumeSliderHud
                isCurrentAppExcludedFast = s.isHideAccessibilityEnabled && s.cloakedPackages.contains(currentForegroundPackage)
                cachedEdgePopTimeoutMs = s.popAutoHideMs.toLong()
            }
        }

        serviceScope.launch {
            ShizukuManager.shizukuState.collect {
                fineVolumeController.verifyAndResolveEngineMode()
            }
        }

        serviceScope.launch {
            while (isActive) {
                val isOverlayActive = hudActive.value || modalActive.value || (effectivePreviewTargetState.value != PreviewTarget.NONE)
                if (isOverlayActive && effectiveSettingsState.value.isPerAppVolumeEnabled) {
                    appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
                }
                delay(500L)
            }
        }

        serviceScope.launch {
            hardwareTriggerEvents.collect { trigger ->
                handleHardwareTrigger(trigger)
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(200L)
                val now = SystemClock.uptimeMillis()
                val timeoutThreshold = cachedEdgePopTimeoutMs.coerceIn(50L, 5000L) + 200L
                if (upPopActive.value && (now - lastUpKeyEventTimestamp > timeoutThreshold) && !swipeTouchActive) {
                    volUpIsDown = false
                    isButtonPhysicallyHeld[ButtonType.VOL_UP] = false
                    upPopActive.value = false
                }
                if (downPopActive.value && (now - lastDownKeyEventTimestamp > timeoutThreshold) && !swipeTouchActive) {
                    volDownIsDown = false
                    isButtonPhysicallyHeld[ButtonType.VOL_DOWN] = false
                    downPopActive.value = false
                }
                if (powerPopActive.value && (now - lastPowerKeyEventTimestamp > timeoutThreshold) && !swipeTouchActive) {
                    powerIsDown = false
                    isButtonPhysicallyHeld[ButtonType.POWER] = false
                    powerPopActive.value = false
                }
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                val settings = effectiveSettingsState.value
                if (settings.isShizukuWatchdogEnabled && ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()) {
                    ShizukuManager.grantAllPermissionsViaShizuku(this@NudgeAccessibilityService)
                }
            }
        }
    }

    private fun createRoutinesNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ROUTINES_BRIDGE_CHANNEL_ID,
                "Nudge Routines Bridge",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                description = "Silent bridge trigger for Samsung Modes and Routines"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val pkg = event.packageName?.toString()
            val className = event.className?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                if (className != "android.widget.Toast") {
                    if (pkg != "com.android.systemui" &&
                        pkg != "android" &&
                        !pkg.contains("systemui") &&
                        !pkg.contains("inputmethod") &&
                        !pkg.contains("honeyboard")
                    ) {
                        currentForegroundPackage = pkg
                        if (::appVolumeController.isInitialized) {
                            appVolumeController.currentForegroundPackage = pkg
                            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
                        }
                    }
                    val settings = effectiveSettingsState.value
                    isCurrentAppExcludedFast = settings.isHideAccessibilityEnabled && settings.cloakedPackages.contains(pkg)
                    NudgeKeepAliveService.reportPackageChanged(this, pkg)
                }
                isScreenInteractive = true
                if (modalActive.value &&
                    className != "android.widget.Toast" &&
                    pkg != "com.android.systemui" &&
                    pkg != "android" &&
                    !pkg.contains("systemui") &&
                    (SystemClock.uptimeMillis() - modalOpenedTimeMs) > 150L
                ) {
                    dismissModalOverlay()
                }
            }
        }
    }

    fun clearAllEdgePops() {
        upPopActive.value = false
        downPopActive.value = false
        powerPopActive.value = false
        upRetractJob?.cancel()
        downRetractJob?.cancel()
        powerRetractJob?.cancel()
        popWatchdogJob?.cancel()
    }

    private fun resetPopWatchdog() {
        popWatchdogJob?.cancel()
        popWatchdogJob = serviceScope.launch {
            delay(2500L)
            if (!swipeTouchActive && !keyHeldDown && !volUpIsDown && !volDownIsDown && !powerIsDown) {
                upPopActive.value = false
                downPopActive.value = false
                powerPopActive.value = false
            }
        }
    }

    fun triggerPowerPopForHold() {
        val currentSettings = effectiveSettingsState.value
        val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
        if (currentSettings.isEdgePopMasterEnabled && currentSettings.enablePowerButtonPop &&
            EdgeOrientationMapper.shouldShowEdgePop(isLandscape, currentSettings)
        ) {
            triggerPowerPop(holdMs = 600L, settings = currentSettings)
            resetPopWatchdog()
        }
    }

    private fun isTriggerAssigned(trigger: HardwareTrigger, settings: NudgeSettings): Boolean {
        if (trigger == HardwareTrigger.NONE) return false
        return (settings.enableDndToggle && settings.dndTrigger == trigger) ||
            (settings.enableActionsPanel && settings.modesTrigger == trigger)
    }

    override fun onInterrupt() {
        clearAllEdgePops()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handleDisplayChange()
    }

    private fun markButtonReleasedAndHideEdgePop(keyCode: Int, settings: NudgeSettings) {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volUpIsDown = false
                pendingVolUpReleased = true
                cancelHoldRepeatLoop(isUp = true)
                schedulePopRetract(isUp = true, settings = settings)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volDownIsDown = false
                pendingVolDownReleased = true
                cancelHoldRepeatLoop(isUp = false)
                schedulePopRetract(isUp = false, settings = settings)
            }
            KeyEvent.KEYCODE_POWER -> {
                powerIsDown = false
                powerRetractJob?.cancel()
                powerPopActive.value = false
            }
            else -> {
                if (keyCode == settings.customButtonKeyCode) {
                    customBtnDown = false
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        if (!::settingsRepository.isInitialized || !::volumeController.isInitialized) {
            return super.onKeyEvent(event)
        }

        updateEdgePopForHardwareEvent(event)

        val keyCode = event.keyCode

        val currentSettings = effectiveSettingsState.value
        val now = SystemClock.uptimeMillis()
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) lastUpKeyEventTimestamp = now
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) lastDownKeyEventTimestamp = now
        if (keyCode == KeyEvent.KEYCODE_POWER) lastPowerKeyEventTimestamp = now

        val isReleaseAction = event.action == KeyEvent.ACTION_UP || (event.flags and KeyEvent.FLAG_CANCELED) != 0
        if (isReleaseAction) {
            markButtonReleasedAndHideEdgePop(keyCode, currentSettings)
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (::fineVolumeController.isInitialized) {
                    fineVolumeController.verifyAndResolveEngineMode()
                }
                if (::appVolumeController.isInitialized) {
                    appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (!isVolumeSliderEnabledFast || isCurrentAppExcludedFast) {
                return super.onKeyEvent(event)
            }
        }

        val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)

        if (isScanningCustomButton && event.action == KeyEvent.ACTION_DOWN) {
            val code = event.keyCode
            val scancode = event.scanCode
            val label = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            isScanningCustomButton = false
            onCustomButtonScanned?.invoke(code, scancode, label)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (modesActive.value) {
                            consumedBackKeyDown = true
                            dismissActionsPanel()
                            return true
                        }
                        if (dndActive.value) {
                            consumedBackKeyDown = true
                            dismissDndOverlay()
                            return true
                        }
                        if (modalActive.value) {
                            consumedBackKeyDown = true
                            dismissModalOverlay()
                            return true
                        }
                    }
                    if (event.repeatCount > 0 && consumedBackKeyDown) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (consumedBackKeyDown) {
                        consumedBackKeyDown = false
                        return true
                    }
                }
            }
            return super.onKeyEvent(event)
        }

        if (keyCode == KeyEvent.KEYCODE_POWER) {
            val now = SystemClock.uptimeMillis()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    powerIsDown = true
                    lastPowerDownTimeMs = now
                    if (event.repeatCount == 0) {
                        if (modesActive.value) dismissActionsPanel()
                        if (dndActive.value) dismissDndOverlay()
                        if (modalActive.value) dismissModalOverlay()

                        if (event.isLongPress) {
                            handleHardwareTrigger(HardwareTrigger.LONG_PRESS_POWER)
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    powerIsDown = false
                }
            }
            return super.onKeyEvent(event)
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val activeStreamEnum = resolveActiveVolumeStream()
        val activeStream = when (activeStreamEnum) {
            ActiveVolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
            ActiveVolumeStream.RING -> AudioManager.STREAM_RING
            ActiveVolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        }
                val cur = volumeController.getStreamVolume(activeStream)
                if (cur > 0) {
                    volumeController.setStreamIndex(activeStream, 0, showSystemUi = false)
                    volumeFraction.value = 0f
                } else {
                    val max = volumeController.getStreamMax(activeStream)
                    val target = (max * 0.35f).roundToInt().coerceAtLeast(1)
                    volumeController.setStreamIndex(activeStream, target, showSystemUi = false)
                    volumeFraction.value = target.toFloat() / max.toFloat()
                }
            }
            return true
        }

        if (currentSettings.customButtonKeyCode != 0 && keyCode == currentSettings.customButtonKeyCode) {
            val now = SystemClock.uptimeMillis()
            val customLongAssigned = isTriggerAssigned(HardwareTrigger.CUSTOM_BTN_LONG, currentSettings)
            val customDoubleAssigned = isTriggerAssigned(HardwareTrigger.CUSTOM_BTN_DOUBLE, currentSettings)
            val customSingleAssigned = isTriggerAssigned(HardwareTrigger.CUSTOM_BTN_SINGLE, currentSettings)

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        customBtnDown = true
                        customBtnDownTimeMs = now
                        customBtnLongFired = false

                        if (customLongAssigned) {
                            customBtnLongJob?.cancel()
                            customBtnLongJob = serviceScope.launch {
                                delay(450L)
                                if (customBtnDown) {
                                    customBtnLongFired = true
                                    handleHardwareTrigger(HardwareTrigger.CUSTOM_BTN_LONG)
                                }
                            }
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    customBtnDown = false
                    customBtnLongJob?.cancel()
                    if (!customBtnLongFired) {
                        val duration = now - customBtnDownTimeMs
                        if (duration < 450L) {
                            if (customDoubleAssigned) {
                                val elapsed = now - lastCustomBtnTapTimeMs
                                if (elapsed <= 300L) {
                                    customBtnSinglePendingJob?.cancel()
                                    customBtnSinglePendingJob = null
                                    handleHardwareTrigger(HardwareTrigger.CUSTOM_BTN_DOUBLE)
                                } else {
                                    lastCustomBtnTapTimeMs = now
                                    customBtnSinglePendingJob?.cancel()
                                    customBtnSinglePendingJob = serviceScope.launch {
                                        delay(300L)
                                        if (customSingleAssigned) {
                                            handleHardwareTrigger(HardwareTrigger.CUSTOM_BTN_SINGLE)
                                        }
                                    }
                                }
                            } else if (customSingleAssigned) {
                                handleHardwareTrigger(HardwareTrigger.CUSTOM_BTN_SINGLE)
                            }
                        }
                    }
                }
            }
            return true
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        val isUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (modesActive.value) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        stepModeSelection(up = isUp)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> return true
            }
            return true
        }

        if (dndActive.value) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        setDndState(active = isUp)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> return true
            }
            return true
        }

        val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, currentSettings)
        val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, currentSettings)

        val isChordAssigned = isTriggerAssigned(HardwareTrigger.BOTH_VOLUME_SHORT, currentSettings)

        val doubleTapTrigger = if (isUp) HardwareTrigger.DOUBLE_TAP_VOL_UP else HardwareTrigger.DOUBLE_TAP_VOL_DOWN
        val isDoubleTapAssigned = isTriggerAssigned(doubleTapTrigger, currentSettings)

        val hudIsActive = hudActive.value || modalActive.value

        val configuredDoubleTapTimeoutMs = when {
            currentSettings.enableDndToggle && currentSettings.dndTrigger == doubleTapTrigger -> currentSettings.dndDoubleTapTimeoutMs
            currentSettings.enableActionsPanel && currentSettings.modesTrigger == doubleTapTrigger -> currentSettings.modesDoubleTapTimeoutMs
            else -> 250L
        }
        val chordBufferMs = if (isChordAssigned) currentSettings.simultaneousPressTimeoutMs else 0L
        val doubleTapBufferMs = if (isDoubleTapAssigned) configuredDoubleTapTimeoutMs else 0L
        val waitWindowMs = maxOf(chordBufferMs, doubleTapBufferMs)

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) {
                    val wasConsumed = if (isUp) consumedUpKeyDown else consumedDownKeyDown
                    return if (wasConsumed) true else super.onKeyEvent(event)
                }

                if (isUp) {
                    volUpIsDown = true
                    lastVolUpPressTimeMs = now
                } else {
                    volDownIsDown = true
                    lastVolDownPressTimeMs = now
                }

                if (isChordAssigned) {
                    val otherDownTime = if (isUp) lastVolDownPressTimeMs else lastVolUpPressTimeMs
                    val otherIsDown = if (isUp) volDownIsDown else volUpIsDown
                    if (otherIsDown && abs(now - otherDownTime) <= maxOf(currentSettings.simultaneousPressTimeoutMs, 145L)) {
                        pendingVolUpJob?.cancel(); pendingVolUpJob = null
                        pendingVolDownJob?.cancel(); pendingVolDownJob = null
                        cancelHoldRepeatLoop(isUp = true)
                        cancelHoldRepeatLoop(isUp = false)
                        keyHeldDown = false
                        chordCandidateStartTimeMs = minOf(now, otherDownTime)
                        chordFired = true

                        if (currentSettings.isEdgePopMasterEnabled && popAllowed) {
                            if (!doesVisiblePanelOverlapEdgePop(EdgePopType.VOL_UP)) upPopActive.value = true
                            if (!doesVisiblePanelOverlapEdgePop(EdgePopType.VOL_DOWN)) downPopActive.value = true
                        }
                        resetPopWatchdog()
                        return true
                    }
                }

                if (hudIsActive) {
                    return performImmediateVolumeDown(isUp, currentSettings, isLandscape, popAllowed, hudAllowed, downTimeMs = now)
                }

                if (waitWindowMs > 0L) {
                    val pendingJob = if (isUp) pendingVolUpJob else pendingVolDownJob
                    if (isDoubleTapAssigned && pendingJob != null && pendingJob.isActive) {
                        pendingJob.cancel()
                        if (isUp) pendingVolUpJob = null else pendingVolDownJob = null
                        handleHardwareTrigger(doubleTapTrigger)
                        return true
                    }

                    pendingJob?.cancel()

                    keyHeldDown = true
                    val sequence = holdSequence.incrementAndGet()
                    if (isUp) consumedUpKeyDown = true else consumedDownKeyDown = true
                    if (isUp) pendingVolUpReleased = false else pendingVolDownReleased = false

                    val activeStreamEnum = resolveActiveVolumeStream()
        val activeStream = when (activeStreamEnum) {
            ActiveVolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
            ActiveVolumeStream.RING -> AudioManager.STREAM_RING
            ActiveVolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        }
                    val job = serviceScope.launch {
                        delay(waitWindowMs)
                        val wasReleased = if (isUp) pendingVolUpReleased else pendingVolDownReleased
                        if (wasReleased) {
                            audioScope?.launch {
                                applyAudioVolumeStep(
                                    streamType = activeStream,
                                    direction = if (isUp) 1 else -1,
                                    settings = currentSettings,
                                    showSystemUi = false,
                                    initialPress = true,
                                    stepMultiplier = 1
                                )
                            }
                            if (hudAllowed) {
                                handleHudTrigger(settings = currentSettings, initialPress = true)
                            }
                        } else if (keyHeldDown && holdSequence.get() == sequence) {
                            startAudioStepAndHoldLoop(
                                isUp = isUp,
                                direction = if (isUp) 1 else -1,
                                sequence = sequence,
                                streamType = activeStream,
                                downTimeMs = now,
                                applyInitialStep = true
                            )
                            if (hudAllowed) {
                                handleHudTrigger(settings = currentSettings, initialPress = true)
                            }
                        }
                    }
                    if (isUp) pendingVolUpJob = job else pendingVolDownJob = job
                    return true
                }

                return performImmediateVolumeDown(isUp, currentSettings, isLandscape, popAllowed, hudAllowed, downTimeMs = now)
            }

            KeyEvent.ACTION_MULTIPLE -> {
                val wasConsumed = if (isUp) consumedUpKeyDown else consumedDownKeyDown
                return if (wasConsumed) true else super.onKeyEvent(event)
            }

            KeyEvent.ACTION_UP -> {
                if (isUp) volUpIsDown = false else volDownIsDown = false

                val isCanceled = (event.flags and KeyEvent.FLAG_CANCELED) != 0
                val downTime = if (isUp) lastVolUpPressTimeMs else lastVolDownPressTimeMs
                val pressDuration = now - downTime

                if (chordFired) {
                    val duration = now - chordCandidateStartTimeMs
                    chordFired = false
                    if (isChordAssigned && duration <= 400L) {
                        handleHardwareTrigger(HardwareTrigger.BOTH_VOLUME_SHORT)
                    }
                    if (popAllowed) {
                        serviceScope.launch(Dispatchers.Main) {
                            schedulePopRetract(isUp = true, settings = currentSettings)
                            schedulePopRetract(isUp = false, settings = currentSettings)
                        }
                    }
                    return true
                }

                if (isUp) lastVolUpReleaseTimeMs = now else lastVolDownReleaseTimeMs = now

                if (waitWindowMs > 0L) {
                    val pendingJob = if (isUp) pendingVolUpJob else pendingVolDownJob
                    if (pendingJob != null && pendingJob.isActive) {
                        if (isUp) pendingVolUpReleased = true else pendingVolDownReleased = true
                        return true
                    }
                }

                keyHeldDown = false
                holdSequence.incrementAndGet()
                cancelHoldRepeatLoop(isUp = true)
                cancelHoldRepeatLoop(isUp = false)
                if (isUp) consumedUpKeyDown = false else consumedDownKeyDown = false
                if (popAllowed) {
                    serviceScope.launch(Dispatchers.Main) {
                        schedulePopRetract(isUp = isUp, settings = currentSettings)
                    }
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun performImmediateVolumeDown(
        isUp: Boolean,
        currentSettings: NudgeSettings,
        isLandscape: Boolean,
        popAllowed: Boolean,
        hudAllowed: Boolean,
        downTimeMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        if (shouldBypassVolumeInterception(currentSettings, isLandscape)) {
            if (isUp) consumedUpKeyDown = false else consumedDownKeyDown = false
            cancelHoldRepeatLoop(isUp = isUp)
            return false
        }

        val sequence = holdSequence.incrementAndGet()
        if (isUp) {
            consumedUpKeyDown = true
            cancelHoldRepeatLoop(isUp = false)
        } else {
            consumedDownKeyDown = true
            cancelHoldRepeatLoop(isUp = true)
        }
        keyHeldDown = true

        val activeStreamEnum = resolveActiveVolumeStream()
        val activeStream = when (activeStreamEnum) {
            ActiveVolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
            ActiveVolumeStream.RING -> AudioManager.STREAM_RING
            ActiveVolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        }
        startAudioStepAndHoldLoop(
            isUp = isUp,
            direction = if (isUp) 1 else -1,
            sequence = sequence,
            streamType = activeStream,
            downTimeMs = downTimeMs,
            applyInitialStep = true
        )
        if (hudAllowed) {
            serviceScope.launch(Dispatchers.Main) {
                handleHudTrigger(settings = currentSettings, initialPress = true)
            }
        }
        return true
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

    private fun resolveActiveBezelEdge(settings: NudgeSettings, isDnd: Boolean): ScreenEdge {
        val isRealLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
        val isPreviewLandscape = settingsRepository.previewLandscape.value || LivePreviewController.state.value.isLandscapePreview
        return if (isRealLandscape) {
            val bezelEdge = if (isDnd) settings.dndLandscapeEdge else settings.modesLandscapeEdge
            bezelEdge.toScreenEdge()
        } else if (isPreviewLandscape) {
            val bezelEdge = if (isDnd) settings.dndLandscapeEdge else settings.modesLandscapeEdge
            mapLandscapeEdgeToPhysicalPortrait(bezelEdge.toScreenEdge())
        } else {
            val bezelEdge = if (isDnd) settings.dndPortraitEdge else settings.modesPortraitEdge
            bezelEdge.toScreenEdge()
        }
    }

    private fun resolveActiveBezelOffset(settings: NudgeSettings, isDnd: Boolean): Float {
        val isRealLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
        val isPreviewLandscape = settingsRepository.previewLandscape.value || LivePreviewController.state.value.isLandscapePreview
        return if (isRealLandscape) {
            if (isDnd) settings.dndLandscapeOffsetPercent else settings.modesLandscapeOffsetPercent
        } else if (isPreviewLandscape) {
            val rawEdge = if (isDnd) settings.dndLandscapeEdge.toScreenEdge() else settings.modesLandscapeEdge.toScreenEdge()
            val rawOffset = if (isDnd) settings.dndLandscapeOffsetPercent else settings.modesLandscapeOffsetPercent
            mapLandscapeOffsetToPhysicalPortrait(rawEdge, rawOffset)
        } else {
            if (isDnd) settings.dndPortraitOffsetPercent else settings.modesPortraitOffsetPercent
        }
    }

    private fun resolveActiveHudEdge(settings: NudgeSettings, rotation: Int): ScreenEdge {
        val isRealLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
        val isPreviewLandscape = settingsRepository.previewLandscape.value || LivePreviewController.state.value.isLandscapePreview
        return if (isRealLandscape) {
            settings.landscapeHudEdgeSide
        } else if (isPreviewLandscape) {
            mapLandscapeEdgeToPhysicalPortrait(settings.landscapeHudEdgeSide)
        } else {
            EdgeOrientationMapper.resolvePhysicalEdge(settings.effectiveHudEdgeSide, rotation)
        }
    }

    private fun computeActiveHudTopOrLeftPx(
        settings: NudgeSettings,
        rotation: Int,
        axisLengthPx: Float,
        elementSpanPx: Int
    ): Int {
        val isRealLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
        val isPreviewLandscape = settingsRepository.previewLandscape.value || LivePreviewController.state.value.isLandscapePreview
        val fraction = if (isRealLandscape) {
            settings.landscapeHudYFraction.coerceIn(0f, 1f)
        } else if (isPreviewLandscape) {
            mapLandscapeOffsetToPhysicalPortrait(settings.landscapeHudEdgeSide, settings.landscapeHudYFraction)
        } else {
            EdgeOrientationMapper.resolveAxisFraction(settings.hudYFraction, rotation)
        }
        val centerPx = (fraction * axisLengthPx).coerceIn(0f, axisLengthPx).roundToInt()
        val maxOffset = (axisLengthPx.roundToInt() - elementSpanPx).coerceAtLeast(0)
        return (centerPx - elementSpanPx / 2).coerceIn(0, maxOffset)
    }

    fun doesVisiblePanelOverlapEdgePop(buttonType: EdgePopType): Boolean {
        val isVolumeSliderVisible = hudActive.value || effectivePreviewTargetState.value == PreviewTarget.HUD || effectivePreviewTargetState.value == PreviewTarget.VOLUME_SLIDER
        val isDndVisible = dndActive.value || effectivePreviewTargetState.value == PreviewTarget.DND || effectivePreviewTargetState.value == PreviewTarget.DND_PANEL
        val isModesVisible = modesActive.value || effectivePreviewTargetState.value == PreviewTarget.MODES || effectivePreviewTargetState.value == PreviewTarget.ACTIONS_PANEL

        if (!isVolumeSliderVisible && !isDndVisible && !isModesVisible) {
            return false
        }

        val settings = effectiveSettingsState.value
        val rotation = rotationState.value
        val popEdge = EdgeOrientationMapper.resolvePhysicalEdge(settings.popEdgeSide, rotation)
        val isVertical = popEdge == ScreenEdge.LEFT || popEdge == ScreenEdge.RIGHT
        val (screenW, screenH) = screenSizeState.value
        val axisLengthPx = if (isVertical) screenH else screenW

        val fraction = when (buttonType) {
            EdgePopType.VOL_UP -> settings.volumeUpYFraction
            EdgePopType.VOL_DOWN -> settings.volumeDownYFraction
            EdgePopType.POWER -> settings.powerButtonYFraction
        }
        val popCenterCoordPx = EdgeOrientationMapper.computeElementCenterPx(fraction, rotation, axisLengthPx.toFloat())
        val popPillHeightDp = if (buttonType == EdgePopType.POWER) settings.powerPopHeightDp else settings.popHeightDp
        val popPillHeightPx = dpToPx(popPillHeightDp).toFloat()
        val popHalfSpan = (popPillHeightPx / 2f) * 0.85f

        if (isVolumeSliderVisible) {
            val sliderEdge = resolveActiveHudEdge(settings, rotation)
            val withinBezel = !settings.floatingMode || settings.hudEdgeInsetDp <= 10f
            if (sliderEdge == popEdge && withinBezel) {
                val hudFraction = if (EdgeOrientationMapper.isLandscapeRotation(rotation) || settingsRepository.previewLandscape.value) {
                    settings.landscapeHudYFraction.coerceIn(0f, 1f)
                } else {
                    EdgeOrientationMapper.resolveAxisFraction(settings.hudYFraction, rotation)
                }
                val panelCenterCoordPx = (hudFraction * axisLengthPx).coerceIn(0f, axisLengthPx.toFloat())
                val panelPillHeightPx = dpToPx(settings.hudHeightDp).toFloat()
                val panelHalfSpan = (panelPillHeightPx / 2f) * 0.90f
                if (kotlin.math.abs(panelCenterCoordPx - popCenterCoordPx) < (popHalfSpan + panelHalfSpan)) {
                    return true
                }
            }
        }

        if (isDndVisible) {
            val dndEdge = resolveActiveBezelEdge(settings, isDnd = true)
            if (dndEdge == popEdge) {
                val offsetPercent = resolveActiveBezelOffset(settings, isDnd = true)
                val panelCenterCoordPx = (offsetPercent * axisLengthPx).coerceIn(0f, axisLengthPx.toFloat())
                val panelPillHeightPx = dpToPx(settings.dndLengthDp).toFloat()
                val panelHalfSpan = (panelPillHeightPx / 2f) * 0.90f
                if (kotlin.math.abs(panelCenterCoordPx - popCenterCoordPx) < (popHalfSpan + panelHalfSpan)) {
                    return true
                }
            }
        }

        if (isModesVisible) {
            val modesEdge = resolveActiveBezelEdge(settings, isDnd = false)
            if (modesEdge == popEdge) {
                val offsetPercent = resolveActiveBezelOffset(settings, isDnd = false)
                val panelCenterCoordPx = (offsetPercent * axisLengthPx).coerceIn(0f, axisLengthPx.toFloat())
                val panelPillHeightPx = dpToPx(settings.modesLengthDp).toFloat()
                val panelHalfSpan = (panelPillHeightPx / 2f) * 0.90f
                if (kotlin.math.abs(panelCenterCoordPx - popCenterCoordPx) < (popHalfSpan + panelHalfSpan)) {
                    return true
                }
            }
        }

        return false
    }

    fun doesVolumeSliderOverlapEdgePop(buttonType: EdgePopType): Boolean {
        return doesVisiblePanelOverlapEdgePop(buttonType)
    }

    private fun doesPanelOverlapEdgePop(buttonType: EdgePopType): Boolean {
        return doesVisiblePanelOverlapEdgePop(buttonType)
    }

    private fun dismissOverlappingEdgePops() {
        if (doesVisiblePanelOverlapEdgePop(EdgePopType.VOL_UP)) {
            upPopActive.value = false
            upRetractJob?.cancel()
        }
        if (doesVisiblePanelOverlapEdgePop(EdgePopType.VOL_DOWN)) {
            downPopActive.value = false
            downRetractJob?.cancel()
        }
        if (doesVisiblePanelOverlapEdgePop(EdgePopType.POWER)) {
            powerPopActive.value = false
            powerRetractJob?.cancel()
        }
    }

    private fun tryTriggerEdgePopOnHardwareClick(isUp: Boolean, settings: NudgeSettings, isLandscape: Boolean) {
        if (!settings.isEdgePopMasterEnabled) return
        val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
        val popType = if (isUp) EdgePopType.VOL_UP else EdgePopType.VOL_DOWN
        if (popAllowed && !doesVisiblePanelOverlapEdgePop(popType)) {
            serviceScope.launch(Dispatchers.Main) {
                triggerVolumePop(isUp = isUp, settings = settings, autoRetract = false)
            }
        }
    }

    private fun getEdgePopButtonBounds(buttonType: EdgePopType, settings: NudgeSettings, rotation: Int): Pair<Int, Int> {
        val popEdge = EdgeOrientationMapper.resolvePhysicalEdge(settings.popEdgeSide, rotation)
        val isVertical = popEdge == ScreenEdge.LEFT || popEdge == ScreenEdge.RIGHT
        val (screenW, screenH) = screenSizeState.value
        val axisLengthPx = if (isVertical) screenH else screenW

        val fraction = when (buttonType) {
            EdgePopType.VOL_UP -> settings.volumeUpYFraction
            EdgePopType.VOL_DOWN -> settings.volumeDownYFraction
            EdgePopType.POWER -> settings.powerButtonYFraction
        }
        val spanDp = when (buttonType) {
            EdgePopType.VOL_UP, EdgePopType.VOL_DOWN -> settings.popHeightDp
            EdgePopType.POWER -> settings.powerPopHeightDp
        }
        val centerPx = EdgeOrientationMapper.computeElementCenterPx(fraction, rotation, axisLengthPx.toFloat()).roundToInt()
        val spanPx = dpToPx(spanDp)
        val startPx = centerPx - spanPx / 2
        val endPx = centerPx + spanPx / 2
        return Pair(startPx, endPx)
    }

    private fun handleHardwareTrigger(trigger: HardwareTrigger) {
        val settings = effectiveSettingsState.value
        when {
            settings.enableDndToggle && settings.dndTrigger == trigger -> {
                executeDndToggle()
                serviceScope.launch {
                    delay(220L)
                    val now = SystemClock.uptimeMillis()
                    if ((now - lastUpKeyEventTimestamp > 200L || !volUpIsDown) &&
                        (now - lastDownKeyEventTimestamp > 200L || !volDownIsDown) &&
                        (now - lastPowerKeyEventTimestamp > 200L || !powerIsDown)) {
                        upPopActive.value = false
                        downPopActive.value = false
                        powerPopActive.value = false
                    }
                }
            }
            settings.enableActionsPanel && settings.modesTrigger == trigger -> {
                showActionsPanel()
                serviceScope.launch {
                    delay(220L)
                    val now = SystemClock.uptimeMillis()
                    if ((now - lastUpKeyEventTimestamp > 200L || !volUpIsDown) &&
                        (now - lastDownKeyEventTimestamp > 200L || !volDownIsDown) &&
                        (now - lastPowerKeyEventTimestamp > 200L || !powerIsDown)) {
                        upPopActive.value = false
                        downPopActive.value = false
                        powerPopActive.value = false
                    }
                }
            }
        }
    }

    private fun setDndState(active: Boolean) {
        val settings = effectiveSettingsState.value
        val hasPolicyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }

        when (settings.dndModeType) {
            DndModeType.SILENT_RING -> {
                val targetRinger = if (active) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                runCatching {
                    audioManager.ringerMode = targetRinger
                }
            }
            DndModeType.DND_ON_OFF -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasPolicyAccess) {
                    val targetFilter = if (active) {
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                    runCatching {
                        notificationManager.setInterruptionFilter(targetFilter)
                    }
                } else {
                    val targetRinger = if (active) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                    runCatching {
                        audioManager.ringerMode = targetRinger
                    }
                }
            }
        }

        dndState.value = active
        showDndOverlay(settings.dndTimeoutMs)
        if (settings.dndPanelHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = true) }
        }
    }

    private fun executeDndToggle() {
        val settings = effectiveSettingsState.value
        val hasPolicyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }

        val targetActive: Boolean = when (settings.dndModeType) {
            DndModeType.SILENT_RING -> {
                val liveRinger = audioManager.ringerMode
                if (liveRinger == AudioManager.RINGER_MODE_NORMAL) {
                    runCatching {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    }
                    true
                } else {
                    runCatching {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                    false
                }
            }
            DndModeType.DND_ON_OFF -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasPolicyAccess) {
                    val currentFilter = notificationManager.currentInterruptionFilter
                    if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        runCatching {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        }
                        true
                    } else {
                        runCatching {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                        false
                    }
                } else {
                    val liveRinger = audioManager.ringerMode
                    if (liveRinger == AudioManager.RINGER_MODE_NORMAL) {
                        runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT }
                        true
                    } else {
                        runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                        false
                    }
                }
            }
        }

        dndState.value = targetActive
        showDndOverlay(settings.dndTimeoutMs)
        if (settings.dndPanelHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = true) }
        }
    }

    private fun showDndOverlay(timeoutMs: Long) {
        dismissOverlappingEdgePops()
        dndHideJob?.cancel()
        dndView?.visibility = View.VISIBLE
        dndActive.value = true
        resetDndTimeout(timeoutMs)
    }

    private fun resetDndTimeout(timeoutMs: Long) {
        dndHideJob?.cancel()
        dndHideJob = serviceScope.launch {
            delay(timeoutMs)
            dismissDndOverlay()
        }
    }

    private fun dismissDndOverlay() {
        dndHideJob?.cancel()
        dndActive.value = false
        dndGoneJob?.cancel()
        dndGoneJob = serviceScope.launch {
            delay(220L)
            if (!dndActive.value && effectivePreviewTargetState.value != PreviewTarget.DND && effectivePreviewTargetState.value != PreviewTarget.DND_PANEL) {
                dndView?.visibility = View.GONE
            }
        }
    }

    private fun pauseDndAutoHideTimer() {
        dndHideJob?.cancel()
    }

    private fun restartDndAutoHideTimer() {
        dndHideJob?.cancel()
        if (effectivePreviewTargetState.value == PreviewTarget.DND || effectivePreviewTargetState.value == PreviewTarget.DND_PANEL) {
            return
        }
        val timeoutMs = effectiveSettingsState.value.dndTimeoutMs
        dndHideJob = serviceScope.launch {
            delay(timeoutMs)
            dismissDndOverlay()
        }
    }

    private fun pauseActionsAutoHideTimer() {
        modesHideJob?.cancel()
    }

    private fun restartActionsAutoHideTimer() {
        modesHideJob?.cancel()
        if (effectivePreviewTargetState.value == PreviewTarget.MODES || effectivePreviewTargetState.value == PreviewTarget.ACTIONS_PANEL) {
            return
        }
        val timeoutMs = effectiveSettingsState.value.modesTimeoutMs
        modesHideJob = serviceScope.launch {
            delay(timeoutMs)
            lockInCurrentMode()
        }
    }

    private fun showAudioMixerModal() {
        if (::fineVolumeController.isInitialized) {
            fineVolumeController.verifyAndResolveEngineMode()
        }
        if (::appVolumeController.isInitialized) {
            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
        }
        hudHideJob?.cancel()
        modalOpenedTimeMs = SystemClock.uptimeMillis()
        modalActive.value = true
    }

    fun showActionsPanel() {
        dismissOverlappingEdgePops()
        val settings = effectiveSettingsState.value
        val modes = settings.customModes.ifEmpty { DEFAULT_CUSTOM_MODES }
        val initialModeId = detectCurrentCustomMode(modes)
        modesSelectedModeId.value = initialModeId
        modesActive.value = true
        modesView?.visibility = View.VISIBLE
        resetModesTimeout(settings.modesTimeoutMs)
        if (settings.actionsPanelHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = true) }
        }
    }

    private fun showModesCarousel() = showActionsPanel()

    private fun detectCurrentCustomMode(modes: List<CustomMode>): String {
        val ringer = audioManager.ringerMode
        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.currentInterruptionFilter
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        for (m in modes) {
            if (filter == NotificationManager.INTERRUPTION_FILTER_ALARMS && m.dndMode == "ALARMS") return m.id
            if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY && m.dndMode == "PRIORITY") return m.id
            if (filter == NotificationManager.INTERRUPTION_FILTER_NONE && m.dndMode == "TOTAL_SILENCE") return m.id
            if (ringer == AudioManager.RINGER_MODE_SILENT && m.ringerMode == "SILENT") return m.id
            if (ringer == AudioManager.RINGER_MODE_VIBRATE && m.ringerMode == "VIBRATE") return m.id
        }
        return modes.firstOrNull()?.id ?: "normal"
    }

    private fun stepModeSelection(up: Boolean) {
        val modes = effectiveSettingsState.value.customModes.ifEmpty { DEFAULT_CUSTOM_MODES }
        val currentIdx = modes.indexOfFirst { it.id == modesSelectedModeId.value }.let { if (it < 0) 0 else it }
        val nextIdx = if (up) {
            (currentIdx - 1 + modes.size) % modes.size
        } else {
            (currentIdx + 1) % modes.size
        }
        modesSelectedModeId.value = modes[nextIdx].id
        actionsWheelStep.tryEmit(if (up) -1 else 1)
        val settings = effectiveSettingsState.value
        resetModesTimeout(settings.modesTimeoutMs)
        if (settings.actionsPanelHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = false) }
        }
    }

    private fun resetModesTimeout(timeoutMs: Long) {
        modesHideJob?.cancel()
        modesHideJob = serviceScope.launch {
            delay(timeoutMs)
            lockInCurrentMode()
        }
    }

    private fun lockInCurrentMode() {
        val settings = effectiveSettingsState.value
        val modes = settings.customModes.ifEmpty { DEFAULT_CUSTOM_MODES }
        val mode = modes.firstOrNull { it.id == modesSelectedModeId.value } ?: modes.first()

        if (settings.enableModesNotificationBridge) {
            emitSamsungRoutinesNotification(mode)
        }

        if (settings.enableModesBuiltInPresets) {
            applyCustomModePreset(mode)
        }

        val broadcastIntent = Intent("com.nudge.edge.MODE_CHANGED").apply {
            putExtra("mode", mode.keyword)
            putExtra("mode_id", mode.id)
            putExtra("mode_name", mode.name)
        }
        sendBroadcast(broadcastIntent)

        if (settings.actionsPanelHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = true) }
        }

        dismissActionsPanel()
    }

    private fun emitSamsungRoutinesNotification(mode: CustomMode) {
        runCatching {
            val builder = NotificationCompat.Builder(this, ROUTINES_BRIDGE_CHANNEL_ID)
                .setContentTitle("Nudge Mode")
                .setContentText(mode.keyword)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(false)
                .setAutoCancel(true)

            notificationManager.notify(ROUTINES_BRIDGE_NOTIF_ID, builder.build())

            serviceScope.launch {
                delay(300L)
                runCatching { notificationManager.cancel(ROUTINES_BRIDGE_NOTIF_ID) }
            }
        }
    }

    private fun applyCustomModePreset(mode: CustomMode) {
        when (mode.ringerMode) {
            "NORMAL" -> runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
            "VIBRATE" -> runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
            "SILENT" -> runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                when (mode.dndMode) {
                    "OFF" -> runCatching {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                    "PRIORITY" -> runCatching {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    }
                    "ALARMS" -> runCatching {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    }
                    "TOTAL_SILENCE" -> runCatching {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    }
                }
            }
        }
    }

    fun dismissActionsPanel() {
        modesHideJob?.cancel()
        modesActive.value = false
        modesGoneJob?.cancel()
        modesGoneJob = serviceScope.launch {
            delay(220L)
            if (!modesActive.value && effectivePreviewTargetState.value != PreviewTarget.MODES && effectivePreviewTargetState.value != PreviewTarget.ACTIONS_PANEL) {
                modesView?.visibility = View.GONE
            }
        }
    }

    private fun dismissModesCarousel() = dismissActionsPanel()

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceRunning.value = false
        keyHeldDown = false
        holdSequence.incrementAndGet()
        consumedUpKeyDown = false
        consumedDownKeyDown = false
        consumedBackKeyDown = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        _isServiceRunning.value = false
        keyHeldDown = false
        holdSequence.incrementAndGet()
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        runCatching { cameraManager.unregisterAvailabilityCallback(cameraAvailabilityCallback) }
        runCatching { cameraManager.unregisterTorchCallback(torchCallback) }
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener) }
        if (::volumeController.isInitialized) {
            runCatching { volumeController.unregisterAudioModeListener() }
        }
        if (::appVolumeController.isInitialized) {
            appVolumeController.release()
            appVolumeController.clear()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        }
        runCatching { unregisterReceiver(volumeReceiver) }
        runCatching { unregisterReceiver(powerScreenReceiver) }
        activeCameraIds.clear()
        torchActiveCameraIds.clear()
        audioScope?.cancel()
        audioScope = null
        audioHandler = null
        audioThread?.quitSafely()
        audioThread = null
        serviceScope.cancel()
        detachAllOverlays()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun dismissModalOverlay() {
        val wasActive = modalActive.value
        val isPreview = effectivePreviewTargetState.value == PreviewTarget.MIXER || effectivePreviewTargetState.value == PreviewTarget.AUDIO_MIXER
        if (!wasActive && !isPreview) return
        modalActive.value = false
        modalDismissedTimeMs = SystemClock.uptimeMillis()
        val settings = effectiveSettingsState.value
        if (settings.enableVolumeSliderHud) {
            hudActive.value = true
            resetHudAutoHide(settings.hudAutoHideMs.toLong())
            hudCollapseGoneJob?.cancel()
            if (hudView?.visibility != View.VISIBLE) {
                hudView?.visibility = View.VISIBLE
            }
        } else {
            hudActive.value = false
            hudHideJob?.cancel()
        }
        if (isPreview) {
            settingsRepository.stopLivePreview()
        }
    }

    private fun closeMixerAndVolumeOverlaysOnScreenOff() {
        cancelHoldRepeatLoop(isUp = true)
        cancelHoldRepeatLoop(isUp = false)
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        dndHideJob?.cancel()
        dndGoneJob?.cancel()
        modesHideJob?.cancel()
        modesGoneJob?.cancel()
        upRetractJob?.cancel()
        downRetractJob?.cancel()
        keyHeldDown = false
        consumedUpKeyDown = false
        consumedDownKeyDown = false
        consumedBackKeyDown = false
        hudScrubbing = false
        swipeTouchActive = false
        modalActive.value = false
        hudActive.value = false
        dndActive.value = false
        modesActive.value = false
        upPopActive.value = false
        downPopActive.value = false
        modalView?.visibility = View.GONE
        hudView?.visibility = View.GONE
        dndView?.visibility = View.GONE
        modesView?.visibility = View.GONE
    }

    private fun handleDisplayChange() {
        val (newRotation, newSize) = readDisplayRotationAndSize()
        val oldRotation = rotationState.value
        val oldSize = screenSizeState.value
        if (newRotation == oldRotation && newSize == oldSize) return

        cancelAllActiveAnimationsAndJobs()
        edgePopView?.visibility = View.GONE
        hudView?.visibility = View.GONE
        swipeCatcherView?.visibility = View.GONE
        modalView?.visibility = View.GONE
        dndView?.visibility = View.GONE
        modesView?.visibility = View.GONE

        rotationResetEpoch.value += 1
        rotationState.value = newRotation
        screenSizeState.value = newSize

        reanchorOverlaysImmediately(effectiveSettingsState.value, newRotation)
        edgePopView?.visibility = View.VISIBLE
    }

    private fun cancelAllActiveAnimationsAndJobs() {
        upRetractJob?.cancel()
        downRetractJob?.cancel()
        powerRetractJob?.cancel()
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        dndHideJob?.cancel()
        dndGoneJob?.cancel()
        modesHideJob?.cancel()
        modesGoneJob?.cancel()
        upPopActive.value = false
        downPopActive.value = false
        powerPopActive.value = false
        hudActive.value = false
        modalActive.value = false
        dndActive.value = false
        modesActive.value = false
        swipeTouchActive = false
        hudScrubbing = false
        settingsRepository.clearLivePreview()
    }

    private fun triggerVolumePop(
        isUp: Boolean,
        settings: NudgeSettings,
        autoRetract: Boolean
    ) {
        val popType = if (isUp) EdgePopType.VOL_UP else EdgePopType.VOL_DOWN
        if (doesVolumeSliderOverlapEdgePop(popType) || doesPanelOverlapEdgePop(popType)) return
        if (isUp) {
            upRetractJob?.cancel()
            if (upPopActive.value) {
                upPulseTrigger.value += 1
            } else {
                upPopActive.value = true
            }
        } else {
            downRetractJob?.cancel()
            if (downPopActive.value) {
                downPulseTrigger.value += 1
            } else {
                downPopActive.value = true
            }
        }
        if (settings.edgePopHapticsEnabled) {
            audioScope?.launch { volumeController.triggerHaptic(strong = false) }
        }
        if (autoRetract) {
            schedulePopRetract(isUp = isUp, settings = settings)
        }
    }

    private fun handleHudTrigger(
        settings: NudgeSettings,
        initialPress: Boolean
    ) {
        if (modalActive.value) return
        if (initialPress) {
            val now = SystemClock.uptimeMillis()
            val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
            val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
            when (settings.hudTriggerMode) {
                HudTriggerMode.SIMULTANEOUS -> showHud(settings.hudAutoHideMs)
                HudTriggerMode.SECOND_PRESS -> {
                    val isConsecutive = (now - lastVolumePressTimeMs) <= 1500L
                    if (hudActive.value || isConsecutive) {
                        showHud(settings.hudAutoHideMs)
                    }
                }
                HudTriggerMode.SWIPE_FROM_POP -> {
                    if (!popAllowed || hudActive.value) {
                        showHud(settings.hudAutoHideMs)
                    }
                }
            }
            lastVolumePressTimeMs = now
        } else if (hudActive.value || settings.hudTriggerMode == HudTriggerMode.SIMULTANEOUS) {
            showHud(settings.hudAutoHideMs)
        }
    }

    private fun ensureAudioExecutor(): Handler {
        audioHandler?.let { return it }
        val thread = HandlerThread("NudgeAudio", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply {
            start()
        }
        val handler = Handler(thread.looper)
        audioThread = thread
        audioHandler = handler
        audioScope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher("NudgeAudio"))
        return handler
    }

    private fun registerCameraAndAudioCallbacks(handler: Handler) {
        cameraPackages = resolveCameraPackages()
        publicCameraIds = runCatching { cameraManager.cameraIdList.toSet() }.getOrDefault(emptySet())
        runCatching { cameraManager.registerTorchCallback(torchCallback, handler) }
        runCatching { cameraManager.registerAvailabilityCallback(cameraAvailabilityCallback, handler) }
        val executor = Executor { command -> handler.post(command) }
        volumeController.registerAudioModeListener(executor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            runCatching { audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler) }
        }
        updateBluetoothState()
    }

    private fun resolveCameraPackages(): Set<String> {
        val result = hashSetOf(
            "com.sec.android.app.camera",
            "com.google.android.GoogleCamera",
            "com.android.camera",
            "com.android.camera2",
            "com.oppo.camera",
            "com.oneplus.camera",
            "com.miui.camera",
            "com.sonyericsson.android.camera",
            "com.motorola.camera3",
            "net.sourceforge.opencamera"
        )
        val actions = arrayOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_VIDEO_CAPTURE
        )
        for (action in actions) {
            val intent = Intent(action)
            val resolved = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(intent, 0)
                }
            }.getOrDefault(emptyList())
            for (info in resolved) {
                info.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let(result::add)
            }
        }
        return result
    }

    private fun isCameraActive(): Boolean {
        val activeNonTorch = activeCameraIds.any { id -> !torchActiveCameraIds.contains(id) }
        val fg = currentForegroundPackage
        val foregroundCamera = fg.isNotEmpty() && cameraPackages.contains(fg)
        return activeNonTorch || foregroundCamera
    }

    private fun shouldBypassVolumeInterception(
        settings: NudgeSettings,
        isLandscape: Boolean
    ): Boolean {
        if (!isScreenInteractive) {
            if (!::powerManager.isInitialized || !powerManager.isInteractive) return true
            isScreenInteractive = true
        }
        if (isCameraActive()) return true
        return !EdgeOrientationMapper.shouldConsumeVolumeKey(isLandscape, settings)
    }

    private fun applyTargetMusicSteps(targetSteps: Int) {
        val maxLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val clamped = targetSteps.coerceIn(0, maxLimit)
        setStoredStepsForStream(AudioManager.STREAM_MUSIC, clamped)
        val gain = fineVolumeController.applyStreamSteps(AudioManager.STREAM_MUSIC, clamped)
        currentSubStepGain = gain
        appVolumeController.refreshAndEnforceActiveApps(gain)
        volumeFraction.value = (clamped.toFloat() / maxLimit.toFloat()).coerceIn(0f, 1f)
    }

    private fun applyAudioVolumeStep(
        streamType: Int,
        direction: Int,
        settings: NudgeSettings,
        showSystemUi: Boolean,
        initialPress: Boolean,
        stepMultiplier: Int = 1,
        allowHaptic: Boolean = true
    ): Boolean {
        fineVolumeController.verifyAndResolveEngineMode()
        appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)

        val maxLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val configuredVolumeStep = settings.volumeStep * stepMultiplier
        val effectiveStep = if (fineVolumeController.isCoarseHardwareOnly()) {
            configuredVolumeStep.coerceIn(1, 4)
        } else {
            configuredVolumeStep.coerceIn(1, 10)
        }
        val currentSteps = getStoredStepsForStream(streamType)
        val targetSteps = (currentSteps + (effectiveStep * direction)).coerceIn(0, maxLimit)
        val changed = targetSteps != currentSteps

        setStoredStepsForStream(streamType, targetSteps)
        lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()

        val flags = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0
        val subStepGain = fineVolumeController.applyStreamSteps(streamType, targetSteps, flags)
        if (streamType == AudioManager.STREAM_MUSIC) {
            currentSubStepGain = subStepGain
            appVolumeController.refreshAndEnforceActiveApps(subStepGain)
        }

        if (settings.effectiveVolumeSliderHaptics && allowHaptic && changed) {
            volumeController.triggerHaptic(strong = false)
        }
        return changed
    }

    private fun startAudioStepAndHoldLoop(
        isUp: Boolean,
        direction: Int,
        sequence: Long,
        streamType: Int,
        downTimeMs: Long = SystemClock.uptimeMillis(),
        applyInitialStep: Boolean = true
    ) {
        val scope = audioScope ?: return
        val job = scope.launch {
            val initialSettings = effectiveSettingsState.value
            val initialLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
            if (applyInitialStep) {
                applyAudioVolumeStep(
                    streamType = streamType,
                    direction = direction,
                    settings = initialSettings,
                    showSystemUi = EdgeOrientationMapper.shouldShowSystemVolumePanel(
                        initialLandscape,
                        initialSettings
                    ),
                    initialPress = true,
                    stepMultiplier = 1
                )
            }

            val delayTo170 = (downTimeMs + 170L - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            if (delayTo170 > 0L) {
                delay(delayTo170)
            }

            val directionSign = if (isUp) 1f else -1f
            var lastHapticTimeMs = 0L

            while (isActive && keyHeldDown && holdSequence.get() == sequence) {
                val latest = effectiveSettingsState.value
                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotationState.value)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, latest)
                val showSystemUi = EdgeOrientationMapper.shouldShowSystemVolumePanel(isLandscape, latest)

                val now = SystemClock.uptimeMillis()
                val holdDurationSec = ((now - downTimeMs - 170L).coerceAtLeast(0L)) / 1000f
                val stepFactor = 1.0f + (latest.volumeStep.toFloat() - 1f) * 0.25f
                val velocityPerSec = when {
                    holdDurationSec < 0.25f -> (0.45f + holdDurationSec * 1.5f) * stepFactor
                    holdDurationSec < 0.70f -> (0.825f + (holdDurationSec - 0.25f) * 2.2f) * stepFactor
                    holdDurationSec < 1.40f -> (1.815f + (holdDurationSec - 0.70f) * 3.0f) * stepFactor
                    else -> (3.915f + (holdDurationSec - 1.40f) * 3.8f) * stepFactor
                }.coerceAtMost(7.5f)
                val deltaNormalized = velocityPerSec * 0.016f * directionSign

                val currentFraction = volumeFraction.value
                val nextFraction = (currentFraction + deltaNormalized).coerceIn(0f, 1f)
                volumeFraction.value = nextFraction

                val maxLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
                val currentSteps = getStoredStepsForStream(streamType)
                val targetSteps = (nextFraction * maxLimit).roundToInt().coerceIn(0, maxLimit)
                if (targetSteps != currentSteps) {
                    setStoredStepsForStream(streamType, targetSteps)
                    lastProgrammaticAudioTimeMs = now
                    val flags = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0
                    val subStepGain = fineVolumeController.applyStreamSteps(streamType, targetSteps, flags)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        currentSubStepGain = subStepGain
                        appVolumeController.refreshAndEnforceActiveApps(subStepGain)
                    }
                    if (latest.volumeSliderHapticsEnabled && (now - lastHapticTimeMs >= 50L)) {
                        volumeController.triggerHaptic(strong = false)
                        lastHapticTimeMs = now
                    }
                }

                if (hudAllowed && keyHeldDown && holdSequence.get() == sequence) {
                    withContext(Dispatchers.Main) {
                        if (keyHeldDown && holdSequence.get() == sequence) {
                            handleHudTrigger(settings = latest, initialPress = false)
                        }
                    }
                }
                delay(16L)
            }
        }
        if (isUp) {
            upRepeatJob?.cancel()
            upRepeatJob = job
        } else {
            downRepeatJob?.cancel()
            downRepeatJob = job
        }
    }

    private fun cancelHoldRepeatLoop(isUp: Boolean) {
        if (isUp) {
            upRepeatJob?.cancel()
            upRepeatJob = null
        } else {
            downRepeatJob?.cancel()
            downRepeatJob = null
        }
    }

    private fun triggerPowerPop(holdMs: Long, settings: NudgeSettings) {
        if (doesVolumeSliderOverlapEdgePop(EdgePopType.POWER) || doesPanelOverlapEdgePop(EdgePopType.POWER)) return
        lastPowerTriggerTimeMs = SystemClock.uptimeMillis()
        powerRetractJob?.cancel()
        if (powerPopActive.value) {
            powerPulseTrigger.value += 1
        } else {
            powerPopActive.value = true
        }
        if (settings.effectiveEdgePopHaptics) {
            audioScope?.launch { volumeController.triggerHaptic(strong = false) }
        }
        powerRetractJob = serviceScope.launch {
            delay(minOf(holdMs, 400L))
            powerPopActive.value = false
        }
    }

    private fun schedulePopRetract(isUp: Boolean, settings: NudgeSettings) {
        val lingerMs = cachedEdgePopTimeoutMs.coerceIn(50L, 5000L)
        val button = if (isUp) ButtonType.VOL_UP else ButtonType.VOL_DOWN
        edgePopDismissJobs[button]?.cancel()
        if (isUp) {
            upRetractJob?.cancel()
            upRetractJob = serviceScope.launch {
                delay(lingerMs)
                if (!swipeTouchActive) {
                    volUpIsDown = false
                    isButtonPhysicallyHeld[ButtonType.VOL_UP] = false
                    upPopActive.value = false
                }
            }
        } else {
            downRetractJob?.cancel()
            downRetractJob = serviceScope.launch {
                delay(lingerMs)
                if (!swipeTouchActive) {
                    volDownIsDown = false
                    isButtonPhysicallyHeld[ButtonType.VOL_DOWN] = false
                    downPopActive.value = false
                }
            }
        }
    }

    private fun showHud(autoHideMs: Long) {
        if (!effectiveSettingsState.value.enableVolumeSliderHud) return
        if (effectiveSettingsState.value.isPerAppVolumeEnabled) {
            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
        }
        dismissOverlappingEdgePops()
        hudCollapseGoneJob?.cancel()
        if (hudView?.visibility != View.VISIBLE) {
            hudView?.visibility = View.VISIBLE
        }
        hudActive.value = true
        resetHudAutoHide(autoHideMs)
    }

    private fun resetHudAutoHide(autoHideMs: Long) {
        hudHideJob?.cancel()
        if (hudScrubbing || modalActive.value) return
        hudHideJob = serviceScope.launch {
            delay(autoHideMs)
            if (!hudScrubbing && !modalActive.value) {
                hudActive.value = false
            }
        }
    }

    private fun setupOverlayWindows() {
        val initialSettings = effectiveSettingsState.value
        val initialRotation = rotationState.value

        val popParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            applyEdgeCutoutAndInsets()
        }

        val popView = createComposeOverlay {
            val settings by effectiveSettingsState.collectAsState()
            val rotation by rotationState.collectAsState()
            val resetEpoch by rotationResetEpoch.collectAsState()
            val upActive by upPopActive.collectAsState()
            val downActive by downPopActive.collectAsState()
            val powerActive by powerPopActive.collectAsState()
            val upPulse by upPulseTrigger.collectAsState()
            val downPulse by downPulseTrigger.collectAsState()
            val powerPulse by powerPulseTrigger.collectAsState()
            val preview by effectivePreviewTargetState.collectAsState()

            val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
            val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
            val screenEdge = EdgeOrientationMapper.resolvePhysicalEdge(
                settings.popEdgeSide,
                rotation
            )

            val isVolumeSliderEditing = preview == PreviewTarget.VOLUME_SLIDER || preview == PreviewTarget.HUD
            val showVolUpPop = !isVolumeSliderEditing && popAllowed && (
                upActive ||
                    preview == PreviewTarget.POP_UP ||
                    preview == PreviewTarget.EDGE_POP_VOL_UP ||
                    preview == PreviewTarget.POP_BOTH ||
                    preview == PreviewTarget.EDGE_POP_ALL
            )
            val showVolDownPop = !isVolumeSliderEditing && popAllowed && (
                downActive ||
                    preview == PreviewTarget.POP_DOWN ||
                    preview == PreviewTarget.EDGE_POP_VOL_DOWN ||
                    preview == PreviewTarget.POP_BOTH ||
                    preview == PreviewTarget.EDGE_POP_ALL
            )
            val showPowerPop = !isVolumeSliderEditing && popAllowed &&
                settings.enablePowerButtonPop &&
                (powerActive ||
                    preview == PreviewTarget.POP_POWER ||
                    preview == PreviewTarget.EDGE_POP_POWER)

            val livePreview by LivePreviewController.state.collectAsState()
            val effectiveSettings = if (livePreview.activeTarget == PreviewTarget.EDGE_POP_POWER) {
                settings.copy(
                    powerPopWidthDp = livePreview.overrideWidthDp ?: settings.powerPopWidthDp,
                    powerPopHeightDp = livePreview.overrideHeightDp ?: settings.powerPopHeightDp,
                    powerButtonYFraction = livePreview.overrideOffsetPercent?.let { it / 100f } ?: settings.powerButtonYFraction
                )
            } else if (livePreview.activeTarget == PreviewTarget.EDGE_POP_VOL_UP || livePreview.activeTarget == PreviewTarget.EDGE_POP_VOL_DOWN || livePreview.activeTarget == PreviewTarget.EDGE_POP_ALL) {
                settings.copy(
                    popWidthDp = livePreview.overrideWidthDp ?: settings.popWidthDp,
                    popHeightDp = livePreview.overrideHeightDp ?: settings.popHeightDp
                )
            } else {
                settings
            }

            EdgePopStripOverlay(
                settings = effectiveSettings,
                screenEdge = screenEdge,
                displayRotation = rotation,
                upExpanded = showVolUpPop,
                downExpanded = showVolDownPop,
                powerExpanded = showPowerPop,
                upPulseTrigger = upPulse,
                downPulseTrigger = downPulse,
                powerPulseTrigger = powerPulse,
                resetEpoch = resetEpoch
            )
        }
        edgePopView = popView
        windowManager.addView(popView, popParams)

        setupSwipeCatcherView(initialSettings, initialRotation)

        if (initialSettings.enableVolumeSliderHud) {
            attachHudAndModalOverlays(initialSettings, initialRotation)
        }

        attachDndOverlay(initialSettings, initialRotation)
        attachModesOverlay(initialSettings, initialRotation)
    }

    private fun setupSwipeCatcherView(settings: NudgeSettings, rotation: Int) {
        val popEdge = EdgeOrientationMapper.resolvePhysicalEdge(settings.popEdgeSide, rotation)
        val catcherSpec = buildSwipeCatcherSpec(settings, popEdge, rotation, visible = false)
        val catcherParams = specToLayoutParams(catcherSpec)
        val catcherView = View(this).apply {
            visibility = View.GONE
            setOnTouchListener(createSwipeCatcherListener())
        }
        swipeCatcherParams = catcherParams
        swipeCatcherView = catcherView
        windowManager.addView(catcherView, catcherParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSwipeCatcherListener(): View.OnTouchListener {
        var startX = 0f
        var startY = 0f
        var triggered = false
        return View.OnTouchListener { _, motion ->
            val currentSettings = effectiveSettingsState.value
            val screenEdge = EdgeOrientationMapper.resolvePhysicalEdge(
                currentSettings.popEdgeSide,
                rotationState.value
            )
            val thresholdPx = dpToPx(12f).toFloat()
            when (motion.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeTouchActive = true
                    triggered = false
                    startX = motion.rawX
                    startY = motion.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!triggered) {
                        val dx = motion.rawX - startX
                        val dy = motion.rawY - startY
                        val swiped = when (screenEdge) {
                            ScreenEdge.RIGHT -> dx <= -thresholdPx
                            ScreenEdge.LEFT -> dx >= thresholdPx
                            ScreenEdge.TOP -> dy >= thresholdPx
                            ScreenEdge.BOTTOM -> dy <= -thresholdPx
                        }
                        if (swiped) {
                            triggered = true
                            swipeTouchActive = false
                            upPopActive.value = false
                            downPopActive.value = false
                            showHud(currentSettings.hudAutoHideMs)
                            if (currentSettings.volumeSliderHapticsEnabled) {
                                volumeController.triggerHaptic(strong = true)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    swipeTouchActive = false
                    if (!triggered) {
                        if (upPopActive.value) schedulePopRetract(isUp = true, settings = currentSettings)
                        if (downPopActive.value) schedulePopRetract(isUp = false, settings = currentSettings)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun attachHudAndModalOverlays(settings: NudgeSettings, rotation: Int) {
        if (hudView == null) {
            val hudEdge = resolveActiveHudEdge(settings, rotation)
            val sliderSpec = buildHudLayoutSpec(settings, hudEdge, rotation, touchable = false)
            val sliderParams = specToLayoutParams(sliderSpec)
            val sliderView = createComposeOverlay {
                val latestSettings by effectiveSettingsState.collectAsState()
                val latestRotation by rotationState.collectAsState()
                val resetEpoch by rotationResetEpoch.collectAsState()
                val active by hudActive.collectAsState()
                val preview by effectivePreviewTargetState.collectAsState()
                val volFraction by volumeFraction.collectAsState()
                val isBtConnected by isBluetoothAudioConnected.collectAsState()

                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(latestRotation)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, latestSettings)
                val screenEdge = resolveActiveHudEdge(
                    latestSettings,
                    latestRotation
                )
                val isAttachedMixerActive = (active && modalActive.collectAsState().value && latestSettings.isAudioMixerAttachedToSlider)
                val isTransitionFromMixer = latestSettings.isAudioMixerAttachedToSlider && (SystemClock.uptimeMillis() - modalDismissedTimeMs < 700L)
                val isVolumeSliderPreview = preview == PreviewTarget.HUD || preview == PreviewTarget.VOLUME_SLIDER
                val visible = hudAllowed && (active || isVolumeSliderPreview) && !isAttachedMixerActive

                val activeApps by appVolumeController.activeMediaApps.collectAsState()
                val primaryApp = if (latestSettings.isPerAppVolumeEnabled && latestSettings.perAppVolumeDisplayMode == "ALONGSIDE_SLIDER") {
                    appVolumeController.getPriorityAlongsideApp()
                } else null
                val mediaLimit by fineVolumeController.dynamicMaxLimit.collectAsState()

                var alongsideScrubFraction by remember(primaryApp?.uid) {
                    mutableFloatStateOf(primaryApp?.fillFraction ?: 1f)
                }
                LaunchedEffect(primaryApp?.fillFraction) {
                    if (!hudScrubbing) {
                        alongsideScrubFraction = primaryApp?.fillFraction ?: 1f
                    }
                }

                LaunchedEffect(visible) {
                    if (visible) {
                        while (isActive) {
                            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
                            delay(500L)
                        }
                    }
                }

                if (primaryApp != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (screenEdge == ScreenEdge.RIGHT) {
                            VolumeSliderHud(
                                screenEdge = screenEdge,
                                hudWidthDp = latestSettings.hudWidthDp,
                                hudHeightDp = latestSettings.hudHeightDp,
                                hudCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                                floatingMode = true,
                                edgeInsetDp = 0f,
                                volumeFraction = alongsideScrubFraction,
                                isVisible = visible,
                                resetEpoch = resetEpoch,
                                isBluetoothConnected = false,
                                activeStreamType = AudioManager.STREAM_MUSIC,
                                inCall = false,
                                isMediaPlayingNow = true,
                                volumeSliderFluidityPercent = latestSettings.volumeSliderFluidityPercent,
                                maxVolumeLimit = mediaLimit,
                                customAppIcon = primaryApp.appIcon,
                                showVolumeCounter = latestSettings.showVolumeCounter,
                                onVolumeScrub = { frac ->
                                    alongsideScrubFraction = frac
                                    appVolumeController.setAppVolumeSteps(
                                        primaryApp.uid,
                                        (frac * mediaLimit).roundToInt()
                                    )
                                },
                                onScrubStateChanged = { scrubbing ->
                                    hudScrubbing = scrubbing
                                    if (scrubbing) {
                                        hudHideJob?.cancel()
                                    } else if (hudActive.value) {
                                        resetHudAutoHide(latestSettings.hudAutoHideMs)
                                    }
                                },
                                onOpenMenu = {
                                    if (latestSettings.effectiveVolumeSliderHaptics) {
                                        volumeController.triggerHaptic(strong = true)
                                    }
                                    showAudioMixerModal()
                                },
                                interactive = visible,
                                instantAppearance = isTransitionFromMixer,
                                modifier = Modifier.wrapContentSize()
                            )
                        }

                        VolumeSliderHud(
                            screenEdge = screenEdge,
                            hudWidthDp = latestSettings.hudWidthDp,
                            hudHeightDp = latestSettings.hudHeightDp,
                            hudCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                            floatingMode = latestSettings.floatingMode,
                            edgeInsetDp = latestSettings.hudEdgeInsetDp,
                            volumeFraction = volFraction,
                            isVisible = visible,
                            resetEpoch = resetEpoch,
                            isBluetoothConnected = isBtConnected,
                            activeStreamType = when (resolveActiveVolumeStream()) {
                                ActiveVolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
                                ActiveVolumeStream.RING -> AudioManager.STREAM_RING
                                ActiveVolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
                            },
                            inCall = volumeController.isCallActive(),
                            isMediaPlayingNow = audioManager.isMusicActive,
                            volumeSliderFluidityPercent = latestSettings.volumeSliderFluidityPercent,
                            maxVolumeLimit = mediaLimit,
                            customAppIcon = null,
                            showVolumeCounter = latestSettings.showVolumeCounter,
                            instantAppearance = isTransitionFromMixer,
                            onVolumeScrub = { fraction ->
                                val targetSteps = (fraction.coerceIn(0f, 1f) * mediaLimit.toFloat()).roundToInt().coerceIn(0, mediaLimit)
                                val currentSteps = getStoredStepsForStream(AudioManager.STREAM_MUSIC)
                                val changed = targetSteps != currentSteps
                                volumeFraction.value = fraction
                                if (changed) {
                                    applyTargetMusicSteps(targetSteps)
                                    lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
                                    if (latestSettings.effectiveVolumeSliderHaptics) {
                                        volumeController.triggerHaptic(strong = false)
                                    }
                                }
                            },
                            onScrubStateChanged = { scrubbing ->
                                hudScrubbing = scrubbing
                                if (scrubbing) {
                                    hudHideJob?.cancel()
                                } else if (hudActive.value) {
                                    resetHudAutoHide(latestSettings.hudAutoHideMs)
                                }
                            },
                            onOpenMenu = {
                                if (latestSettings.effectiveVolumeSliderHaptics) {
                                    volumeController.triggerHaptic(strong = true)
                                }
                                showAudioMixerModal()
                            },
                            interactive = visible,
                            modifier = Modifier.wrapContentSize()
                        )

                        if (screenEdge != ScreenEdge.RIGHT) {
                            VolumeSliderHud(
                                screenEdge = screenEdge,
                                hudWidthDp = latestSettings.hudWidthDp,
                                hudHeightDp = latestSettings.hudHeightDp,
                                hudCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                                floatingMode = true,
                                edgeInsetDp = 0f,
                                volumeFraction = alongsideScrubFraction,
                                isVisible = visible,
                                resetEpoch = resetEpoch,
                                isBluetoothConnected = false,
                                activeStreamType = AudioManager.STREAM_MUSIC,
                                inCall = false,
                                isMediaPlayingNow = true,
                                volumeSliderFluidityPercent = latestSettings.volumeSliderFluidityPercent,
                                maxVolumeLimit = mediaLimit,
                                customAppIcon = primaryApp.appIcon,
                                showVolumeCounter = latestSettings.showVolumeCounter,
                                onVolumeScrub = { frac ->
                                    alongsideScrubFraction = frac
                                    appVolumeController.setAppVolumeSteps(
                                        primaryApp.uid,
                                        (frac * mediaLimit).roundToInt()
                                    )
                                },
                                onScrubStateChanged = { scrubbing ->
                                    hudScrubbing = scrubbing
                                    if (scrubbing) {
                                        hudHideJob?.cancel()
                                    } else if (hudActive.value) {
                                        resetHudAutoHide(latestSettings.hudAutoHideMs)
                                    }
                                },
                                onOpenMenu = {
                                    if (latestSettings.effectiveVolumeSliderHaptics) {
                                        volumeController.triggerHaptic(strong = true)
                                    }
                                    showAudioMixerModal()
                                },
                                interactive = visible,
                                instantAppearance = isTransitionFromMixer,
                                modifier = Modifier.wrapContentSize()
                            )
                        }
                    }
                } else {
                    VolumeSliderHud(
                        screenEdge = screenEdge,
                        hudWidthDp = latestSettings.hudWidthDp,
                        hudHeightDp = latestSettings.hudHeightDp,
                        hudCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                        floatingMode = latestSettings.floatingMode,
                        edgeInsetDp = latestSettings.hudEdgeInsetDp,
                        volumeFraction = volFraction,
                        isVisible = visible,
                        resetEpoch = resetEpoch,
                        isBluetoothConnected = isBtConnected,
                        activeStreamType = when (resolveActiveVolumeStream()) {
                            ActiveVolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
                            ActiveVolumeStream.RING -> AudioManager.STREAM_RING
                            ActiveVolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
                        },
                        inCall = volumeController.isCallActive(),
                        isMediaPlayingNow = audioManager.isMusicActive,
                        volumeSliderFluidityPercent = latestSettings.volumeSliderFluidityPercent,
                        maxVolumeLimit = mediaLimit,
                        customAppIcon = null,
                        showVolumeCounter = latestSettings.showVolumeCounter,
                        instantAppearance = isTransitionFromMixer,
                        onVolumeScrub = { fraction ->
                            val targetSteps = (fraction.coerceIn(0f, 1f) * mediaLimit.toFloat()).roundToInt().coerceIn(0, mediaLimit)
                            val currentSteps = getStoredStepsForStream(AudioManager.STREAM_MUSIC)
                            val changed = targetSteps != currentSteps
                            volumeFraction.value = fraction
                            if (changed) {
                                applyTargetMusicSteps(targetSteps)
                                lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
                                if (latestSettings.effectiveVolumeSliderHaptics) {
                                    volumeController.triggerHaptic(strong = false)
                                }
                            }
                        },
                        onScrubStateChanged = { scrubbing ->
                            hudScrubbing = scrubbing
                            if (scrubbing) {
                                hudHideJob?.cancel()
                            } else if (hudActive.value) {
                                resetHudAutoHide(latestSettings.hudAutoHideMs)
                            }
                        },
                        onOpenMenu = {
                            if (latestSettings.effectiveVolumeSliderHaptics) {
                                volumeController.triggerHaptic(strong = true)
                            }
                            showAudioMixerModal()
                        },
                        interactive = visible,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            sliderView.visibility = View.GONE
            hudParams = sliderParams
            hudView = sliderView
            windowManager.addView(sliderView, sliderParams)
        }

        if (modalView == null) {
            val quickModalParams = createFullModalParams()
            val quickModalComposeView = createComposeOverlay {
                val isOpen by modalActive.collectAsState()
                val latestSettings by effectiveSettingsState.collectAsState()
                val volFraction by volumeFraction.collectAsState()
                val isBtConnected by isBluetoothAudioConnected.collectAsState()
                val latestRotation by rotationState.collectAsState()
                val resolvedEdge = resolveActiveHudEdge(latestSettings, latestRotation)
                val mixerActiveApps by if (latestSettings.isPerAppVolumeEnabled) {
                    appVolumeController.activeMediaApps.collectAsState()
                } else {
                    remember { MutableStateFlow(emptyList<ActiveMediaApp>()) }.collectAsState()
                }
                val mediaLimit by fineVolumeController.dynamicMaxLimit.collectAsState()
                val previewTarget by effectivePreviewTargetState.collectAsState()
                val isMixerVisible = isOpen || previewTarget == PreviewTarget.AUDIO_MIXER || previewTarget == PreviewTarget.MIXER

                LaunchedEffect(isMixerVisible) {
                    if (isMixerVisible) {
                        while (isActive) {
                            appVolumeController.refreshAndEnforceActiveApps(currentSubStepGain)
                            delay(500L)
                        }
                    }
                }

                NudgeTheme(darkTheme = !latestSettings.lightMode) {
                    QuickVolumeModal(
                        volumeController = volumeController,
                        musicFractionExternal = volFraction,
                        sliderWidthDp = latestSettings.hudWidthDp,
                        sliderHeightDp = latestSettings.hudHeightDp,
                        sliderCornerRadiusDp = latestSettings.hudCornerRadiusDp,
                        lightMode = latestSettings.lightMode,
                        hapticsEnabled = latestSettings.effectiveVolumeSliderHaptics,
                        isBluetoothConnected = isBtConnected,
                        isCallActive = volumeController.isCallActive(),
                        isAlwaysShowCallSlider = latestSettings.isAlwaysShowCallSliderInMixer,
                        callFractionExternal = (getStoredStepsForStream(AudioManager.STREAM_VOICE_CALL).toFloat() / mediaLimit.toFloat()).coerceIn(0f, 1f),
                        onCallFractionChanged = { fraction ->
                            val targetSteps = (fraction.coerceIn(0f, 1f) * mediaLimit.toFloat()).roundToInt().coerceIn(0, mediaLimit)
                            setStoredStepsForStream(AudioManager.STREAM_VOICE_CALL, targetSteps)
                            fineVolumeController.applyStreamSteps(AudioManager.STREAM_VOICE_CALL, targetSteps)
                        },
                        isVisible = isMixerVisible,
                        onMusicFractionChanged = { fraction ->
                            val targetSteps = (fraction.coerceIn(0f, 1f) * mediaLimit.toFloat()).roundToInt().coerceIn(0, mediaLimit)
                            setStoredStepsForStream(AudioManager.STREAM_MUSIC, targetSteps)
                            applyTargetMusicSteps(targetSteps)
                            lastProgrammaticAudioTimeMs = SystemClock.uptimeMillis()
                        },
                        onOpenSettings = {
                            modalActive.value = false
                            hudActive.value = false
                            val intent = Intent(this, MainActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                            startActivity(intent)
                        },
                        onDismiss = {
                            dismissModalOverlay()
                        },
                        audioMixerScalePercent = latestSettings.audioMixerScalePercent,
                        audioMixerPopupWidthDp = latestSettings.audioMixerPopupWidthDp,
                        audioMixerPopupHeightDp = latestSettings.audioMixerPopupHeightDp,
                        audioMixerPillWidthDp = latestSettings.audioMixerPillWidthDp,
                        audioMixerPillHeightDp = latestSettings.audioMixerPillHeightDp,
                        audioMixerPillSpacingDp = latestSettings.audioMixerPillSpacingDp,
                        isAudioMixerAttachedToSlider = latestSettings.isAudioMixerAttachedToSlider,
                        isAudioMixerManuallySized = latestSettings.isAudioMixerManuallySized,
                        audioMixerXPercent = latestSettings.audioMixerXPercent,
                        audioMixerYPercent = latestSettings.audioMixerYPercent,
                        sliderEdge = resolvedEdge,
                        sliderYFraction = latestSettings.hudYFraction,
                        sliderXFraction = 0.5f,
                        sliderEdgeInsetDp = if (latestSettings.floatingMode) latestSettings.hudEdgeInsetDp else 0f,
                        volumeSliderFluidityPercent = latestSettings.volumeSliderFluidityPercent,
                        mediaMaxVolumeLimit = mediaLimit,
                        callMaxVolumeLimit = mediaLimit,
                        ringMaxVolumeLimit = mediaLimit,
                        notifyMaxVolumeLimit = mediaLimit,
                        alarmMaxVolumeLimit = mediaLimit,
                        activeMediaApps = mixerActiveApps,
                        onAppVolumeChanged = { uid, targetSteps, _ ->
                            appVolumeController.setAppVolumeSteps(uid, targetSteps)
                        }
                    )
                }
            }
            val quickModalContainer = object : FrameLayout(this) {
                override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                    if (ev?.actionMasked == MotionEvent.ACTION_OUTSIDE && modalActive.value) {
                        dismissModalOverlay()
                        return true
                    }
                    return super.dispatchTouchEvent(ev)
                }
            }.apply {
                lifecycleOwner.attachToView(this)
                addView(
                    quickModalComposeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                visibility = View.GONE
            }
            modalParams = quickModalParams
            modalView = quickModalContainer
            windowManager.addView(quickModalContainer, quickModalParams)
        }
    }

    private fun attachDndOverlay(settings: NudgeSettings, rotation: Int) {
        if (dndView != null) return
        val edge = resolveActiveBezelEdge(settings, isDnd = true)
        val spec = buildDndLayoutSpec(settings, edge, rotation, touchable = false)
        val params = specToLayoutParams(spec)

        val composeView = createComposeOverlay {
            val latestSettings by effectiveSettingsState.collectAsState()
            val latestRotation by rotationState.collectAsState()
            val active by dndActive.collectAsState()
            val isSilentOrDnd by dndState.collectAsState()
            val preview by effectivePreviewTargetState.collectAsState()

            val resolvedEdge = resolveActiveBezelEdge(latestSettings, isDnd = true)
            val visible = active || preview == PreviewTarget.DND || preview == PreviewTarget.DND_PANEL

            DndSilentBezelOverlay(
                screenEdge = resolvedEdge,
                overlayWidthDp = latestSettings.dndThicknessDp,
                overlayHeightDp = latestSettings.dndLengthDp,
                iconSizeDp = latestSettings.dndIconSizeDp,
                unfocusedOffsetDp = latestSettings.dndUnfocusedOffsetDp,
                fluidityPercent = latestSettings.dndPanelFluidityPercent,
                showLabel = latestSettings.showDndLabel,
                modeType = latestSettings.dndModeType,
                isSilentOrDndActive = isSilentOrDnd,
                isVisible = visible,
                onSelectState = { active: Boolean -> setDndState(active) },
                onPauseAutoHideTimer = { pauseDndAutoHideTimer() },
                onRestartAutoHideTimer = { restartDndAutoHideTimer() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composeView.visibility = View.GONE
        dndParams = params
        dndView = composeView
        windowManager.addView(composeView, params)
    }

    private fun attachModesOverlay(settings: NudgeSettings, rotation: Int) {
        if (modesView != null) return
        val edge = resolveActiveBezelEdge(settings, isDnd = false)
        val spec = buildModesLayoutSpec(settings, edge, rotation, touchable = false)
        val params = specToLayoutParams(spec)

        val composeView = createComposeOverlay {
            val latestSettings by effectiveSettingsState.collectAsState()
            val latestRotation by rotationState.collectAsState()
            val active by modesActive.collectAsState()
            val selectedModeId by modesSelectedModeId.collectAsState()
            val preview by effectivePreviewTargetState.collectAsState()

            val resolvedEdge = resolveActiveBezelEdge(latestSettings, isDnd = false)
            val visible = active || preview == PreviewTarget.MODES || preview == PreviewTarget.ACTIONS_PANEL
            val modes = latestSettings.customModes.ifEmpty { DEFAULT_CUSTOM_MODES }

            ActionsPanelOverlay(
                screenEdge = resolvedEdge,
                overlayWidthDp = latestSettings.modesThicknessDp,
                overlayHeightDp = latestSettings.modesLengthDp,
                iconSizeDp = latestSettings.actionsIconSizeDp,
                iconSpacingDp = latestSettings.actionsIconSpacingDp,
                fluidityPercent = latestSettings.actionsPanelFluidityPercent,
                showLabel = latestSettings.showActionsLabel,
                modes = modes,
                selectedModeId = selectedModeId,
                isVisible = visible,
                onModeSelected = { mode ->
                    modesSelectedModeId.value = mode.id
                    resetModesTimeout(latestSettings.modesTimeoutMs)
                },
                onPauseAutoHideTimer = { pauseActionsAutoHideTimer() },
                onRestartAutoHideTimer = { restartActionsAutoHideTimer() },
                onStepUp = { stepModeSelection(up = true) },
                onStepDown = { stepModeSelection(up = false) },
                stepFlow = actionsWheelStep,
                modifier = Modifier.fillMaxSize()
            )
        }
        composeView.visibility = View.GONE
        modesParams = params
        modesView = composeView
        windowManager.addView(composeView, params)
    }

    private fun detachHudAndModalOverlays() {
        hudActive.value = false
        modalActive.value = false
        hudHideJob?.cancel()
        hudCollapseGoneJob?.cancel()
        modalCollapseGoneJob?.cancel()
        removeOverlayView(hudView)
        removeOverlayView(modalView)
        hudView = null
        hudParams = null
        modalView = null
        modalParams = null
    }

    private fun detachAllOverlays() {
        removeOverlayView(edgePopView)
        removeOverlayView(swipeCatcherView)
        removeOverlayView(hudView)
        removeOverlayView(modalView)
        removeOverlayView(dndView)
        removeOverlayView(modesView)
        edgePopView = null
        swipeCatcherView = null
        swipeCatcherParams = null
        hudView = null
        hudParams = null
        modalView = null
        modalParams = null
        dndView = null
        dndParams = null
        modesView = null
        modesParams = null
    }

    private fun observeOverlayLayout() {
        serviceScope.launch {
            combine(
                effectiveSettingsState,
                rotationState,
                settingsRepository.previewLandscape,
                screenSizeState
            ) { settings, rotation, _, size ->
                Triple(settings, rotation, size)
            }.distinctUntilChanged()
            .collect { (settings, rotation, _) ->
                    val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
                    val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, settings)
                    val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)

                    if (!popAllowed && (upPopActive.value || downPopActive.value || powerPopActive.value)) {
                        upPopActive.value = false
                        downPopActive.value = false
                        powerPopActive.value = false
                    }

                    if (settings.enableVolumeSliderHud) {
                        attachHudAndModalOverlays(settings, rotation)
                    } else {
                        detachHudAndModalOverlays()
                    }

                    if (!hudAllowed && (hudActive.value || modalActive.value)) {
                        hudActive.value = false
                        modalActive.value = false
                    }

                    reanchorOverlaysImmediately(settings, rotation)
                }
        }

        serviceScope.launch {
            combine(
                effectiveSettingsState,
                upPopActive,
                downPopActive,
                hudActive,
                effectivePreviewTargetState,
                appVolumeController.activeMediaApps
            ) { args: Array<Any?> ->
                val settings = args[0] as NudgeSettings
                val up = args[1] as Boolean
                val down = args[2] as Boolean
                val hud = args[3] as Boolean
                val preview = args[4] as PreviewTarget
                @Suppress("UNCHECKED_CAST")
                val activeApps = args[5] as List<ActiveMediaApp>
                HudTouchSnapshot(
                    settings = settings,
                    swipeActive = (up || down) &&
                        settings.hudTriggerMode == HudTriggerMode.SWIPE_FROM_POP &&
                        !hud,
                    hudVisible = hud || preview == PreviewTarget.HUD || preview == PreviewTarget.VOLUME_SLIDER,
                    hasAlongsideApp = settings.isPerAppVolumeEnabled &&
                        settings.perAppVolumeDisplayMode == "ALONGSIDE_SLIDER" &&
                        activeApps.isNotEmpty()
                )
            }.combine(rotationState) { snapshot, rotation ->
                Pair(snapshot, rotation)
            }.distinctUntilChanged().collect { (snapshot, rotation) ->
                val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
                val popAllowed = EdgeOrientationMapper.shouldShowEdgePop(isLandscape, snapshot.settings)
                val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, snapshot.settings)

                val popEdge = EdgeOrientationMapper.resolvePhysicalEdge(
                    snapshot.settings.popEdgeSide,
                    rotation
                )
                val showSwipeCatcher = popAllowed && hudAllowed && snapshot.swipeActive
                val catcherView = swipeCatcherView
                val catcherParams = swipeCatcherParams
                if (catcherView != null && catcherParams != null) {
                    val catcherSpec = buildSwipeCatcherSpec(
                        settings = snapshot.settings,
                        screenEdge = popEdge,
                        rotation = rotation,
                        visible = showSwipeCatcher
                    )
                    catcherView.visibility = if (showSwipeCatcher) View.VISIBLE else View.GONE
                    applyLayoutSpecIfChanged(catcherView, catcherParams, catcherSpec)
                }

                val hView = hudView
                val hParams = hudParams
                if (hView != null && hParams != null) {
                    val effectiveVisible = hudAllowed && snapshot.hudVisible
                    if (effectiveVisible) {
                        hudCollapseGoneJob?.cancel()
                        if (hView.visibility != View.VISIBLE) {
                            hView.visibility = View.VISIBLE
                        }
                    } else if (hView.visibility == View.VISIBLE) {
                        hudCollapseGoneJob?.cancel()
                        hudCollapseGoneJob = serviceScope.launch {
                            delay(260L)
                            hudView?.visibility = View.GONE
                        }
                    }

                    val hudEdge = resolveActiveHudEdge(
                        snapshot.settings,
                        rotation
                    )
                    val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                        effectivePreviewTargetState.value == PreviewTarget.HUD ||
                        effectivePreviewTargetState.value == PreviewTarget.VOLUME_SLIDER
                    val touchable = effectiveVisible && hudActive.value && !isPreviewActive
                    val hudSpec = buildHudLayoutSpec(
                        settings = snapshot.settings,
                        screenEdge = hudEdge,
                        rotation = rotation,
                        touchable = touchable,
                        hasAlongsideAppOverride = snapshot.hasAlongsideApp
                    )
                    applyLayoutSpecIfChanged(hView, hParams, hudSpec)
                }
            }
        }

        serviceScope.launch {
            combine(modalActive, effectivePreviewTargetState) { open, preview ->
                Pair(open, preview == PreviewTarget.AUDIO_MIXER || preview == PreviewTarget.MIXER)
            }.collect { (open, isPreview) ->
                if ((open || isPreview) && modalView == null) {
                    attachHudAndModalOverlays(effectiveSettingsState.value, rotationState.value)
                }
                val view = modalView ?: return@collect
                val params = modalParams ?: return@collect
                val shouldBeVisible = open || isPreview
                val desiredFlags = if (open && !isPreview) {
                    baseOverlayFlags() or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                } else {
                    baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
                if (shouldBeVisible) {
                    modalCollapseGoneJob?.cancel()
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                    }
                } else if (view.visibility == View.VISIBLE) {
                    modalCollapseGoneJob?.cancel()
                    modalCollapseGoneJob = serviceScope.launch {
                        delay(210L)
                        if (!modalActive.value && effectivePreviewTargetState.value != PreviewTarget.AUDIO_MIXER && effectivePreviewTargetState.value != PreviewTarget.MIXER) {
                            modalView?.visibility = View.GONE
                        }
                    }
                }
                if (params.flags != desiredFlags) {
                    params.flags = desiredFlags
                    if (view.isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }
            }
        }

        serviceScope.launch {
            combine(dndActive, effectivePreviewTargetState) { active, preview ->
                active || preview == PreviewTarget.DND || preview == PreviewTarget.DND_PANEL
            }.distinctUntilChanged().collect { shouldShow ->
                val dView = dndView ?: return@collect
                val dParams = dndParams ?: return@collect
                if (shouldShow) {
                    dismissOverlappingEdgePops()
                    dndGoneJob?.cancel()
                    if (dView.visibility != View.VISIBLE) {
                        dView.visibility = View.VISIBLE
                    }
                } else {
                    dndGoneJob?.cancel()
                    dndGoneJob = serviceScope.launch {
                        delay(220L)
                        if (!dndActive.value && effectivePreviewTargetState.value != PreviewTarget.DND && effectivePreviewTargetState.value != PreviewTarget.DND_PANEL) {
                            dView.visibility = View.GONE
                        }
                    }
                }
                val latest = effectiveSettingsState.value
                val rot = rotationState.value
                val edge = resolveActiveBezelEdge(latest, isDnd = true)
                val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                    effectivePreviewTargetState.value == PreviewTarget.DND ||
                    effectivePreviewTargetState.value == PreviewTarget.DND_PANEL
                val touchable = dndActive.value && !isPreviewActive
                applyLayoutSpecIfChanged(dView, dParams, buildDndLayoutSpec(latest, edge, rot, touchable = touchable))
            }
        }

        serviceScope.launch {
            combine(modesActive, effectivePreviewTargetState) { active, preview ->
                Pair(active || preview == PreviewTarget.MODES || preview == PreviewTarget.ACTIONS_PANEL, active)
            }.distinctUntilChanged().collect { (shouldShow, active) ->
                val mView = modesView ?: return@collect
                val mParams = modesParams ?: return@collect
                if (shouldShow) {
                    dismissOverlappingEdgePops()
                    modesGoneJob?.cancel()
                    if (mView.visibility != View.VISIBLE) {
                        mView.visibility = View.VISIBLE
                    }
                } else {
                    modesGoneJob?.cancel()
                    modesGoneJob = serviceScope.launch {
                        delay(220L)
                        if (!modesActive.value && effectivePreviewTargetState.value != PreviewTarget.MODES && effectivePreviewTargetState.value != PreviewTarget.ACTIONS_PANEL) {
                            mView.visibility = View.GONE
                        }
                    }
                }
                val latest = effectiveSettingsState.value
                val rot = rotationState.value
                val edge = resolveActiveBezelEdge(latest, isDnd = false)
                val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                    effectivePreviewTargetState.value == PreviewTarget.MODES ||
                    effectivePreviewTargetState.value == PreviewTarget.ACTIONS_PANEL
                val touchable = modesActive.value && !isPreviewActive
                applyLayoutSpecIfChanged(mView, mParams, buildModesLayoutSpec(latest, edge, rot, touchable = touchable))
            }
        }
    }

    private fun reanchorOverlaysImmediately(settings: NudgeSettings, rotation: Int) {
        val hView = hudView
        val hParams = hudParams
        if (hView != null && hParams != null) {
            val isLandscape = EdgeOrientationMapper.isLandscapeRotation(rotation)
            val hudAllowed = EdgeOrientationMapper.shouldShowSliderHud(isLandscape, settings)
            val hudEdge = resolveActiveHudEdge(settings, rotation)
            val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                effectivePreviewTargetState.value == PreviewTarget.HUD ||
                effectivePreviewTargetState.value == PreviewTarget.VOLUME_SLIDER
            val touchable = hudAllowed && hudActive.value && !isPreviewActive
            applyLayoutSpecIfChanged(
                hView,
                hParams,
                buildHudLayoutSpec(settings, hudEdge, rotation, touchable)
            )
        }

        val dView = dndView
        val dParams = dndParams
        if (dView != null && dParams != null) {
            val edge = resolveActiveBezelEdge(settings, isDnd = true)
            val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                effectivePreviewTargetState.value == PreviewTarget.DND ||
                effectivePreviewTargetState.value == PreviewTarget.DND_PANEL
            val touchable = dndActive.value && !isPreviewActive
            if ((dndActive.value || isPreviewActive) && dView.visibility != View.VISIBLE) {
                dndGoneJob?.cancel()
                dView.visibility = View.VISIBLE
            }
            applyLayoutSpecIfChanged(dView, dParams, buildDndLayoutSpec(settings, edge, rotation, touchable))
        }

        val mView = modesView
        val mParams = modesParams
        if (mView != null && mParams != null) {
            val edge = resolveActiveBezelEdge(settings, isDnd = false)
            val isPreviewActive = LivePreviewController.state.value.activeTarget != PreviewTarget.NONE ||
                effectivePreviewTargetState.value == PreviewTarget.MODES ||
                effectivePreviewTargetState.value == PreviewTarget.ACTIONS_PANEL
            val touchable = modesActive.value && !isPreviewActive
            if ((modesActive.value || isPreviewActive) && mView.visibility != View.VISIBLE) {
                modesGoneJob?.cancel()
                mView.visibility = View.VISIBLE
            }
            applyLayoutSpecIfChanged(mView, mParams, buildModesLayoutSpec(settings, edge, rotation, touchable))
        }
    }

    private fun applyLayoutSpecIfChanged(
        view: View,
        params: WindowManager.LayoutParams,
        desired: WindowLayoutSpec
    ) {
        if (params.width != desired.width ||
            params.height != desired.height ||
            params.gravity != desired.gravity ||
            params.x != desired.x ||
            params.y != desired.y ||
            params.flags != desired.flags
        ) {
            params.width = desired.width
            params.height = desired.height
            params.gravity = desired.gravity
            params.x = desired.x
            params.y = desired.y
            params.flags = desired.flags
            if (view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun createComposeOverlay(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        val displayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayMgr = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = displayMgr?.getDisplay(Display.DEFAULT_DISPLAY)
            if (defaultDisplay != null) createDisplayContext(defaultDisplay) else this
        } else {
            this
        }
        return ComposeView(displayContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            lifecycleOwner.attachToView(this)
            lifecycleOwner.ensureResumed()
            setContent(content)
        }
    }

    private fun specToLayoutParams(spec: WindowLayoutSpec): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            spec.width,
            spec.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            spec.flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = spec.gravity
            x = spec.x
            y = spec.y
            applyEdgeCutoutAndInsets()
        }
    }

    private fun buildSwipeCatcherSpec(
        settings: NudgeSettings,
        screenEdge: ScreenEdge,
        rotation: Int,
        visible: Boolean
    ): WindowLayoutSpec {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val breadthPx = dpToPx(maxOf(settings.popWidthDp * 1.15f, 44f))
        val targetFraction = if (upPopActive.value) settings.volumeUpYFraction else settings.volumeDownYFraction
        val spanPx = dpToPx(settings.popHeightDp)
        val (screenW, screenH) = screenSizeState.value

        val width = if (isVertical) breadthPx else spanPx
        val height = if (isVertical) spanPx else breadthPx
        val x: Int
        val y: Int
        if (isVertical) {
            x = 0
            y = EdgeOrientationMapper.computeElementTopOrLeftPx(
                portraitYFraction = targetFraction,
                rotation = rotation,
                axisLengthPx = screenH.toFloat(),
                elementSpanPx = height
            )
        } else {
            x = EdgeOrientationMapper.computeElementTopOrLeftPx(
                portraitYFraction = targetFraction,
                rotation = rotation,
                axisLengthPx = screenW.toFloat(),
                elementSpanPx = width
            )
            y = 0
        }

        val flags = if (visible) {
            baseOverlayFlags()
        } else {
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowLayoutSpec(
            width = width,
            height = height,
            gravity = screenEdgeGravity(screenEdge),
            x = x,
            y = y,
            flags = flags
        )
    }

    private fun buildHudLayoutSpec(
        settings: NudgeSettings,
        screenEdge: ScreenEdge,
        rotation: Int,
        touchable: Boolean,
        hasAlongsideAppOverride: Boolean? = null
    ): WindowLayoutSpec {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val insetDp = settings.hudEdgeInsetDp
        val hasAlongsideApp = hasAlongsideAppOverride ?: (settings.isPerAppVolumeEnabled &&
            settings.perAppVolumeDisplayMode == "ALONGSIDE_SLIDER" &&
            ::appVolumeController.isInitialized &&
            appVolumeController.activeMediaApps.value.isNotEmpty())
        val thicknessMultiplier = if (hasAlongsideApp) 2.2f else 1f
        val thicknessPx = dpToPx(computeHudCutoutThicknessDp(settings.hudWidthDp, insetDp) * thicknessMultiplier)
        val spanPx = dpToPx(computeHudOuterSpanDp(settings.hudHeightDp, settings.volumeSliderFluidityPercent))
        val width = if (isVertical) thicknessPx else spanPx
        val height = if (isVertical) spanPx else thicknessPx
        val (screenW, screenH) = screenSizeState.value

        val x: Int
        val y: Int
        if (isVertical) {
            x = 0
            y = computeActiveHudTopOrLeftPx(
                settings = settings,
                rotation = rotation,
                axisLengthPx = screenH.toFloat(),
                elementSpanPx = height
            )
        } else {
            x = computeActiveHudTopOrLeftPx(
                settings = settings,
                rotation = rotation,
                axisLengthPx = screenW.toFloat(),
                elementSpanPx = width
            )
            y = 0
        }

        val flags = if (touchable) {
            baseOverlayFlags()
        } else {
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowLayoutSpec(
            width = width,
            height = height,
            gravity = screenEdgeGravity(screenEdge),
            x = x,
            y = y,
            flags = flags
        )
    }

    private fun buildDndLayoutSpec(
        settings: NudgeSettings,
        screenEdge: ScreenEdge,
        rotation: Int,
        touchable: Boolean
    ): WindowLayoutSpec {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val breadthPx = dpToPx(settings.dndThicknessDp + 90f)
        val spanPx = dpToPx(computeDndOuterSpanDp(settings.dndLengthDp, settings.dndPanelFluidityPercent))
        val width = if (isVertical) breadthPx else spanPx
        val height = if (isVertical) spanPx else breadthPx
        val (screenW, screenH) = screenSizeState.value

        val offsetPercent = resolveActiveBezelOffset(settings, isDnd = true)
        val x: Int
        val y: Int
        if (isVertical) {
            x = 0
            val centerPx = (offsetPercent * screenH).roundToInt()
            val maxOffset = (screenH - height).coerceAtLeast(0)
            y = (centerPx - height / 2).coerceIn(0, maxOffset)
        } else {
            val centerPx = (offsetPercent * screenW).roundToInt()
            val maxOffset = (screenW - width).coerceAtLeast(0)
            x = (centerPx - width / 2).coerceIn(0, maxOffset)
            y = 0
        }

        val flags = if (touchable) {
            baseOverlayFlags()
        } else {
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return WindowLayoutSpec(
            width = width,
            height = height,
            gravity = screenEdgeGravity(screenEdge),
            x = x,
            y = y,
            flags = flags
        )
    }

    private fun buildModesLayoutSpec(
        settings: NudgeSettings,
        screenEdge: ScreenEdge,
        rotation: Int,
        touchable: Boolean
    ): WindowLayoutSpec {
        val isVertical = screenEdge == ScreenEdge.LEFT || screenEdge == ScreenEdge.RIGHT
        val breadthPx = dpToPx(settings.modesThicknessDp + 90f)
        val spanPx = dpToPx(computeActionsOuterSpanDp(settings.modesLengthDp, settings.actionsPanelFluidityPercent))
        val width = if (isVertical) breadthPx else spanPx
        val height = if (isVertical) spanPx else breadthPx
        val (screenW, screenH) = screenSizeState.value

        val offsetPercent = resolveActiveBezelOffset(settings, isDnd = false)
        val x: Int
        val y: Int
        if (isVertical) {
            x = 0
            val centerPx = (offsetPercent * screenH).roundToInt()
            val maxOffset = (screenH - height).coerceAtLeast(0)
            y = (centerPx - height / 2).coerceIn(0, maxOffset)
        } else {
            val centerPx = (offsetPercent * screenW).roundToInt()
            val maxOffset = (screenW - width).coerceAtLeast(0)
            x = (centerPx - width / 2).coerceIn(0, maxOffset)
            y = 0
        }

        val flags = if (touchable) {
            baseOverlayFlags()
        } else {
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return WindowLayoutSpec(
            width = width,
            height = height,
            gravity = screenEdgeGravity(screenEdge),
            x = x,
            y = y,
            flags = flags
        )
    }

    private fun createFullModalParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            applyEdgeCutoutAndInsets()
        }
    }

    private fun WindowManager.LayoutParams.applyEdgeCutoutAndInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            fitInsetsSides = 0
        }
    }

    private fun baseOverlayFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private fun screenEdgeGravity(screenEdge: ScreenEdge): Int {
        return when (screenEdge) {
            ScreenEdge.RIGHT -> Gravity.TOP or Gravity.RIGHT
            ScreenEdge.LEFT -> Gravity.TOP or Gravity.LEFT
            ScreenEdge.TOP -> Gravity.TOP or Gravity.LEFT
            ScreenEdge.BOTTOM -> Gravity.BOTTOM or Gravity.LEFT
        }
    }

    private fun dpToPx(dp: Float): Int {
        return ceil(dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun readDisplayRotationAndSize(): Pair<Int, Pair<Int, Int>> {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
        return Pair(rotation, size)
    }

    private fun removeOverlayView(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
    }

    private data class WindowLayoutSpec(
        val width: Int,
        val height: Int,
        val gravity: Int,
        val x: Int,
        val y: Int,
        val flags: Int
    )

    private data class HudTouchSnapshot(
        val settings: NudgeSettings,
        val swipeActive: Boolean,
        val hudVisible: Boolean,
        val hasAlongsideApp: Boolean = false
    )

    companion object {
        const val ROUTINES_BRIDGE_CHANNEL_ID = "nudge_routines_bridge"
        const val ROUTINES_BRIDGE_NOTIF_ID = 9001

        @Volatile
        var instance: NudgeAccessibilityService? = null
            private set

        @Volatile
        var isScanningCustomButton: Boolean = false

        var onCustomButtonScanned: ((keyCode: Int, scanCode: Int, buttonName: String) -> Unit)? = null

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _hardwareTriggerEvents = MutableSharedFlow<HardwareTrigger>(extraBufferCapacity = 16)
        val hardwareTriggerEvents = _hardwareTriggerEvents.asSharedFlow()

        fun onHardwareTriggerFired(trigger: HardwareTrigger) {
            _hardwareTriggerEvents.tryEmit(trigger)
        }

        fun openActionsMenu() {
            instance?.let { service ->
                service.serviceScope.launch(Dispatchers.Main) {
                    service.showActionsPanel()
                }
            }
        }

        fun openDndMenu() {
            instance?.let { service ->
                service.serviceScope.launch(Dispatchers.Main) {
                    service.executeDndToggle()
                }
            }
        }

        fun openVolumeSlider() {
            instance?.let { service ->
                service.serviceScope.launch(Dispatchers.Main) {
                    service.handleHudTrigger(service.effectiveSettingsState.value, initialPress = true)
                }
            }
        }
    }
}
