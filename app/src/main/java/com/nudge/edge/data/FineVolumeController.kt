package com.nudge.edge.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class VolumeEngineMode {
    OEM_FINE_VOLUME,
    NATIVE_HIGH_SCALE,
    SHIZUKU_HYBRID_FINE,
    NATIVE_COARSE
}

class FineVolumeController(
    private val context: Context,
    private val audioManager: AudioManager
) {
    private var semGetFineVolumeMethod: Method? = null
    private var semSetFineVolumeMethod: Method? = null

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

    companion object {
        private val _staticEngineMode = MutableStateFlow(VolumeEngineMode.NATIVE_COARSE)
        val currentEngineMode: StateFlow<VolumeEngineMode> = _staticEngineMode.asStateFlow()
    }

    private val _currentEngineMode = MutableStateFlow(VolumeEngineMode.NATIVE_COARSE)
    val currentEngineMode: StateFlow<VolumeEngineMode> = _currentEngineMode.asStateFlow()

    private val _isShizukuAudioReady = MutableStateFlow(false)
    val isShizukuAudioReady: StateFlow<Boolean> = _isShizukuAudioReady.asStateFlow()

    private var subStepMultiplier: Int = 1
    private val _dynamicMaxLimit = MutableStateFlow(getNativeHardwareRange(AudioManager.STREAM_MUSIC).second)
    val dynamicMaxLimit: StateFlow<Int> = _dynamicMaxLimit.asStateFlow()
    val maxMediaVolumeLimit: StateFlow<Int> get() = dynamicMaxLimit

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
        verifyAndResolveEngineMode()
    }

    fun getNativeHardwareRange(streamType: Int): Pair<Int, Int> {
        val minIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audioManager.getStreamMinVolume(streamType) }.getOrDefault(0)
        } else 0
        val maxIdx = runCatching { audioManager.getStreamMaxVolume(streamType) }.getOrDefault(15)
        val range = (maxIdx - minIdx).coerceAtLeast(1)
        return minIdx to range
    }

    fun verifyAndResolveEngineMode(): VolumeEngineMode {
        val (_, nativeRange) = getNativeHardwareRange(AudioManager.STREAM_MUSIC)

        val shizukuReady = bindAndVerifyShizukuAudioService()
        _isShizukuAudioReady.value = shizukuReady

        val oemFineResolved = runCatching {
            val getMethod = AudioManager::class.java.getMethod("semGetFineVolume", Int::class.javaPrimitiveType)
            val setMethod = AudioManager::class.java.getMethod(
                "semSetFineVolume",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val initialFine = getMethod.invoke(audioManager, AudioManager.STREAM_MUSIC) as Int
            if (initialFine >= 0) {
                setMethod.invoke(audioManager, AudioManager.STREAM_MUSIC, initialFine, 0)
                val verifyFine = getMethod.invoke(audioManager, AudioManager.STREAM_MUSIC) as Int
                if (initialFine == verifyFine) {
                    semGetFineVolumeMethod = getMethod
                    semSetFineVolumeMethod = setMethod
                    true
                } else false
            } else false
        }.getOrDefault(false)

        if (oemFineResolved) {
            subStepMultiplier = 10
            _dynamicMaxLimit.value = nativeRange * subStepMultiplier
            _currentEngineMode.value = VolumeEngineMode.OEM_FINE_VOLUME
            _staticEngineMode.value = VolumeEngineMode.OEM_FINE_VOLUME
            return VolumeEngineMode.OEM_FINE_VOLUME
        }

        if (nativeRange > 35) {
            subStepMultiplier = 1
            _dynamicMaxLimit.value = nativeRange
            _currentEngineMode.value = VolumeEngineMode.NATIVE_HIGH_SCALE
            _staticEngineMode.value = VolumeEngineMode.NATIVE_HIGH_SCALE
            return VolumeEngineMode.NATIVE_HIGH_SCALE
        }

        if (shizukuReady) {
            subStepMultiplier = 10
            _dynamicMaxLimit.value = nativeRange * subStepMultiplier
            _currentEngineMode.value = VolumeEngineMode.SHIZUKU_HYBRID_FINE
            _staticEngineMode.value = VolumeEngineMode.SHIZUKU_HYBRID_FINE
            return VolumeEngineMode.SHIZUKU_HYBRID_FINE
        }

        subStepMultiplier = 1
        _dynamicMaxLimit.value = nativeRange
        _currentEngineMode.value = VolumeEngineMode.NATIVE_COARSE
        _staticEngineMode.value = VolumeEngineMode.NATIVE_COARSE
        return VolumeEngineMode.NATIVE_COARSE
    }

    fun isCoarseHardwareOnly(): Boolean {
        return _currentEngineMode.value == VolumeEngineMode.NATIVE_COARSE
    }

    fun bindAndVerifyShizukuAudioService(): Boolean {
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
            val probe1 = activeConfigsMethod.invoke(serviceInstance) as? List<*> ?: return false
            val probe2 = activeConfigsMethod.invoke(serviceInstance) as? List<*> ?: return false

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
            _isShizukuAudioReady.value = true
            true
        }.getOrDefault(false)
    }

    fun getRawActivePlaybackConfigurations(): List<Any>? {
        if (iAudioService == null || getActiveConfigsMethod == null) {
            if (!bindAndVerifyShizukuAudioService()) return null
        }
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            getActiveConfigsMethod?.invoke(iAudioService) as? List<Any>
        }.getOrNull()
    }

    fun getClientUid(config: Any): Int {
        return runCatching {
            (getClientUidMethod?.invoke(config) as? Int)
                ?: (clientUidField?.getInt(config))
                ?: -1
        }.getOrDefault(-1)
    }

    fun getPlayerState(config: Any): Int {
        return runCatching {
            (getPlayerStateMethod?.invoke(config) as? Int)
                ?: (playerStateField?.getInt(config))
                ?: -1
        }.getOrDefault(-1)
    }

    fun getPlayerProxy(config: Any): Any? {
        return runCatching {
            getPlayerProxyMethod?.invoke(config) ?: iPlayerField?.get(config)
        }.getOrNull()
    }

    fun getAudioUsage(config: Any): Int {
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

    fun setPlayerProxyVolume(proxyOrIPlayer: Any, gain: Float): Boolean {
        val clamped = gain.coerceIn(0f, 1f)
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

    fun getStreamCurrentSteps(streamType: Int): Int {
        val (minIdx, nativeRange) = getNativeHardwareRange(streamType)
        val limit = _dynamicMaxLimit.value.coerceAtLeast(1)

        if (streamType == AudioManager.STREAM_MUSIC && _currentEngineMode.value == VolumeEngineMode.OEM_FINE_VOLUME) {
            val fineVal = runCatching {
                semGetFineVolumeMethod?.invoke(audioManager, AudioManager.STREAM_MUSIC) as? Int
            }.getOrNull()
            if (fineVal != null) return fineVal.coerceIn(0, limit)
        }

        val currentNative = (audioManager.getStreamVolume(streamType) - minIdx).coerceIn(0, nativeRange)
        return ((currentNative.toFloat() / nativeRange.toFloat()) * limit.toFloat()).roundToInt().coerceIn(0, limit)
    }

    fun applyStreamSteps(streamType: Int, targetSteps: Int, flags: Int = 0): Float {
        val (minIdx, nativeRange) = getNativeHardwareRange(streamType)
        val limit = _dynamicMaxLimit.value.coerceAtLeast(1)
        val clampedSteps = targetSteps.coerceIn(0, limit)
        val normalized = clampedSteps.toFloat() / limit.toFloat()

        if (streamType == AudioManager.STREAM_MUSIC) {
            when (_currentEngineMode.value) {
                VolumeEngineMode.OEM_FINE_VOLUME -> {
                    runCatching {
                        semSetFineVolumeMethod?.invoke(audioManager, AudioManager.STREAM_MUSIC, clampedSteps, flags)
                        val verify = semGetFineVolumeMethod?.invoke(audioManager, AudioManager.STREAM_MUSIC) as? Int
                        if (verify != null && verify != clampedSteps) {
                            semSetFineVolumeMethod?.invoke(audioManager, AudioManager.STREAM_MUSIC, clampedSteps, flags)
                        }
                    }
                    return 1.0f
                }
                VolumeEngineMode.SHIZUKU_HYBRID_FINE -> {
                    val exactHardwarePos = normalized * nativeRange.toFloat()
                    val ceilingIndex = ceil(exactHardwarePos).toInt().coerceIn(0, nativeRange)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, minIdx + ceilingIndex, flags)
                    return if (ceilingIndex == 0) 0f else (exactHardwarePos / ceilingIndex.toFloat()).coerceIn(0f, 1f)
                }
                else -> {
                    val targetNative = (minIdx + (normalized * nativeRange.toFloat()).roundToInt()).coerceIn(minIdx, minIdx + nativeRange)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetNative, flags)
                    return 1.0f
                }
            }
        } else {
            val targetNative = (minIdx + (normalized * nativeRange.toFloat()).roundToInt()).coerceIn(minIdx, minIdx + nativeRange)
            audioManager.setStreamVolume(streamType, targetNative, flags)
            return 1.0f
        }
    }
}
