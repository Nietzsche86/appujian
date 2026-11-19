package id.mtsrth.exam.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import id.mtsrth.exam.ui.dashboard.DashboardActivity

/**
 * Legacy compatibility redirect. The modern AndroidX SplashScreen API is now
 * installed directly on DashboardActivity for instant startup without artificial delay.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
