package id.mtsrth.exam.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExamBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ExamReminderScheduler.rescheduleFromStorage(context.applicationContext)
        }
    }
}
