package id.sch.examkiosk

import android.app.Application
import id.sch.examkiosk.data.local.SecureStorage

class ExamApp : Application() {

    lateinit var secureStorage: SecureStorage
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        secureStorage = SecureStorage(applicationContext)
    }

    companion object {
        lateinit var instance: ExamApp
            private set
    }
}
