package id.sch.examkiosk.security

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class KioskEnforcer(
    private val activity: Activity,
    private val onLockStateChanged: (isPinned: Boolean) -> Unit
) {

    private val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val handler = Handler(Looper.getMainLooper())

    private var isGracePeriodActive = false
    private val checkIntervalMs = 500L

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkLockTaskState()
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    fun startEnforcement() {
        try {
            activity.startLockTask()
        } catch (_: Exception) {
        }
        handler.post(pollRunnable)
    }

    fun stopEnforcement() {
        handler.removeCallbacks(pollRunnable)
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
        }
    }

    fun isPinned(): Boolean {
        val mode = activityManager.lockTaskModeState
        return mode == ActivityManager.LOCK_TASK_MODE_PINNED
    }

    fun isWithinGracePeriod(): Boolean {
        return isGracePeriodActive
    }

    private fun checkLockTaskState() {
        val currentlyPinned = isPinned()
        if (currentlyPinned && !isGracePeriodActive) {
            // Aktifkan masa tenggang 3 detik pertama kali ter-pin
            isGracePeriodActive = true
            handler.postDelayed({
                isGracePeriodActive = false
            }, GRACE_PERIOD_MS)
        }
        onLockStateChanged(currentlyPinned)
    }

    companion object {
        private const val GRACE_PERIOD_MS = 3000L
    }
}
