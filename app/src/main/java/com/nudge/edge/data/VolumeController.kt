package com.nudge.edge.data

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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

    fun getActiveStreamType(): Int {
        val mode = audioManager.mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        val isMediaPlayingNow = audioManager.isMusicActive
        return when {
            inCall && isMediaPlayingNow -> AudioManager.STREAM_MUSIC
            inCall && !isMediaPlayingNow -> AudioManager.STREAM_VOICE_CALL
            mode == AudioManager.MODE_RINGTONE -> AudioManager.STREAM_RING
            else -> AudioManager.STREAM_MUSIC
        }
    }

    fun detectTrueDeviceMaxSteps(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        val rawMin = getStreamMin(streamType)
        val rawMax = getStreamMax(streamType)
        val span = rawMax - rawMin
        if (span >= 50) {
            return span
        }
        if (isSoundAssistantInstalled() || hasSettingsFineScaleIndicator(rawMax)) {
            return 150
        }
        return 100
    }

    fun isCallActive(context: Context? = null): Boolean {
        val mode = audioManager.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return true
        }
        val ctx = context ?: appContext
        val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        if (telecom != null) {
            if (runCatching { telecom.isInCall }.getOrDefault(false) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runCatching { telecom.isInManagedCall }.getOrDefault(false))) {
                return true
            }
        }
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        if (telephony != null) {
            if (runCatching { telephony.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE }.getOrDefault(false)) {
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activePlaybacks = runCatching { audioManager.activePlaybackConfigurations }.getOrDefault(emptyList())
            val hasVoicePlayback = activePlaybacks.any { config ->
                val usage = config.audioAttributes.usage
                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING
            }
            if (hasVoicePlayback) return true

            val activeRecordings = runCatching { audioManager.activeRecordingConfigurations }.getOrDefault(emptyList())
            val hasVoiceRecording = activeRecordings.any { config ->
                val source = config.audioSource
                source == android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
            if (hasVoiceRecording) return true
        }
        return false
    }

    fun getStreamMin(streamType: Int): Int {
        val safeStream = if (streamType == STREAM_BLUETOOTH_SCO) AudioManager.STREAM_VOICE_CALL else streamType
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sysMin = runCatching { audioManager.getStreamMinVolume(safeStream) }.getOrDefault(if (safeStream == AudioManager.STREAM_VOICE_CALL) 1 else 0)
            if (safeStream == AudioManager.STREAM_VOICE_CALL) sysMin.coerceAtLeast(1) else sysMin
        } else {
            if (safeStream == AudioManager.STREAM_VOICE_CALL) 1 else 0
        }
    }

    fun getStreamMax(streamType: Int): Int {
        val safeStream = if (streamType == STREAM_BLUETOOTH_SCO) AudioManager.STREAM_VOICE_CALL else streamType
        val publicMax = runCatching { audioManager.getStreamMaxVolume(safeStream) }.getOrDefault(15).coerceAtLeast(1)
        if (safeStream == AudioManager.STREAM_MUSIC) {
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

    fun ensureRingerAudible(streamType: Int) {
        if (streamType != AudioManager.STREAM_RING &&
            streamType != AudioManager.STREAM_NOTIFICATION
        ) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            runCatching { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL }
        }
        val notifManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notifManager != null && notifManager.isNotificationPolicyAccessGranted) {
            if (notifManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                runCatching {
                    notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        }
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
        val flags = 0

        if ((streamType == AudioManager.STREAM_RING || streamType == AudioManager.STREAM_NOTIFICATION) && clamped > 0) {
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                runCatching {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
            val notifManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notifManager != null && notifManager.isNotificationPolicyAccessGranted) {
                if (notifManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    runCatching {
                        notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }
            }
        }

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

        val safeStream = if (streamType == STREAM_BLUETOOTH_SCO) AudioManager.STREAM_VOICE_CALL else streamType
        val current = knownCurrent ?: runCatching { audioManager.getStreamVolume(safeStream) }.getOrDefault(0)
        if (clamped != current || showSystemUi) {
            val result = runCatching {
                audioManager.setStreamVolume(safeStream, clamped, flags)
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
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                vibrator.vibrate(effect, audioAttrs)
            }
        }.recoverCatching {
            vibrator.vibrate(effect)
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

    fun isBluetoothAudioConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull()
            if (devices != null) {
                return devices.any { isBluetoothOutputDevice(it.type) }
            }
        }
        @Suppress("DEPRECATION")
        return audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
    }

    private fun isBluetoothOutputDevice(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) {
                    true
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                )) {
                    true
                } else {
                    false
                }
            }
        }
    }

    companion object {
        const val STREAM_BLUETOOTH_SCO = 6

        fun computeSteppedVolume(
            currentVolume: Int,
            direction: Int,
            volumeStep: Int,
            minVolume: Int,
            maxVolume: Int,
            scaleSteps: Int = 100
        ): Int {
            val sign = when {
                direction > 0 -> 1
                direction < 0 -> -1
                else -> 0
            }
            if (sign == 0) return currentVolume
            val span = (maxVolume - minVolume).coerceAtLeast(1)
            val effectiveScale = if (scaleSteps in 5..200) scaleSteps else 100
            val step = volumeStep.coerceIn(1, 20)
            return if (span > 15) {
                val currentRatio = (currentVolume - minVolume).toFloat() / span.toFloat()
                val stepRatio = step.toFloat() / effectiveScale.toFloat()
                val currentStepIndex = (currentRatio / stepRatio).roundToInt()
                val nextStepIndex = currentStepIndex + sign
                val nextRatio = (nextStepIndex * stepRatio).coerceIn(0f, 1f)
                var nextVol = minVolume + (nextRatio * span.toFloat()).roundToInt()
                if (sign > 0 && nextVol <= currentVolume && currentVolume < maxVolume) {
                    nextVol = currentVolume + 1
                } else if (sign < 0 && nextVol >= currentVolume && currentVolume > minVolume) {
                    nextVol = currentVolume - 1
                }
                nextVol.coerceIn(minVolume, maxVolume)
            } else {
                (currentVolume + sign * step).coerceIn(minVolume, maxVolume)
            }
        }
    }
}
