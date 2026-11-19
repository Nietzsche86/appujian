package id.mtsrth.exam.security

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class KioskEnforcer(
    private val activity: Activity,
    private val onLockStateChanged: (isPinned: Boolean) -> Unit,
    private val onUnpinnedAttempt: (() -> Unit)? = null
) {

    private val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val handler = Handler(Looper.getMainLooper())

    private var isEnforcing = false
    private var wasPinned = false
    private var isGracePeriodActive = false
    private val checkIntervalMs = 500L

    private val clearGraceRunnable = Runnable {
        isGracePeriodActive = false
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isEnforcing) return
            checkLockTaskState()
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    fun startEnforcement() {
        isEnforcing = true
        try {
            activity.startLockTask()
        } catch (_: Exception) {
        }
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    fun stopEnforcement() {
        isEnforcing = false
        wasPinned = false
        isGracePeriodActive = false
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(clearGraceRunnable)
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
        }
    }

    fun isPinned(): Boolean {
        val mode = activityManager.lockTaskModeState
        return mode == ActivityManager.LOCK_TASK_MODE_PINNED ||
            mode == ActivityManager.LOCK_TASK_MODE_LOCKED
    }

    fun isWithinGracePeriod(): Boolean {
        return isGracePeriodActive
    }

    private fun checkLockTaskState() {
        val currentlyPinned = isPinned()

        // Aktifkan masa tenggang 3 detik HANYA saat transisi dari tidak ter-pin -> ter-pin
        // (mencegah bug loop grace period yang berulang setiap 3,5 detik).
        if (currentlyPinned && !wasPinned) {
            isGracePeriodActive = true
            handler.removeCallbacks(clearGraceRunnable)
            handler.postDelayed(clearGraceRunnable, GRACE_PERIOD_MS)
        } else if (!currentlyPinned && wasPinned) {
            isGracePeriodActive = false
            handler.removeCallbacks(clearGraceRunnable)
            onUnpinnedAttempt?.invoke()
        }

        wasPinned = currentlyPinned
        onLockStateChanged(currentlyPinned)
    }

    companion object {
        private const val GRACE_PERIOD_MS = 3000L
    }
}
