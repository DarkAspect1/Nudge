package com.nudge.edge.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ShizukuStatus(val displayName: String) {
    NOT_INSTALLED("Not Installed"),
    NOT_RUNNING("Not Running"),
    PERMISSION_REQUIRED("Permission Required"),
    CONNECTED_AND_ACTIVE("Connected & Active")
}

data class ShizukuState(
    val isInstalled: Boolean = false,
    val isRunning: Boolean = false,
    val isPermissionGranted: Boolean = false
)

object ShizukuManager {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE_PERMISSION = 1001
    private const val ACCESSIBILITY_SERVICE_COMPONENT = "com.nudge.edge/com.nudge.edge.service.NudgeAccessibilityService"
    private const val ACCESSIBILITY_SERVICE_SHORT = "com.nudge.edge/.service.NudgeAccessibilityService"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _shizukuState = MutableStateFlow(ShizukuState())
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    @Volatile var isPowerButtonPhysicallyDown: Boolean = false
    @Volatile var lastPowerDownTimeMs: Long = 0L
    @Volatile var onPowerKeyLongPress: (() -> Unit)? = null
    private var powerLongPressJob: Job? = null

    private var getEventJob: Job? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        checkStatus()
    }

    private var pendingAction: (() -> Unit)? = null

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            if (granted) {
                _status.value = ShizukuStatus.CONNECTED_AND_ACTIVE
                _shizukuState.value = ShizukuState(isInstalled = true, isRunning = true, isPermissionGranted = true)
                startInputEventMonitoring()
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            } else {
                _status.value = ShizukuStatus.PERMISSION_REQUIRED
                _shizukuState.value = ShizukuState(isInstalled = true, isRunning = true, isPermissionGranted = false)
                pendingAction = null
            }
        }
    }

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        }
        checkStatus(context)
    }

    fun checkStatus(context: Context? = null) {
        var isInstalled = _shizukuState.value.isInstalled
        val pm = context?.packageManager
        if (pm != null) {
            isInstalled = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
                }
            }.isSuccess

            if (!isInstalled) {
                _status.value = ShizukuStatus.NOT_INSTALLED
                _shizukuState.value = ShizukuState(isInstalled = false, isRunning = false, isPermissionGranted = false)
                stopInputEventMonitoring()
                return
            }
        }

        if (!Shizuku.pingBinder()) {
            _status.value = ShizukuStatus.NOT_RUNNING
            _shizukuState.value = ShizukuState(isInstalled = isInstalled, isRunning = false, isPermissionGranted = false)
            stopInputEventMonitoring()
            return
        }

        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        if (granted) {
            _status.value = ShizukuStatus.CONNECTED_AND_ACTIVE
            _shizukuState.value = ShizukuState(isInstalled = true, isRunning = true, isPermissionGranted = true)
            startInputEventMonitoring()
        } else {
            _status.value = ShizukuStatus.PERMISSION_REQUIRED
            _shizukuState.value = ShizukuState(isInstalled = true, isRunning = true, isPermissionGranted = false)
            stopInputEventMonitoring()
        }
    }

    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder()
    }.getOrDefault(false)

    fun hasShizukuPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun startInputEventMonitoring() {
        if (getEventJob?.isActive == true) return
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return

        getEventJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", "getevent -ql"), null, null) as java.lang.Process
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.contains("KEY_POWER")) {
                        if (l.contains("DOWN")) {
                            isPowerButtonPhysicallyDown = true
                            lastPowerDownTimeMs = SystemClock.uptimeMillis()
                            powerLongPressJob?.cancel()
                            powerLongPressJob = scope.launch {
                                delay(500L)
                                if (isPowerButtonPhysicallyDown) {
                                    onPowerKeyLongPress?.invoke()
                                }
                            }
                        } else if (l.contains("UP")) {
                            isPowerButtonPhysicallyDown = false
                            powerLongPressJob?.cancel()
                        }
                    }
                }
                process.waitFor()
            }
        }
    }

    fun stopInputEventMonitoring() {
        powerLongPressJob?.cancel()
        powerLongPressJob = null
        getEventJob?.cancel()
        getEventJob = null
        isPowerButtonPhysicallyDown = false
    }

    fun requestPermission(context: Context? = null) {
        if (Shizuku.pingBinder()) {
            runCatching {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            }
            return
        }
        if (context != null) {
            when (_status.value) {
                ShizukuStatus.NOT_INSTALLED -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }
                ShizukuStatus.NOT_RUNNING -> {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launchIntent) }
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
                else -> {}
            }
        }
    }

    fun runWithShizuku(context: Context, action: () -> Unit) {
        if (!Shizuku.pingBinder()) {
            requestPermission(context)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            runCatching {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            }
        }
    }

    fun grantAccessibilityOnlyViaShizuku(context: Context, onComplete: (() -> Unit)? = null) {
        runWithShizuku(context) {
            scope.launch {
                val current = runCommand("settings get secure enabled_accessibility_services")
                if (!current.contains(ACCESSIBILITY_SERVICE_COMPONENT) && !current.contains(ACCESSIBILITY_SERVICE_SHORT)) {
                    val updated = if (current.isBlank() || current == "null") {
                        ACCESSIBILITY_SERVICE_COMPONENT
                    } else {
                        "$current:$ACCESSIBILITY_SERVICE_COMPONENT"
                    }
                    runCommand("settings put secure enabled_accessibility_services \"$updated\"")
                }
                runCommand("settings put secure accessibility_enabled 1")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    fun grantBatteryOptimizationOnlyViaShizuku(context: Context, onComplete: (() -> Unit)? = null) {
        runWithShizuku(context) {
            scope.launch {
                runCommand("dumpsys deviceidle whitelist +com.nudge.edge")
                runCommand("cmd appops set com.nudge.edge RUN_IN_BACKGROUND allow")
                runCommand("cmd appops set com.nudge.edge RUN_ANY_IN_BACKGROUND allow")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    fun grantDndAccessOnlyViaShizuku(context: Context, onComplete: (() -> Unit)? = null) {
        runWithShizuku(context) {
            scope.launch {
                runCommand("cmd notification allow_dnd com.nudge.edge")
                runCommand("cmd appops set com.nudge.edge ACCESS_NOTIFICATION_POLICY allow")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    fun grantNotificationsOnlyViaShizuku(context: Context, onComplete: (() -> Unit)? = null) {
        runWithShizuku(context) {
            scope.launch {
                runCommand("pm grant com.nudge.edge android.permission.POST_NOTIFICATIONS")
                runCommand("cmd appops set com.nudge.edge POST_NOTIFICATION allow")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    fun grantPowerTriggerOnlyViaShizuku(context: Context, onComplete: (() -> Unit)? = null) {
        runWithShizuku(context) {
            scope.launch {
                runCommand("settings put secure voice_interaction_service com.nudge.edge/com.nudge.edge.service.NudgeVoiceInteractionService")
                runCommand("settings put secure assistant com.nudge.edge/com.nudge.edge.service.NudgeVoiceInteractionService")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    fun grantRequiredPermissions(context: Context? = null, onComplete: (() -> Unit)? = null) {
        grantAllPermissionsViaShizuku(context, onComplete)
    }

    fun grantAllPermissionsViaShizuku(context: Context? = null, onComplete: (() -> Unit)? = null) {
        startInputEventMonitoring()
        scope.launch {
            val autoGrantCommands = listOf(
                "pm grant com.nudge.edge android.permission.WRITE_SECURE_SETTINGS",
                "pm grant com.nudge.edge android.permission.POST_NOTIFICATIONS",
                "cmd notification allow_dnd com.nudge.edge",
                "appops set com.nudge.edge ACCESS_NOTIFICATION_POLICY allow",
                "settings put secure voice_interaction_service com.nudge.edge/com.nudge.edge.service.NudgeVoiceInteractionService",
                "settings put secure assistant com.nudge.edge/com.nudge.edge.service.NudgeVoiceInteractionService",
                "dumpsys deviceidle whitelist +com.nudge.edge",
                "cmd deviceidle whitelist +com.nudge.edge",
                "cmd appops set com.nudge.edge RUN_IN_BACKGROUND allow",
                "cmd appops set com.nudge.edge RUN_ANY_IN_BACKGROUND allow",
                "cmd appops set com.nudge.edge SYSTEM_ALERT_WINDOW allow"
            )
            for (cmd in autoGrantCommands) {
                runCommand(cmd)
            }
            val current = runCommand("settings get secure enabled_accessibility_services")
            if (!current.contains(ACCESSIBILITY_SERVICE_COMPONENT) && !current.contains(ACCESSIBILITY_SERVICE_SHORT)) {
                val updated = if (current.isBlank() || current == "null") {
                    ACCESSIBILITY_SERVICE_SHORT
                } else {
                    "$current:$ACCESSIBILITY_SERVICE_SHORT"
                }
                runCommand("settings put secure enabled_accessibility_services '$updated'")
            }
            runCommand("settings put secure accessibility_enabled 1")
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    fun runCommand(cmd: String): String {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        return runCatching {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        }.getOrDefault("")
    }

    fun enableAccessibility(context: Context): Boolean {

        var success = runCatching {
            val cr = context.contentResolver
            val existing = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            if (!existing.contains(ACCESSIBILITY_SERVICE_COMPONENT) && !existing.contains(ACCESSIBILITY_SERVICE_SHORT)) {
                val updated = if (existing.isBlank()) ACCESSIBILITY_SERVICE_SHORT else "$existing:$ACCESSIBILITY_SERVICE_SHORT"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            }
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            true
        }.getOrDefault(false)

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val current = runCommand("settings get secure enabled_accessibility_services")
            if (!current.contains(ACCESSIBILITY_SERVICE_COMPONENT) && !current.contains(ACCESSIBILITY_SERVICE_SHORT)) {
                val updated = if (current.isBlank() || current == "null") {
                    ACCESSIBILITY_SERVICE_SHORT
                } else {
                    "$current:$ACCESSIBILITY_SERVICE_SHORT"
                }
                runCommand("settings put secure enabled_accessibility_services '$updated'")
            }
            runCommand("settings put secure accessibility_enabled 1")
            success = true
        }
        return success
    }

    fun disableAccessibility(context: Context): Boolean {
        var success = runCatching {
            val cr = context.contentResolver
            val existing = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val remaining = existing.split(":")
                .filterNot { it.contains("com.nudge.edge") }
                .joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, remaining)
            if (remaining.isBlank()) {
                Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "0")
            }
            true
        }.getOrDefault(false)

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val current = runCommand("settings get secure enabled_accessibility_services")
            val remaining = current.split(":")
                .filterNot { it.contains("com.nudge.edge") }
                .joinToString(":")
            runCommand("settings put secure enabled_accessibility_services '$remaining'")
            if (remaining.isBlank()) {
                runCommand("settings put secure accessibility_enabled 0")
            }
            success = true
        }
        return success
    }

    fun getForegroundPackageViaShizuku(): String? {
        val windowOut = runCommand("dumpsys window displays")
        if (windowOut.isNotBlank()) {
            val focusRegex = Regex("""mCurrentFocus=Window\{[a-f0-9]+\s+[^\s]+\s+([a-zA-Z0-9_.]+)/""")
            val m = focusRegex.find(windowOut)
            if (m != null) {
                val pkg = m.groupValues[1]
                if (pkg.isNotBlank() && pkg != "null") return pkg
            }
        }
        val out = runCommand("dumpsys activity activities")
        if (out.isBlank()) return null

        val match = Regex("""(?:mResumedActivity|topResumedActivity)=ActivityRecord\{[a-f0-9]+\s+[^\s]+\s+([a-zA-Z0-9_.]+)/""").find(out)
            ?: Regex("""(?:mResumedActivity|topResumedActivity):\s+ActivityRecord\{[a-f0-9]+\s+[^\s]+\s+([a-zA-Z0-9_.]+)/""").find(out)
            ?: Regex("""([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]*""").find(out)
        return match?.groupValues?.getOrNull(1)
    }
}
