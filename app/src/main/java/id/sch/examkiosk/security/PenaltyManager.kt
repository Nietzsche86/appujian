package id.sch.examkiosk.security

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import id.sch.examkiosk.ExamApp
import id.sch.examkiosk.data.local.SecureStorage

class PenaltyManager(private val context: Context) {

    private val secureStorage: SecureStorage = ExamApp.instance.secureStorage

    val violationCount: Int
        get() = secureStorage.violationCount

    val remainingBanSeconds: Long
        get() = secureStorage.remainingBanSeconds

    val isPermanentLockout: Boolean
        get() = secureStorage.violationCount >= MAX_VIOLATIONS_ALLOWED

    fun isBanActive(): Boolean {
        return isPermanentLockout || secureStorage.remainingBanSeconds > 0
    }

    fun checkAndRecordBootCount(): Boolean {
        val currentBootCount = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        } catch (_: Exception) {
            0
        }

        val lastBootCount = secureStorage.lastBootCount
        if (lastBootCount != -1 && currentBootCount > lastBootCount) {
            // Terdeteksi reboot saat ujian!
            secureStorage.lastBootCount = currentBootCount
            return true
        }

        secureStorage.lastBootCount = currentBootCount
        return false
    }

    fun triggerViolation(reason: ViolationReason): Long {
        val newViolationCount = secureStorage.violationCount + 1
        secureStorage.violationCount = newViolationCount

        val banDurationSeconds = when (newViolationCount) {
            1 -> BAN_DURATION_1_SEC
            2 -> BAN_DURATION_2_SEC
            3 -> BAN_DURATION_3_SEC
            else -> PERMANENT_BAN_FLAG
        }

        secureStorage.remainingBanSeconds = banDurationSeconds

        // Berikan getaran respons
        vibrateFeedback(newViolationCount)

        return banDurationSeconds
    }

    fun tickBanSecond() {
        val current = secureStorage.remainingBanSeconds
        if (current > 0 && current != PERMANENT_BAN_FLAG) {
            secureStorage.remainingBanSeconds = current - 1
        }
    }

    fun resetViolationsAndBan() {
        secureStorage.violationCount = 0
        secureStorage.remainingBanSeconds = 0L
    }

    private fun vibrateFeedback(violationLevel: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = if (violationLevel == 1) 500L else 1000L
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500L)
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val BAN_DURATION_1_SEC = 60L
        const val BAN_DURATION_2_SEC = 180L
        const val BAN_DURATION_3_SEC = 300L
        const val PERMANENT_BAN_FLAG = -1L
        const val MAX_VIOLATIONS_ALLOWED = 4
    }
}
