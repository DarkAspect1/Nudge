package com.nudge.edge.data

import android.content.Context
import android.media.AudioManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class FineVolumeController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        const val FINE_MULTIPLIER = 10

        val ALL_MIXER_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL
        )
    }

    private val cachedFineVolumeByStream = ConcurrentHashMap<Int, Int>()

    private val getFineVolume = runCatching {
        AudioManager::class.java.getMethod("semGetFineVolume", Int::class.javaPrimitiveType)
    }.getOrNull()

    private val setFineVolume = runCatching {
        AudioManager::class.java.getMethod(
            "semSetFineVolume",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
    }.getOrNull()

    fun getStreamMinIndex(streamType: Int): Int {
        return runCatching { audioManager.getStreamMinVolume(streamType) }.getOrDefault(0).coerceAtLeast(0)
    }

    fun getStreamMaxIndex(streamType: Int): Int {
        val min = getStreamMinIndex(streamType)
        return runCatching { audioManager.getStreamMaxVolume(streamType) }
            .getOrDefault(min.coerceAtLeast(1))
            .coerceAtLeast(min.coerceAtLeast(1))
    }

    fun getStreamFineMaxCount(streamType: Int): Int {
        return getStreamMaxIndex(streamType) * FINE_MULTIPLIER
    }

    fun getEffectiveMinIndex(streamType: Int): Int {
        val maxIdx = getStreamMaxIndex(streamType)
        return if (streamType == AudioManager.STREAM_VOICE_CALL) {
            1.coerceAtMost(maxIdx)
        } else {
            getStreamMinIndex(streamType)
        }
    }

    fun getStreamMinNormalizedFraction(streamType: Int): Float {
        val maxIdx = getStreamMaxIndex(streamType)
        if (maxIdx <= 0) return 0f

        if (streamType == AudioManager.STREAM_VOICE_CALL) {
            val effMin = 1.coerceAtMost(maxIdx)
            return (effMin.toFloat() / maxIdx.toFloat()).coerceIn(0f, 1f)
        }
        return 0f
    }

    fun getStreamDisplayVolumeCount(streamType: Int, normalizedFraction: Float): Int {
        val maxIdx = getStreamMaxIndex(streamType)
        val fineMax = maxIdx * FINE_MULTIPLIER
        if (fineMax <= 0) return 0
        val clamped = normalizedFraction.coerceIn(0f, 1f)

        if (streamType == AudioManager.STREAM_VOICE_CALL) {
            val effMin = getEffectiveMinIndex(streamType)
            val minFine = effMin * FINE_MULTIPLIER
            return (clamped * fineMax).roundToInt().coerceIn(minFine, fineMax)
        }
        return (clamped * fineMax).roundToInt().coerceIn(0, fineMax)
    }

    fun stepStreamWithSystem(streamType: Int, direction: Int): Float {
        val adjustDir = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val flags = if (streamType == AudioManager.STREAM_RING) AudioManager.FLAG_ALLOW_RINGER_MODES else 0

        if (direction > 0) unmuteStreamIfNeeded(streamType, flags)

        cachedFineVolumeByStream.remove(streamType)
        runCatching { audioManager.adjustStreamVolume(streamType, adjustDir, flags) }

        if (streamType == AudioManager.STREAM_VOICE_CALL) {
            val effMin = getEffectiveMinIndex(streamType)
            val currentCoarse = runCatching { audioManager.getStreamVolume(streamType) }.getOrDefault(effMin)
            if (currentCoarse < effMin) {
                runCatching { audioManager.setStreamVolume(streamType, effMin, 0) }
            }
        }

        return readSystemNormalizedVolume(streamType)
    }

    fun readSystemNormalizedVolume(streamType: Int): Float {
        val maxIdx = getStreamMaxIndex(streamType)
        val minIdx = getStreamMinIndex(streamType)
        val range = maxIdx - minIdx
        if (range <= 0) return 0f

        val currentIdx = runCatching { audioManager.getStreamVolume(streamType) }
            .getOrDefault(minIdx)
            .coerceIn(minIdx, maxIdx)

        if (streamType == AudioManager.STREAM_VOICE_CALL) {
            val effMin = getEffectiveMinIndex(streamType)
            val clampedIdx = currentIdx.coerceIn(effMin, maxIdx)
            val fineMax = maxIdx * FINE_MULTIPLIER
            val cachedFine = cachedFineVolumeByStream[streamType]
            if (cachedFine != null) {
                val expectedCoarse = (cachedFine.toFloat() / FINE_MULTIPLIER.toFloat()).roundToInt().coerceIn(effMin, maxIdx)
                if (expectedCoarse == clampedIdx) {
                    return (cachedFine.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
                }
            }

            val rawFraction = (clampedIdx.toFloat() / maxIdx.toFloat()).coerceIn(0f, 1f)
            val targetFine = (rawFraction * fineMax).roundToInt().coerceIn(effMin * FINE_MULTIPLIER, fineMax)
            cachedFineVolumeByStream[streamType] = targetFine
            return (targetFine.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
        }

        if ((streamType == AudioManager.STREAM_RING || streamType == AudioManager.STREAM_NOTIFICATION) &&
            runCatching { audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL }.getOrDefault(false)
        ) {
            cachedFineVolumeByStream[streamType] = 0
            return 0f
        }

        val fineMax = maxIdx * FINE_MULTIPLIER

        if (getFineVolume != null && streamType == AudioManager.STREAM_MUSIC) {
            val fineVol = runCatching {
                getFineVolume?.invoke(audioManager, streamType) as Int
            }.getOrDefault(-1)

            if (fineVol in 0..fineMax) {
                if (fineVol == 0) {
                    cachedFineVolumeByStream[streamType] = 0
                    return 0f
                }
                val isMuted = runCatching { audioManager.isStreamMute(streamType) }.getOrDefault(false)
                val coarseVol = runCatching { audioManager.getStreamVolume(streamType) }.getOrDefault(0)
                if (isMuted && coarseVol > 0) {
                    cachedFineVolumeByStream[streamType] = 0
                    return 0f
                }
                cachedFineVolumeByStream[streamType] = fineVol
                return (fineVol.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
            }
        }

        val isMuted = runCatching { audioManager.isStreamMute(streamType) }.getOrDefault(false)
        if (isMuted) {
            cachedFineVolumeByStream[streamType] = 0
            return 0f
        }

        if (currentIdx <= minIdx) {
            cachedFineVolumeByStream[streamType] = 0
            return 0f
        }

        val cachedFine = cachedFineVolumeByStream[streamType]
        if (cachedFine != null && cachedFine in 1..fineMax) {
            val expectedCoarse = (cachedFine.toFloat() / FINE_MULTIPLIER.toFloat())
                .roundToInt()
                .coerceIn(minIdx, maxIdx)
            if (expectedCoarse == currentIdx) {
                return (cachedFine.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
            }
        }

        val rawFraction = (currentIdx - minIdx).toFloat() / range.toFloat()
        val fineFromCoarse = (rawFraction * fineMax).roundToInt().coerceIn(0, fineMax)
        cachedFineVolumeByStream[streamType] = fineFromCoarse
        return (fineFromCoarse.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
    }

    fun setStreamFromTouchDrag(streamType: Int, touchFraction: Float): Float {
        val maxIdx = getStreamMaxIndex(streamType)
        val minIdx = getStreamMinIndex(streamType)
        val range = maxIdx - minIdx
        if (range <= 0) return 0f

        val fineMax = maxIdx * FINE_MULTIPLIER
        val clamped = touchFraction.coerceIn(0f, 1f)
        val targetFine = (clamped * fineMax).roundToInt().coerceIn(0, fineMax)
        cachedFineVolumeByStream[streamType] = targetFine

        if (streamType == AudioManager.STREAM_VOICE_CALL) {
            val effMin = getEffectiveMinIndex(streamType)
            val minFine = effMin * FINE_MULTIPLIER
            val constrainedFine = targetFine.coerceIn(minFine, fineMax)
            cachedFineVolumeByStream[streamType] = constrainedFine

            val targetCoarse = (constrainedFine.toFloat() / FINE_MULTIPLIER.toFloat())
                .coerceIn(effMin.toFloat(), maxIdx.toFloat())
            
            runCatching {
                audioManager.setStreamVolume(streamType, targetCoarse.roundToInt().coerceIn(effMin, maxIdx), 0)
            }
            if (setFineVolume != null) {
                runCatching { setFineVolume?.invoke(audioManager, streamType, constrainedFine, 0) }
            }
            return (constrainedFine.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
        }

        if (targetFine <= 0) {
            if (setFineVolume != null && streamType == AudioManager.STREAM_MUSIC) {
                runCatching { setFineVolume?.invoke(audioManager, streamType, 0, 0) }
            }
            runCatching { audioManager.setStreamVolume(streamType, minIdx, 0) }
            return 0f
        }

        val flags = if (streamType == AudioManager.STREAM_RING) AudioManager.FLAG_ALLOW_RINGER_MODES else 0
        unmuteStreamIfNeeded(streamType, flags)

        val targetCoarse = (targetFine.toFloat() / FINE_MULTIPLIER.toFloat())
            .roundToInt()
            .coerceIn(minIdx, maxIdx)

        if (setFineVolume != null && streamType == AudioManager.STREAM_MUSIC) {
            if (targetFine < FINE_MULTIPLIER &&
                runCatching { audioManager.getStreamVolume(streamType) }.getOrDefault(0) == minIdx
            ) {
                runCatching { audioManager.setStreamVolume(streamType, minIdx + 1, 0) }
            }
            runCatching { setFineVolume?.invoke(audioManager, streamType, targetFine, 0) }
        } else {
            runCatching { audioManager.setStreamVolume(streamType, targetCoarse, 0) }
        }

        return (targetFine.toFloat() / fineMax.toFloat()).coerceIn(0f, 1f)
    }

    fun onScrubReleased(streamType: Int) {
        val fine = cachedFineVolumeByStream[streamType] ?: return
        val maxIdx = getStreamMaxIndex(streamType)
        val minIdx = getStreamMinIndex(streamType)
        val effMin = getEffectiveMinIndex(streamType)
        val targetCoarse = (fine.toFloat() / FINE_MULTIPLIER.toFloat()).roundToInt().coerceIn(effMin, maxIdx)
        runCatching { audioManager.setStreamVolume(streamType, targetCoarse, 0) }
        if (setFineVolume != null && streamType == AudioManager.STREAM_MUSIC) {
            runCatching { setFineVolume?.invoke(audioManager, streamType, fine, 0) }
        }
    }

    private fun unmuteStreamIfNeeded(streamType: Int, flags: Int = 0) {
        if (streamType == AudioManager.STREAM_RING || streamType == AudioManager.STREAM_NOTIFICATION) {
            runCatching {
                if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
            }
        }
        runCatching {
            if (audioManager.isStreamMute(streamType)) {
                audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, flags)
            }
        }
    }
}
