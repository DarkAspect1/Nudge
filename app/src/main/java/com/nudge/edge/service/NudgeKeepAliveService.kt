package com.nudge.edge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nudge.edge.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NudgeKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var keepAliveJob: Job? = null
    private var cloakingPollJob: Job? = null
    @Volatile private var isCloakingActive = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                restoreFromCloak()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ShizukuManager.init(this)
        createNotificationChannel()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)

        observeSettings()
        startKeepAliveWatcher()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nudge Service Stability",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps overlay key interception responsive and stable across OEM battery managers."
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun observeSettings() {
        val repo = SettingsRepository.getInstance(applicationContext)
        serviceScope.launch {
            repo.settings.collectLatest { settings ->
                if (settings.cloakedPackages.isEmpty() && (_isCurrentlyCloaked.value || isCloakingActive)) {
                    restoreFromCloak()
                }
            }
        }
    }

    fun cloakAccessibility() {
        if (isCloakingActive) return
        isCloakingActive = true
        _isCurrentlyCloaked.value = true

        NudgeAccessibilityService.instance?.dismissAllOverlaysForCloakedApp()

        if (ShizukuManager.hasShizukuPermission()) {
            ShizukuManager.disableAccessibility(applicationContext)
        }

        startCloakPolling()
    }

    fun restoreFromCloak() {
        if (!isCloakingActive && !_isCurrentlyCloaked.value) return
        isCloakingActive = false
        _isCurrentlyCloaked.value = false
        cloakingPollJob?.cancel()
        cloakingPollJob = null

        val settings = SettingsRepository.getInstance(applicationContext).settings.value
        if (settings.isAutoKeepAliveViaShizuku || ShizukuManager.hasShizukuPermission()) {
            ShizukuManager.enableAccessibility(applicationContext)
        }
    }

    private fun startCloakPolling() {
        cloakingPollJob?.cancel()
        cloakingPollJob = serviceScope.launch {
            val repo = SettingsRepository.getInstance(applicationContext)
            while (isCloakingActive && isActive) {
                delay(700L)
                val settings = repo.settings.value
                val cloakedSet = settings.cloakedPackages
                if (cloakedSet.isEmpty()) {
                    restoreFromCloak()
                    break
                }
                val fgPkg = ShizukuManager.getForegroundPackageViaShizuku()
                if (fgPkg != null && !cloakedSet.contains(fgPkg)) {
                    restoreFromCloak()
                    break
                }
            }
        }
    }

    private fun startKeepAliveWatcher() {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            val repo = SettingsRepository.getInstance(applicationContext)
            while (isActive) {
                delay(4000L)
                val settings = repo.settings.value

                if (settings.isAutoKeepAliveViaShizuku) {
                    val accService = NudgeAccessibilityService.instance
                    val isServiceCloaked = accService?.isCurrentAppExcludedFast == true
                    val currentPkg = ShizukuManager.getForegroundPackageViaShizuku()
                    val isPkgCloaked = currentPkg != null && settings.cloakedPackages.contains(currentPkg)
                    val isCloaked = if (currentPkg != null) {
                        isPkgCloaked
                    } else {
                        isServiceCloaked
                    }
                    if (isCloaked) {
                        if (!isCloakingActive) {
                            cloakAccessibility()
                        }
                    } else {
                        if (isCloakingActive) {
                            restoreFromCloak()
                        } else if (accService == null) {
                            ShizukuManager.enableAccessibility(applicationContext)
                        }
                    }
                }
            }
        }
    }

    fun handleAppWindowChanged(packageName: String) {
        val repo = SettingsRepository.getInstance(applicationContext)
        val settings = repo.settings.value

        val cloakedSet = settings.cloakedPackages
        if (cloakedSet.isEmpty()) {
            if (_isCurrentlyCloaked.value || isCloakingActive) {
                restoreFromCloak()
            }
            return
        }

        val isSystemChrome = packageName == "com.android.systemui" ||
            packageName == "android" ||
            packageName.contains("systemui") ||
            packageName.contains("inputmethod") ||
            packageName.contains("honeyboard")

        if (isSystemChrome) {
            return
        }

        val shouldCloak = cloakedSet.contains(packageName)
        if (shouldCloak) {
            cloakAccessibility()
        } else {
            restoreFromCloak()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        val targetPkg = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        if (!targetPkg.isNullOrBlank()) {
            handleAppWindowChanged(targetPkg)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        keepAliveJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "nudge_keep_alive"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        @Volatile
        var instance: NudgeKeepAliveService? = null
            private set

        private val _isCurrentlyCloaked = MutableStateFlow(false)
        val isCurrentlyCloaked: StateFlow<Boolean> = _isCurrentlyCloaked.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, NudgeKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startService(intent) }
            } else {
                context.startService(intent)
            }
        }

        fun reportPackageChanged(context: Context, packageName: String) {
            instance?.handleAppWindowChanged(packageName) ?: run {
                val intent = Intent(context, NudgeKeepAliveService::class.java).apply {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
                context.startService(intent)
            }
        }
    }
}
