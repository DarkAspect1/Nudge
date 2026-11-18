package com.nudge.edge.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class ActiveMediaApp(
    val uid: Int,
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable,
    val volumeFraction: Float,
    val maxVolumeLimit: Int = 100
) {
    val fillFraction: Float
        get() = volumeFraction.coerceIn(0f, 1f)
    val currentSteps: Int
        get() = (fillFraction * maxVolumeLimit.coerceAtLeast(1).toFloat()).roundToInt()
            .coerceIn(0, maxVolumeLimit.coerceAtLeast(1))
}

class AppVolumeController(
    private val context: Context,
    private val audioManager: AudioManager
) {
    private val packageManager = context.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val perAppFractionMap = ConcurrentHashMap<Int, Float>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val usageByUid = ConcurrentHashMap<Int, Int>()
    private val activeProxiesCache = ConcurrentHashMap<Int, List<Any>>()

    private val _activeMediaApps = MutableStateFlow<List<ActiveMediaApp>>(emptyList())
    val activeMediaApps: StateFlow<List<ActiveMediaApp>> = _activeMediaApps.asStateFlow()

    @Volatile
    var currentForegroundPackage: String = ""

    @Volatile
    var isPerAppVolumeEnabled: Boolean = false

    private var iAudioService: Any? = null
    private var getActiveConfigsMethod: Method? = null
    private var getClientUidMethod: Method? = null
    private var getPlayerStateMethod: Method? = null
    private var getPlayerProxyMethod: Method? = null
    private var playerSetVolumeMethod: Method? = null
    private var iPlayerSetVolumeMethod: Method? = null
    private var clientUidField: Field? = null
    private var playerStateField: Field? = null
    private var iPlayerField: Field? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            refreshActiveApps()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
        bindShizukuAudioService()
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        refreshActiveApps()
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

    fun bindShizukuAudioService(): Boolean {
        return runCatching {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            }

            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java).apply {
                isAccessible = true
            }
            val rawBinder = getServiceMethod.invoke(null, Context.AUDIO_SERVICE) as? IBinder ?: return false
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            val stubClass = Class.forName("android.media.IAudioService\$Stub")
            val asInterfaceMethod = stubClass.getDeclaredMethod("asInterface", IBinder::class.java).apply {
                isAccessible = true
            }
            val serviceInstance = asInterfaceMethod.invoke(null, wrappedBinder) ?: return false

            val activeConfigsMethod = serviceInstance.javaClass.getDeclaredMethod("getActivePlaybackConfigurations").apply {
                isAccessible = true
            }
            activeConfigsMethod.invoke(serviceInstance) as? List<*> ?: return false

            val apcClass = AudioPlaybackConfiguration::class.java
            getClientUidMethod = runCatching {
                apcClass.getDeclaredMethod("getClientUid").apply { isAccessible = true }
            }.getOrNull()
            getPlayerStateMethod = runCatching {
                apcClass.getDeclaredMethod("getPlayerState").apply { isAccessible = true }
            }.getOrNull()
            getPlayerProxyMethod = runCatching {
                apcClass.getDeclaredMethod("getPlayerProxy").apply { isAccessible = true }
            }.getOrNull()

            clientUidField = runCatching {
                apcClass.getDeclaredField("mClientUid").apply { isAccessible = true }
            }.getOrNull()
            playerStateField = runCatching {
                apcClass.getDeclaredField("mPlayerState").apply { isAccessible = true }
            }.getOrNull()
            iPlayerField = runCatching {
                apcClass.getDeclaredField("mIPlayer").apply { isAccessible = true }
            }.getOrNull()

            val playerProxyClass = Class.forName("android.media.PlayerProxy")
            playerSetVolumeMethod = runCatching {
                playerProxyClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType).apply {
                    isAccessible = true
                }
            }.getOrNull()

            val iPlayerClass = Class.forName("android.media.IPlayer")
            iPlayerSetVolumeMethod = runCatching {
                iPlayerClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType).apply {
                    isAccessible = true
                }
            }.getOrNull()

            iAudioService = serviceInstance
            getActiveConfigsMethod = activeConfigsMethod
            true
        }.getOrDefault(false)
    }

    private fun getRawActivePlaybackConfigurations(): List<Any>? {
        if (iAudioService == null || getActiveConfigsMethod == null) {
            if (!bindShizukuAudioService()) return null
        }
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            getActiveConfigsMethod?.invoke(iAudioService) as? List<Any>
        }.getOrNull()
    }

    private fun getClientUid(config: Any): Int {
        return runCatching {
            (getClientUidMethod?.invoke(config) as? Int)
                ?: (clientUidField?.getInt(config))
                ?: -1
        }.getOrDefault(-1)
    }

    private fun getPlayerState(config: Any): Int {
        return runCatching {
            (getPlayerStateMethod?.invoke(config) as? Int)
                ?: (playerStateField?.getInt(config))
                ?: -1
        }.getOrDefault(-1)
    }

    private fun getPlayerProxy(config: Any): Any? {
        return runCatching {
            getPlayerProxyMethod?.invoke(config) ?: iPlayerField?.get(config)
        }.getOrNull()
    }

    private fun getAudioUsage(config: Any): Int {
        return runCatching {
            if (config is AudioPlaybackConfiguration) {
                config.audioAttributes?.usage ?: 0
            } else {
                val method = config.javaClass.getMethod("getAudioAttributes")
                val attrs = method.invoke(config)
                val usageMethod = attrs?.javaClass?.getMethod("getUsage")
                usageMethod?.invoke(attrs) as? Int ?: 0
            }
        }.getOrDefault(0)
    }

    private fun setPlayerProxyVolume(proxyOrIPlayer: Any, volume: Float): Boolean {
        val clamped = volume.coerceIn(0f, 1f)
        return runCatching {
            when {
                playerSetVolumeMethod != null && proxyOrIPlayer.javaClass.name == "android.media.PlayerProxy" -> {
                    playerSetVolumeMethod?.invoke(proxyOrIPlayer, clamped)
                    true
                }
                iPlayerSetVolumeMethod != null -> {
                    iPlayerSetVolumeMethod?.invoke(proxyOrIPlayer, clamped)
                    true
                }
                else -> {
                    val m = proxyOrIPlayer.javaClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
                    m.isAccessible = true
                    m.invoke(proxyOrIPlayer, clamped)
                    true
                }
            }
        }.getOrDefault(false)
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

    fun refreshActiveApps() {
        val rawConfigs = getRawActivePlaybackConfigurations()
        if (rawConfigs.isNullOrEmpty()) {
            activeProxiesCache.clear()
            _activeMediaApps.value = emptyList()
            return
        }

        val activeProxiesByUid = linkedMapOf<Int, MutableList<Any>>()
        usageByUid.clear()

        for (config in rawConfigs) {
            val state = getPlayerState(config)
            val uid = getClientUid(config)
            val usage = getAudioUsage(config)

            if (state == 2 && uid >= 10000) {
                val proxy = getPlayerProxy(config)
                if (proxy != null) {
                    activeProxiesByUid.getOrPut(uid) { mutableListOf() }.add(proxy)
                    if (!usageByUid.containsKey(uid) || usage == 1 || usage == 14) {
                        usageByUid[uid] = usage
                    }
                }
            }
        }

        activeProxiesCache.clear()
        for ((uid, proxies) in activeProxiesByUid) {
            activeProxiesCache[uid] = proxies.toList()
            val directGain = if (isPerAppVolumeEnabled) {
                (perAppFractionMap.getOrPut(uid) { 1.0f }).coerceIn(0f, 1f)
            } else {
                1.0f
            }
            proxies.forEach { proxy ->
                setPlayerProxyVolume(proxy, directGain)
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
            val savedFraction = perAppFractionMap.getOrPut(uid) { 1.0f }.coerceIn(0f, 1f)
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

            appsList.add(
                ActiveMediaApp(
                    uid = uid,
                    packageName = pkgName,
                    appLabel = label,
                    appIcon = icon,
                    volumeFraction = savedFraction
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

    fun setAppVolume(uid: Int, newFraction: Float) {
        val clamped = newFraction.coerceIn(0f, 1f)
        perAppFractionMap[uid] = clamped
        val proxies = activeProxiesCache[uid]
        if (!proxies.isNullOrEmpty()) {
            proxies.forEach { proxy ->
                setPlayerProxyVolume(proxy, clamped)
            }
        }
        val currentList = _activeMediaApps.value
        if (currentList.isNotEmpty()) {
            _activeMediaApps.value = currentList.map { app ->
                if (app.uid == uid) {
                    app.copy(volumeFraction = clamped)
                } else {
                    app
                }
            }
        }
    }
}
