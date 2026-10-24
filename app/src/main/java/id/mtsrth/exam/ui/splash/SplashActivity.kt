package id.mtsrth.exam.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.databinding.ActivitySplashBinding
import id.mtsrth.exam.ui.dashboard.DashboardActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val storage = ExamApp.instance.secureStorage
        binding.tvSplashWelcome.text = "Selamat Datang di E-Ujian"
        if (storage.schoolName.isNotBlank()) {
            binding.tvSplashSchoolName.text = storage.schoolName
        } else {
            binding.tvSplashSchoolName.text = "MTS ROUDHOTUT THOWALIB"
        }
        if (storage.schoolTagline.isNotBlank()) {
            binding.tvSplashTagline.text = storage.schoolTagline
        }

        // Animasi halus fade-in & scale-up logo dan teks
        binding.layoutCenterBrand.alpha = 0f
        binding.layoutCenterBrand.scaleX = 0.85f
        binding.layoutCenterBrand.scaleY = 0.85f

        binding.layoutCenterBrand.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(900)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Tunggu sebentar lalu buka DashboardActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1700)
    }
}
