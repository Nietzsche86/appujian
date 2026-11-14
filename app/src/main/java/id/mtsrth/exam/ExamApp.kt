package id.mtsrth.exam

import android.app.Application
import id.mtsrth.exam.data.local.SecureStorage
import id.mtsrth.exam.notification.ExamReminderScheduler

class ExamApp : Application() {

    lateinit var secureStorage: SecureStorage
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        secureStorage = SecureStorage(applicationContext)
        ExamReminderScheduler.ensureChannel(applicationContext)
    }

    companion object {
        lateinit var instance: ExamApp
            private set
    }
}
