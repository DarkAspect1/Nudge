package com.nudge.edge.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class ActiveMediaApp(
    val uid: Int,
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable,
    val currentSteps: Int,
    val maxVolumeLimit: Int
) {
    val fillFraction: Float
        get() = (currentSteps.toFloat() / maxVolumeLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val volumeFraction: Float
        get() = fillFraction
}

class AppVolumeController(
    private val context: Context,
    private val audioManager: AudioManager,
    private val fineVolumeController: FineVolumeController
) {
    private val packageManager = context.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val perAppFractionMap = ConcurrentHashMap<Int, Float>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val usageByUid = ConcurrentHashMap<Int, Int>()
    private val activeProxiesCache = ConcurrentHashMap<Int, List<Any>>()

    private val _activeMediaApps = MutableStateFlow<List<ActiveMediaApp>>(emptyList())
    val activeMediaApps: StateFlow<List<ActiveMediaApp>> = _activeMediaApps.asStateFlow()

    var currentGlobalSubStepGain: Float = 1.0f

    @Volatile
    var currentForegroundPackage: String = ""

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            refreshAndEnforceActiveApps(currentGlobalSubStepGain)
        }
    }

    init {
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        refreshAndEnforceActiveApps()
    }

    fun release() {
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
    }

    fun clear() {
        perAppFractionMap.clear()
        iconCache.clear()
        usageByUid.clear()
        activeProxiesCache.clear()
        _activeMediaApps.value = emptyList()
    }

    private fun isSystemApp(packageName: String): Boolean {
        if (packageName.isBlank() ||
            packageName == context.packageName ||
            packageName == "android" ||
            packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.inputmethod") ||
            packageName.startsWith("com.google.android.tts") ||
            packageName.startsWith("com.google.android.gms") ||
            packageName.startsWith("com.google.android.feedback") ||
            packageName.startsWith("com.sec.android.app.launcher") ||
            packageName.contains("systemui") ||
            packageName.contains("launcher") ||
            packageName.contains("dialer") ||
            packageName.contains("incallui")
        ) {
            return true
        }

        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSys = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLaunchIntent = packageManager.getLaunchIntentForPackage(packageName) != null
            isSys && (!hasLaunchIntent || !isUpdatedSys && packageName.startsWith("com.google.android.apps.nexuslauncher"))
        }.getOrDefault(false)
    }

    fun refreshAndEnforceActiveApps(globalSubStepGain: Float = currentGlobalSubStepGain) {
        currentGlobalSubStepGain = globalSubStepGain
        val rawConfigs = fineVolumeController.getRawActivePlaybackConfigurations()
        if (rawConfigs.isNullOrEmpty()) {
            _activeMediaApps.value = emptyList()
            return
        }

        val currentLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val activeProxiesByUid = linkedMapOf<Int, MutableList<Any>>()
        this.usageByUid.clear()

        for (config in rawConfigs) {
            val state = fineVolumeController.getPlayerState(config)
            val uid = fineVolumeController.getClientUid(config)
            val usage = fineVolumeController.getAudioUsage(config)

            if (state == 2 && uid >= 10000) {
                val proxy = fineVolumeController.getPlayerProxy(config)
                if (proxy != null) {
                    activeProxiesByUid.getOrPut(uid) { mutableListOf() }.add(proxy)
                    if (!this.usageByUid.containsKey(uid) || usage == 1 || usage == 14) {
                        this.usageByUid[uid] = usage
                    }
                }
            }
        }

        activeProxiesCache.clear()
        for ((uid, proxies) in activeProxiesByUid) {
            activeProxiesCache[uid] = proxies.toList()
            val savedFraction = perAppFractionMap.getOrPut(uid) { 1.0f }
            val effectiveGain = (savedFraction * globalSubStepGain).coerceIn(0f, 1f)
            proxies.forEach { proxy ->
                fineVolumeController.setPlayerProxyVolume(proxy, effectiveGain)
                fineVolumeController.setPlayerProxyVolume(proxy, effectiveGain)
            }
        }

        val candidatePackagesByUid = linkedMapOf<Int, String>()
        for (uid in activeProxiesByUid.keys) {
            val packages = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull() ?: continue
            val matched = packages.firstOrNull { it == currentForegroundPackage }
                ?: packages.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
                ?: packages.firstOrNull { !it.contains("provider") && !it.contains("overlay") }
                ?: packages.firstOrNull()
                ?: continue

            if (isSystemApp(matched)) continue

            val isGame = (usageByUid[uid] == 14) || runCatching {
                val appInfo = packageManager.getApplicationInfo(matched, 0)
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                    ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
            }.getOrDefault(false)

            val isForeground = (matched == currentForegroundPackage || packages.contains(currentForegroundPackage)) || runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.runningAppProcesses?.any { proc ->
                    proc.uid == uid && proc.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                } == true
            }.getOrDefault(false)

            if (isGame && !isForeground) continue

            candidatePackagesByUid[uid] = matched
        }

        if (candidatePackagesByUid.isEmpty()) {
            _activeMediaApps.value = emptyList()
            return
        }

        val appsList = mutableListOf<ActiveMediaApp>()
        for ((uid, pkgName) in candidatePackagesByUid) {
            val savedFraction = perAppFractionMap.getOrPut(uid) { 1.0f }
            val icon = iconCache.getOrPut(pkgName) {
                runCatching { packageManager.getApplicationIcon(pkgName) }.getOrNull()
                    ?: packageManager.getLaunchIntentForPackage(pkgName)?.resolveActivityInfo(packageManager, 0)?.loadIcon(packageManager)
                    ?: packageManager.defaultActivityIcon
            }

            val rawLabel = runCatching {
                val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrNull() ?: runCatching {
                val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
                launchIntent?.resolveActivityInfo(packageManager, 0)?.loadLabel(packageManager)?.toString()
            }.getOrNull()

            val label = if (rawLabel.isNullOrBlank() || rawLabel.equals("unknown", ignoreCase = true)) {
                pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            } else {
                rawLabel
            }

            val appSteps = (savedFraction * currentLimit.toFloat()).roundToInt().coerceIn(0, currentLimit)
            appsList.add(
                ActiveMediaApp(
                    uid = uid,
                    packageName = pkgName,
                    appLabel = label,
                    appIcon = icon,
                    currentSteps = appSteps,
                    maxVolumeLimit = currentLimit
                )
            )
        }

        _activeMediaApps.value = appsList
    }

    fun getPriorityAlongsideApp(): ActiveMediaApp? {
        val apps = _activeMediaApps.value
        if (apps.isEmpty()) return null
        val fgPkg = currentForegroundPackage
        if (fgPkg.isNotBlank()) {
            val fgApp = apps.firstOrNull { it.packageName == fgPkg }
            if (fgApp != null) return fgApp
        }
        return apps.firstOrNull { usageByUid[it.uid] != 14 } ?: apps.firstOrNull()
    }

    fun setAppVolumeSteps(uid: Int, targetSteps: Int) {
        val currentLimit = fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val fraction = (targetSteps.coerceIn(0, currentLimit).toFloat() / currentLimit.toFloat()).coerceIn(0f, 1f)
        perAppFractionMap[uid] = fraction
        val proxies = activeProxiesCache[uid]
        if (!proxies.isNullOrEmpty()) {
            val effectiveGain = (fraction * currentGlobalSubStepGain).coerceIn(0f, 1f)
            proxies.forEach { proxy ->
                fineVolumeController.setPlayerProxyVolume(proxy, effectiveGain)
            }
        }
        val currentList = _activeMediaApps.value
        if (currentList.isNotEmpty()) {
            _activeMediaApps.value = currentList.map { app ->
                if (app.uid == uid) {
                    app.copy(currentSteps = targetSteps.coerceIn(0, currentLimit))
                } else app
            }
        }
        refreshAndEnforceActiveApps(currentGlobalSubStepGain)
    }

    fun setAppVolumeSteps(
        uid: Int,
        targetSteps: Int,
        maxLimitOverride: Int? = null,
        globalSubStepGain: Float = currentGlobalSubStepGain
    ) {
        val currentLimit = maxLimitOverride ?: fineVolumeController.dynamicMaxLimit.value.coerceAtLeast(1)
        val fraction = (targetSteps.coerceIn(0, currentLimit).toFloat() / currentLimit.toFloat()).coerceIn(0f, 1f)
        perAppFractionMap[uid] = fraction
        val proxies = activeProxiesCache[uid]
        if (!proxies.isNullOrEmpty()) {
            val effectiveGain = (fraction * globalSubStepGain).coerceIn(0f, 1f)
            proxies.forEach { proxy ->
                fineVolumeController.setPlayerProxyVolume(proxy, effectiveGain)
            }
        }
        val currentList = _activeMediaApps.value
        if (currentList.isNotEmpty()) {
            _activeMediaApps.value = currentList.map { app ->
                if (app.uid == uid) {
                    app.copy(currentSteps = targetSteps.coerceIn(0, currentLimit))
                } else app
            }
        }
        refreshAndEnforceActiveApps(globalSubStepGain)
    }
}
