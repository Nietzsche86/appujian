package id.mtsrth.exam.security

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Helper umpan balik peringatan pelanggaran di layar ujian:
 * - Getaran haptic yang aman lintas versi Android
 * - Nada beep keras volume penuh (STREAM_ALARM) yang tetap berbunyi walau HP dalam Mode Hening/Getar
 */
class SecurityAlertHelper(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val volumeLock = Any()

    private var activeBeepJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private var savedAlarmVolume: Int? = null
    private var beepGeneration: Long = 0L

    fun triggerVibration(durationMs: Long = 350L) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {
        }
    }

    fun playLoudAlarmBeep(durationMs: Int, frequencyHz: Double = DEFAULT_BEEP_FREQUENCY_HZ) {
        if (durationMs <= 0) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        val myGeneration: Long
        synchronized(volumeLock) {
            beepGeneration++
            myGeneration = beepGeneration

            activeBeepJob?.cancel()
            stopAndReleaseTrackLocked()

            // Simpan volume alarm awal (hanya jika belum dalam state override) lalu naikkan ke maksimum
            try {
                if (savedAlarmVolume == null) {
                    savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                }
                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (maxAlarm > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
                }
            } catch (_: Exception) {
                // Sebagian perangkat mengunci perubahan volume saat mode Do Not Disturb total; tetap lanjutkan pemutaran
            }
        }

        activeBeepJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val samples = generateTonePcm16(durationMs = durationMs, frequencyHz = frequencyHz)
                val bufferBytes = samples.size * 2

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val builtTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()

                if (!isActive || synchronized(volumeLock) { myGeneration != beepGeneration }) {
                    builtTrack.release()
                    return@launch
                }

                builtTrack.write(samples, 0, samples.size)
                builtTrack.setVolume(AudioTrack.getMaxVolume())

                synchronized(volumeLock) {
                    if (myGeneration != beepGeneration) {
                        builtTrack.release()
                        return@launch
                    }
                    activeAudioTrack = builtTrack
                }

                track = builtTrack
                builtTrack.play()

                val stepMs = 40L
                var elapsed = 0L
                val totalWait = durationMs.toLong() + 40L
                while (isActive && elapsed < totalWait) {
                    delay(stepMs)
                    elapsed += stepMs
                }
            } catch (_: Exception) {
            } finally {
                synchronized(volumeLock) {
                    if (track != null && activeAudioTrack === track) {
                        stopAndReleaseTrackLocked()
                    } else {
                        try {
                            track?.release()
                        } catch (_: Exception) {
                        }
                    }

                    // Kembalikan volume alarm awal hanya jika tidak ada beep baru yang menggantikan
                    if (myGeneration == beepGeneration) {
                        restoreSavedAlarmVolumeLocked(audioManager)
                    }
                }
            }
        }
    }

    fun release() {
        synchronized(volumeLock) {
            beepGeneration++
            activeBeepJob?.cancel()
            activeBeepJob = null
            stopAndReleaseTrackLocked()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                restoreSavedAlarmVolumeLocked(audioManager)
            }
        }
        scope.cancel()
    }

    private fun stopAndReleaseTrackLocked() {
        val current = activeAudioTrack ?: return
        activeAudioTrack = null
        try {
            if (current.playState == AudioTrack.PLAYSTATE_PLAYING) {
                current.stop()
            }
        } catch (_: Exception) {
        }
        try {
            current.release()
        } catch (_: Exception) {
        }
    }

    private fun restoreSavedAlarmVolumeLocked(audioManager: AudioManager) {
        val orig = savedAlarmVolume ?: return
        savedAlarmVolume = null
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, orig, 0)
        } catch (_: Exception) {
        }
    }

    private fun generateTonePcm16(durationMs: Int, frequencyHz: Double): ShortArray {
        val totalSamples = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(1)
        val fadeSamples = ((SAMPLE_RATE * 12) / 1000).coerceAtMost(totalSamples / 4)
        val output = ShortArray(totalSamples)
        val twoPiF = 2.0 * PI * frequencyHz

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Nada utama + harmonik ringan agar lebih nyaring pada speaker HP
            val rawWave = 0.85 * sin(twoPiF * t) + 0.15 * sin(twoPiF * 2.0 * t)
            val envelope = when {
                fadeSamples > 0 && i < fadeSamples -> i.toDouble() / fadeSamples
                fadeSamples > 0 && i >= totalSamples - fadeSamples -> (totalSamples - 1 - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val sampleVal = (rawWave * envelope * 31000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i] = sampleVal.toShort()
        }
        return output
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val DEFAULT_BEEP_FREQUENCY_HZ = 1200.0
    }
}
