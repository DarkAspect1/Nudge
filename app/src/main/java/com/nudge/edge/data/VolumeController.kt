package com.nudge.edge.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import java.lang.reflect.Method
import java.util.concurrent.Executor
import kotlin.math.roundToInt

class VolumeController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var latchedFineMax: Int = 0

    @Volatile
    private var fineProbeDone = false

    @Volatile
    private var cachedSoundAssistantInstalled = false

    @Volatile
    private var lastSoundAssistantCheckMs = -60_000L

    @Volatile
    private var cachedCallOrRingtone = false

    private var modeChangedListener: Any? = null

    private val roMediaVolSteps: Int by lazy {
        probeSystemPropertyInt("ro.config.media_vol_steps")
    }

    private val defaultVibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var fineGetterMethod: Method? = null
    private var fineGetterArgCount: Int = 0
    private var fineSetterMethod: Method? = null
    private var fineSetterArgCount: Int = 0
    private var fineMaxMethod: Method? = null
    private var fineMaxArgCount: Int = 0
    private var fineSupportedMethod: Method? = null

    private fun ensureFineMethodsProbed() {
        if (fineProbeDone) return
        synchronized(this) {
            if (fineProbeDone) return
            val cls = audioManager.javaClass

            fineGetterMethod = runCatching {
                cls.getMethod("semGetFineVolume", Int::class.javaPrimitiveType).also {
                    fineGetterArgCount = 1
                }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "semGetFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineGetterArgCount = 2 }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "getFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineGetterArgCount = 2 }
            }.getOrNull() ?: runCatching {
                cls.getMethod("getFineVolume", Int::class.javaPrimitiveType).also {
                    fineGetterArgCount = 1
                }
            }.getOrNull() ?: runCatching {
                cls.getMethod("semGetStreamVolume", Int::class.javaPrimitiveType).also {
                    fineGetterArgCount = 1
                }
            }.getOrNull()

            fineSetterMethod = runCatching {
                cls.getMethod(
                    "semSetFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineSetterArgCount = 3 }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "semSetFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineSetterArgCount = 2 }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "setFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineSetterArgCount = 4 }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "setFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineSetterArgCount = 3 }
            }.getOrNull() ?: runCatching {
                cls.getMethod(
                    "setFineVolume",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).also { fineSetterArgCount = 2 }
            }.getOrNull()

            fineMaxMethod = runCatching {
                cls.getMethod("semGetStreamVolumeMax", Int::class.javaPrimitiveType).also {
                    fineMaxArgCount = 1
                }
            }.getOrNull() ?: runCatching {
                cls.getMethod("semGetFineVolumeMax", Int::class.javaPrimitiveType).also {
                    fineMaxArgCount = 1
                }
            }.getOrNull() ?: runCatching {
                cls.getMethod("semGetFineVolumeMax").also {
                    fineMaxArgCount = 0
                }
            }.getOrNull()

            fineSupportedMethod = runCatching {
                cls.getMethod("semIsFineVolumeSupported")
            }.getOrNull() ?: runCatching {
                cls.getMethod("isFineVolumeSupported")
            }.getOrNull()

            fineProbeDone = true
        }
    }

    private fun queryFineVolume(streamType: Int): Int? {
        ensureFineMethodsProbed()
        val m = fineGetterMethod ?: return null
        return runCatching {
            val result = when (fineGetterArgCount) {
                2 -> m.invoke(audioManager, streamType, 0)
                else -> m.invoke(audioManager, streamType)
            }
            (result as? Int)?.takeIf { it >= 0 }
        }.getOrNull()
    }

    private fun writeFineVolume(streamType: Int, fineIndex: Int, flags: Int): Boolean {
        ensureFineMethodsProbed()
        val m = fineSetterMethod ?: return false
        return runCatching {
            when (fineSetterArgCount) {
                4 -> m.invoke(audioManager, streamType, fineIndex, flags, 0)
                3 -> m.invoke(audioManager, streamType, fineIndex, flags)
                2 -> m.invoke(audioManager, streamType, fineIndex)
            }
            true
        }.getOrDefault(false)
    }

    private fun queryFineMax(streamType: Int): Int? {
        ensureFineMethodsProbed()
        val m = fineMaxMethod ?: return null
        return runCatching {
            val result = when (fineMaxArgCount) {
                0 -> m.invoke(audioManager)
                else -> m.invoke(audioManager, streamType)
            }
            (result as? Int)?.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun queryFineSupportedFlag(): Boolean {
        ensureFineMethodsProbed()
        val m = fineSupportedMethod ?: return false
        return runCatching {
            (m.invoke(audioManager) as? Boolean) ?: false
        }.getOrDefault(false)
    }

    private fun probeSystemPropertyInt(key: String): Int {
        return runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            val m = cls.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            (m.invoke(null, key, 0) as? Int) ?: 0
        }.getOrDefault(0)
    }

    private fun isSoundAssistantInstalled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSoundAssistantCheckMs < 10_000L) {
            return cachedSoundAssistantInstalled
        }
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    "com.samsung.android.soundassistant",
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo("com.samsung.android.soundassistant", 0)
            }
            true
        }.getOrDefault(false)
        cachedSoundAssistantInstalled = installed
        lastSoundAssistantCheckMs = now
        return installed
    }

    private fun hasSettingsFineScaleIndicator(publicMax: Int): Boolean {
        val cr = appContext.contentResolver
        val valueKeys = arrayOf(
            "volume_music_speaker",
            "volume_music_headset",
            "volume_music_bt_a2dp",
            "volume_music_usb_headset",
            "volume_music"
        )
        for (key in valueKeys) {
            val v = runCatching { Settings.System.getInt(cr, key, -1) }.getOrDefault(-1)
            if (v in (publicMax + 1)..150) {
                return true
            }
        }
        val stepKeys = arrayOf(
            "soundassistant_step_volume",
            "sound_assistant_volume_step",
            "fine_volume_step",
            "sem_fine_volume_step"
        )
        for (key in stepKeys) {
            val v = runCatching { Settings.System.getInt(cr, key, -1) }.getOrDefault(-1)
            if (v in 1..9) {
                return true
            }
        }
        return false
    }

    private fun isMusicFineModeActive(publicMax: Int): Boolean {
        if (publicMax > 15) return false
        val semMax = queryFineMax(AudioManager.STREAM_MUSIC)
        if (semMax != null && semMax > publicMax) {
            latchedFineMax = semMax
            return true
        }
        val fineVal = queryFineVolume(AudioManager.STREAM_MUSIC)
        if (fineVal != null && fineVal % 10 != 0) {
            latchedFineMax = publicMax * 10
            return true
        }
        if (latchedFineMax > publicMax) {
            return true
        }
        if (fineVal != null && fineSetterMethod != null && (queryFineSupportedFlag() || isSoundAssistantInstalled() || hasSettingsFineScaleIndicator(publicMax))) {
            latchedFineMax = publicMax * 10
            return true
        }
        if (isSoundAssistantInstalled() || hasSettingsFineScaleIndicator(publicMax)) {
            latchedFineMax = publicMax * 10
            return true
        }
        return false
    }

    fun recordObservedStreamValue(streamType: Int, value: Int) {
        if (streamType != AudioManager.STREAM_MUSIC) return
        val publicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (value > publicMax && value > latchedFineMax) {
            latchedFineMax = if (value <= publicMax * 10) publicMax * 10 else value
        }
    }

    fun getStreamMin(streamType: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(streamType)
        } else {
            0
        }
    }

    fun getStreamMax(streamType: Int): Int {
        val publicMax = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        if (streamType == AudioManager.STREAM_MUSIC) {
            return getMusicMaxVolume()
        }
        return publicMax
    }

    fun getMusicMinVolume(): Int = getStreamMin(AudioManager.STREAM_MUSIC)

    fun getMusicMaxVolume(): Int {
        val publicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (publicMax > 15) return publicMax

        val semMax = queryFineMax(AudioManager.STREAM_MUSIC)
        if (semMax != null && semMax > publicMax) {
            latchedFineMax = semMax
            return semMax
        }

        if (roMediaVolSteps > publicMax) {
            return roMediaVolSteps
        }

        if (isMusicFineModeActive(publicMax)) {
            return latchedFineMax.coerceAtLeast(publicMax * 10)
        }

        return publicMax
    }

    fun getStreamVolume(streamType: Int): Int {
        if (streamType == AudioManager.STREAM_MUSIC) {
            val publicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val maxVol = getMusicMaxVolume()
            if (maxVol > publicMax) {
                val fineVal = queryFineVolume(AudioManager.STREAM_MUSIC)
                if (fineVal != null && fineVal in 0..maxVol) {
                    return fineVal
                }
                val coarse = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val ratio = maxVol.toFloat() / publicMax.toFloat()
                return (coarse * ratio).roundToInt().coerceIn(0, maxVol)
            }
        }
        return audioManager.getStreamVolume(streamType)
    }

    fun getMusicVolumeIndex(): Int = getStreamVolume(AudioManager.STREAM_MUSIC)

    fun indexToFraction(streamType: Int, index: Int): Float {
        val min = getStreamMin(streamType)
        val max = getStreamMax(streamType)
        val span = (max - min).coerceAtLeast(1)
        return ((index - min).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    fun getStreamFraction(streamType: Int): Float {
        return indexToFraction(streamType, getStreamVolume(streamType))
    }

    fun getMusicVolumeFraction(): Float = getStreamFraction(AudioManager.STREAM_MUSIC)

    fun fractionToStreamIndex(streamType: Int, fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val min = getStreamMin(streamType)
        val max = getStreamMax(streamType)
        return (min + (max - min) * clamped).roundToInt().coerceIn(min, max)
    }

    fun setStreamIndex(
        streamType: Int,
        targetIndex: Int,
        showSystemUi: Boolean = false,
        knownCurrent: Int? = null
    ): Int {
        val min = getStreamMin(streamType)
        val max = getStreamMax(streamType)
        val clamped = targetIndex.coerceIn(min, max)
        val publicMax = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val flags = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0

        if (streamType == AudioManager.STREAM_MUSIC && max > publicMax) {
            val currentFine = knownCurrent ?: getStreamVolume(streamType)
            if (clamped == currentFine && !showSystemUi) {
                return currentFine
            }
            val wroteFine = writeFineVolume(streamType, clamped, flags)
            if (wroteFine) {
                return clamped
            }
            val coarseTarget = (clamped.toFloat() * publicMax.toFloat() / max.toFloat())
                .roundToInt()
                .coerceIn(min, publicMax)
            val currentCoarse = audioManager.getStreamVolume(streamType)
            if (coarseTarget != currentCoarse || showSystemUi) {
                runCatching { audioManager.setStreamVolume(streamType, coarseTarget, flags) }
            }
            return clamped
        }

        val current = knownCurrent ?: audioManager.getStreamVolume(streamType)
        if (clamped != current || showSystemUi) {
            val result = runCatching {
                audioManager.setStreamVolume(streamType, clamped, flags)
            }
            if (result.isFailure) {
                return current
            }
        }
        return clamped
    }

    fun setStreamFraction(
        streamType: Int,
        fraction: Float,
        showSystemUi: Boolean = false
    ): Int {
        return setStreamIndex(streamType, fractionToStreamIndex(streamType, fraction), showSystemUi)
    }

    fun setMusicVolumeFraction(
        fraction: Float,
        showSystemUi: Boolean = false
    ): Int {
        return setStreamFraction(AudioManager.STREAM_MUSIC, fraction, showSystemUi)
    }

    fun registerAudioModeListener(executor: Executor) {
        refreshAudioModeCache()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeChangedListener == null) {
            val listener = AudioManager.OnModeChangedListener { mode ->
                cachedCallOrRingtone = isPriorityAudioMode(mode)
            }
            modeChangedListener = listener
            runCatching { audioManager.addOnModeChangedListener(executor, listener) }
        }
    }

    fun unregisterAudioModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = modeChangedListener as? AudioManager.OnModeChangedListener ?: return
            runCatching { audioManager.removeOnModeChangedListener(listener) }
        }
        modeChangedListener = null
    }

    fun refreshAudioModeCache() {
        cachedCallOrRingtone = isCallOrRingtonePriority()
    }

    fun isCallOrRingtonePriorityFast(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedCallOrRingtone
        } else {
            isCallOrRingtonePriority()
        }
    }

    fun isCallOrRingtonePriority(): Boolean {
        return isPriorityAudioMode(audioManager.mode)
    }

    private fun isPriorityAudioMode(mode: Int): Boolean {
        return mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
    }

    fun triggerHaptic(strong: Boolean = false) {
        val vibrator = defaultVibrator
        if (!vibrator.hasVibrator()) return
        val effect = createHapticEffect(vibrator, strong)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                vibrator.vibrate(effect, audioAttrs)
            }
        }
    }

    private fun createHapticEffect(vibrator: Vibrator, strong: Boolean): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effectId = if (strong) {
                VibrationEffect.EFFECT_HEAVY_CLICK
            } else {
                VibrationEffect.EFFECT_CLICK
            }
            val supported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator.areAllEffectsSupported(effectId) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
            } else {
                true
            }
            if (supported) {
                return VibrationEffect.createPredefined(effectId)
            }
        }
        val duration = if (strong) 22L else 14L
        return VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    companion object {
        fun computeSteppedVolume(
            currentVolume: Int,
            direction: Int,
            volumeStep: Int,
            minVolume: Int,
            maxVolume: Int
        ): Int {
            val step = volumeStep.coerceIn(1, 10)
            val sign = when {
                direction > 0 -> 1
                direction < 0 -> -1
                else -> 0
            }
            return (currentVolume + sign * step).coerceIn(minVolume, maxVolume)
        }
    }
}