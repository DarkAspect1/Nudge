package com.nudge.edge

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nudge.edge.data.SettingsRepository
import com.nudge.edge.data.VolumeController
import com.nudge.edge.service.NudgeAccessibilityService
import com.nudge.edge.ui.settings.MainScreen
import com.nudge.edge.ui.theme.NudgeTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var volumeController: VolumeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        settingsRepository = SettingsRepository.getInstance(this)
        volumeController = VolumeController(this)

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val settings by settingsRepository.settings.collectAsState()
            val previewTarget by settingsRepository.previewTarget.collectAsState()
            val preferencesLoaded by settingsRepository.preferencesLoaded.collectAsState()
            val lastPromptedVersion by settingsRepository.lastPromptedAccessibilityVersion.collectAsState()
            val serviceRunning by NudgeAccessibilityService.isServiceRunning.collectAsState()

            var serviceSettingEnabled by remember {
                mutableStateOf(isAccessibilityServiceEnabled(context))
            }
            var musicVolumeFraction by remember {
                mutableFloatStateOf(volumeController.getMusicVolumeFraction())
            }
            var musicMaxVolume by remember {
                mutableIntStateOf(volumeController.getMusicMaxVolume())
            }
            var screenDimsDp by remember {
                mutableStateOf(readRealScreenMetricsDp(context))
            }
            var displayRotation by remember {
                mutableIntStateOf(readDisplayRotation(context))
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            serviceSettingEnabled = isAccessibilityServiceEnabled(context)
                            musicMaxVolume = volumeController.getMusicMaxVolume()
                            musicVolumeFraction = volumeController.getMusicVolumeFraction()
                            screenDimsDp = readRealScreenMetricsDp(context)
                            displayRotation = readDisplayRotation(context)
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            settingsRepository.clearLivePreview()
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(context) {
                val displayManager =
                    context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val displayListener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit
                    override fun onDisplayRemoved(displayId: Int) = Unit
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
                            displayRotation = readDisplayRotation(context)
                            screenDimsDp = readRealScreenMetricsDp(context)
                        }
                    }
                }
                displayManager.registerDisplayListener(
                    displayListener,
                    Handler(Looper.getMainLooper())
                )
                onDispose {
                    displayManager.unregisterDisplayListener(displayListener)
                }
            }

            DisposableEffect(context) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                            val stream = intent.getIntExtra(
                                "android.media.EXTRA_VOLUME_STREAM_TYPE",
                                -1
                            )
                            if (stream == AudioManager.STREAM_MUSIC) {
                                musicMaxVolume = volumeController.getMusicMaxVolume()
                                musicVolumeFraction = volumeController.getMusicVolumeFraction()
                            }
                        }
                    }
                }
                val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                registerVolumeReceiver(context, receiver, filter)
                onDispose {
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = settings.lightMode
                controller.isAppearanceLightNavigationBars = settings.lightMode
            }

            NudgeTheme(darkTheme = !settings.lightMode) {
                MainScreen(
                    settings = settings,
                    previewTarget = previewTarget,
                    serviceActive = serviceRunning || serviceSettingEnabled,
                    showAccessibilityPrompt = preferencesLoaded &&
                        !(serviceRunning || serviceSettingEnabled) &&
                        lastPromptedVersion < BuildConfig.VERSION_CODE,
                    musicVolumeFraction = musicVolumeFraction,
                    musicMaxVolume = musicMaxVolume,
                    realScreenWidthDp = screenDimsDp.first,
                    realScreenHeightDp = screenDimsDp.second,
                    displayRotation = displayRotation,
                    repository = settingsRepository,
                    volumeController = volumeController,
                    onVolumeFractionUpdated = { musicVolumeFraction = it },
                    onDismissAccessibilityPrompt = {
                        settingsRepository.markAccessibilityPromptHandled(BuildConfig.VERSION_CODE)
                    },
                    onOpenAccessibilitySettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerVolumeReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun readDisplayRotation(context: Context): Int {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }

    private fun readRealScreenMetricsDp(context: Context): Pair<Float, Float> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width() / density, bounds.height() / density)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels / density, metrics.heightPixels / density)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, NudgeAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expectedComponent
        }
    }
}
