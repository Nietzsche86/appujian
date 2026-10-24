package id.mtsrth.exam.ui.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.pdf.PdfRenderer
import android.os.BatteryManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.R
import id.mtsrth.exam.data.crypto.CryptoEngine
import id.mtsrth.exam.data.sync.LocalSyncManager
import id.mtsrth.exam.databinding.ActivityKioskExamBinding
import id.mtsrth.exam.security.EnvironmentMonitor
import id.mtsrth.exam.security.KioskEnforcer
import id.mtsrth.exam.security.PenaltyManager
import id.mtsrth.exam.security.ViolationReason
import id.mtsrth.exam.ui.dialog.ConfirmFinishDialog
import id.mtsrth.exam.ui.dialog.SupervisorPinDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class KioskExamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskExamBinding
    private lateinit var penaltyManager: PenaltyManager
    private lateinit var environmentMonitor: EnvironmentMonitor
    private lateinit var kioskEnforcer: KioskEnforcer

    private var examId: String = ""
    private var subjectName: String = ""
    private var gradeLevel: Int = 0
    private var durationMinutes: Int = 90
    private var endTime: String = ""
    private var saltHex: String = ""
    private var ivHex: String = ""
    private var token: String = ""

    private var pdfRenderer: PdfRenderer? = null
    private var pdfPfd: ParcelFileDescriptor? = null
    private var activeTempPdfFile: File? = null
    private var pdfAdapter: PdfPageAdapter? = null

    // Exam countdown
    private var timerJob: Job? = null
    private var examRemainingSeconds: Long = 0L
    private var examElapsedBaseTime: Long = 0L

    // Anti-cheat navigation guard
    private var backGestureAttemptCount = 0
    private var lastBackAttemptTimestamp = 0L

    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                binding.tvStatusBarBattery.text = "$batteryPct%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enforce Window Security Flags (Anti-Screenshot / Recording & Keep Screen On)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityKioskExamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentExtras()
        initSecurityEngines()
        setupStatusBar()
        setupListeners()
        setupBackNavigationLock()

        // 2. Start Kiosk Screen Pinning & Anti-Cheat
        kioskEnforcer.startEnforcement()

        // 3. Decrypt and load PDF
        loadAndDisplayPdf()

        // 4. Check for mid-exam reboot
        if (penaltyManager.checkAndRecordBootCount()) {
            handleViolation(ViolationReason.DEVICE_REBOOT_DETECTED)
        }
    }

    override fun onResume() {
        super.onResume()
        registerBatteryReceiver()
        environmentMonitor.startMonitoring()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        environmentMonitor.stopMonitoring()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        environmentMonitor.onWindowFocusChanged(
            hasFocus = hasFocus,
            isKioskGracePeriod = kioskEnforcer.isWithinGracePeriod()
        )
    }

    private fun extractIntentExtras() {
        examId = intent.getStringExtra(EXTRA_EXAM_ID) ?: ""
        subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Ujian"
        gradeLevel = intent.getIntExtra(EXTRA_GRADE_LEVEL, 7)
        durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 90)
        endTime = intent.getStringExtra(EXTRA_END_TIME) ?: ""
        saltHex = intent.getStringExtra(EXTRA_SALT_HEX) ?: ""
        ivHex = intent.getStringExtra(EXTRA_IV_HEX) ?: ""
        token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
    }

    private fun initSecurityEngines() {
        penaltyManager = PenaltyManager(this)

        environmentMonitor = EnvironmentMonitor(this) { reason ->
            handleViolation(reason)
        }

        kioskEnforcer = KioskEnforcer(this) { isPinned ->
            runOnUiThread {
                if (isPinned) {
                    binding.overlayKioskBlocker.visibility = View.GONE
                } else {
                    binding.overlayKioskBlocker.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupStatusBar() {
        binding.tvStatusBarSubject.text = "$subjectName • Kelas $gradeLevel"

        val secureStorage = ExamApp.instance.secureStorage
        val savedRemaining = secureStorage.remainingExamSeconds
        examRemainingSeconds = if (savedRemaining > 0 && secureStorage.activeExamId == examId) {
            savedRemaining
        } else {
            durationMinutes * 60L
        }

        secureStorage.activeExamId = examId
        startExamTimer()
    }

    private fun setupListeners() {
        binding.btnReactivatePin.setOnClickListener {
            kioskEnforcer.startEnforcement()
        }

        binding.btnSupervisorLock.setOnClickListener {
            showSupervisorDialog()
        }

        binding.btnSupervisorEmergency.setOnClickListener {
            showSupervisorDialog()
        }
    }

    private fun setupBackNavigationLock() {
        // Blokir gestur swipe dari tepi kiri/kanan dan tombol back (Android 10 - 14)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAttempt()
            }
        })
    }

    private fun handleBackAttempt() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAttemptTimestamp < 3000L) {
            backGestureAttemptCount++
        } else {
            backGestureAttemptCount = 1
        }
        lastBackAttemptTimestamp = now

        // Jika siswa mencoba swipe kembali berkali-kali (>= 3 kali cepat), picu penalti pelanggaran!
        if (backGestureAttemptCount >= 3) {
            backGestureAttemptCount = 0
            handleViolation(ViolationReason.UNAUTHORIZED_EXIT_ATTEMPT)
        } else {
            Toast.makeText(
                this,
                "Gestur dan tombol kembali DIKUNCI selama ujian! Hubungi pengawas untuk menyelesaikan.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackAttempt()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            handleBackAttempt()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun loadAndDisplayPdf() {
        lifecycleScope.launch {
            val syncManager = LocalSyncManager(this@KioskExamActivity)
            val cryptoEngine = CryptoEngine(this@KioskExamActivity)
            val encFile = syncManager.getExamFile(examId)

            if (!encFile.exists()) {
                Toast.makeText(this@KioskExamActivity, "Berkas soal terenkripsi tidak ditemukan!", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            val decryptResult = cryptoEngine.decryptToUnlinkedPfd(
                encryptedFile = encFile,
                token = token,
                saltHex = saltHex,
                ivHex = ivHex
            )

            decryptResult.onSuccess { session ->
                pdfPfd = session.pfd
                activeTempPdfFile = session.tempFile
                try {
                    val renderer = PdfRenderer(session.pfd)
                    pdfRenderer = renderer

                    val adapter = PdfPageAdapter(renderer, lifecycleScope)
                    pdfAdapter = adapter

                    val layoutManager = LinearLayoutManager(this@KioskExamActivity)
                    binding.rvPdfPages.layoutManager = layoutManager
                    binding.rvPdfPages.adapter = adapter

                    val totalPages = renderer.pageCount
                    updatePageIndicator(binding.rvPdfPages, layoutManager, totalPages)

                    binding.rvPdfPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            updatePageIndicator(recyclerView, layoutManager, totalPages)
                        }
                    })
                } catch (e: Exception) {
                    Toast.makeText(this@KioskExamActivity, "Gagal merender PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }.onFailure { err ->
                Toast.makeText(this@KioskExamActivity, "Dekripsi soal gagal: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun updatePageIndicator(recyclerView: RecyclerView, layoutManager: LinearLayoutManager, totalPages: Int) {
        if (totalPages <= 0) return
        if (totalPages == 1) {
            binding.tvStatusBarPage.text = getString(R.string.page_indicator, 1, 1)
            return
        }

        // 1. Jika sudah mentok di bagian paling bawah dokumen, pasti sedang di halaman terakhir
        if (!recyclerView.canScrollVertically(1)) {
            binding.tvStatusBarPage.text = getString(R.string.page_indicator, totalPages, totalPages)
            return
        }

        // 2. Jika sudah mentok di bagian paling atas dokumen, pasti sedang di halaman pertama
        if (!recyclerView.canScrollVertically(-1)) {
            binding.tvStatusBarPage.text = getString(R.string.page_indicator, 1, totalPages)
            return
        }

        val firstPos = layoutManager.findFirstVisibleItemPosition()
        val lastPos = layoutManager.findLastVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) return

        val rvTop = recyclerView.paddingTop
        val rvBottom = recyclerView.height - recyclerView.paddingBottom

        var maxVisibleHeight = -1
        var dominantPosition = firstPos

        for (pos in firstPos..lastPos) {
            val view = layoutManager.findViewByPosition(pos) ?: continue
            val top = view.top.coerceAtLeast(rvTop)
            val bottom = view.bottom.coerceAtMost(rvBottom)
            val visibleHeight = bottom - top
            if (visibleHeight > maxVisibleHeight) {
                maxVisibleHeight = visibleHeight
                dominantPosition = pos
            }
        }

        val currentPage = (dominantPosition + 1).coerceIn(1, totalPages)
        binding.tvStatusBarPage.text = getString(R.string.page_indicator, currentPage, totalPages)
    }

    private fun startExamTimer() {
        timerJob?.cancel()
        examElapsedBaseTime = SystemClock.elapsedRealtime()

        var heartbeatCounter = 0
        var isBlinkingRed = false

        timerJob = lifecycleScope.launch {
            while (isActive && examRemainingSeconds > 0) {
                delay(1000L)
                examRemainingSeconds--
                heartbeatCounter++

                // Simpan sisa waktu setiap 5 detik ke SecureStorage
                if (heartbeatCounter >= 5) {
                    heartbeatCounter = 0
                    ExamApp.instance.secureStorage.remainingExamSeconds = examRemainingSeconds
                }

                val hours = examRemainingSeconds / 3600
                val minutes = (examRemainingSeconds % 3600) / 60
                val seconds = examRemainingSeconds % 60
                val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                binding.tvStatusBarTimer.text = timeFormatted

                // Styling warna timer dinamis
                when {
                    examRemainingSeconds <= 300 -> { // <= 5 Menit (Berkedip Merah)
                        isBlinkingRed = !isBlinkingRed
                        val colorRes = if (isBlinkingRed) R.color.timer_danger else R.color.timer_normal
                        val color = ContextCompat.getColor(this@KioskExamActivity, colorRes)
                        binding.tvStatusBarTimer.setTextColor(color)
                        binding.ivTimerIcon.setColorFilter(color)
                    }
                    examRemainingSeconds <= 900 -> { // <= 15 Menit (Kuning)
                        val yellow = ContextCompat.getColor(this@KioskExamActivity, R.color.timer_warning)
                        binding.tvStatusBarTimer.setTextColor(yellow)
                        binding.ivTimerIcon.setColorFilter(yellow)
                    }
                    else -> {
                        val normal = ContextCompat.getColor(this@KioskExamActivity, R.color.timer_normal)
                        binding.tvStatusBarTimer.setTextColor(normal)
                        binding.ivTimerIcon.setColorFilter(normal)
                    }
                }

                // Cek jam nyata di HP jika batas jam selesai ditentukan
                if (endTime.isNotBlank()) {
                    val endMin = id.mtsrth.exam.util.ExamScheduleHelper.parseMinutesOfDay(endTime)
                    if (endMin != null) {
                        val now = java.util.Calendar.getInstance()
                        val currentMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                        if (currentMin >= endMin) {
                            finishExam(isExpired = true)
                            return@launch
                        }
                    }
                }

                // Tick ban penalty jika sedang aktif
                if (penaltyManager.isBanActive()) {
                    tickBanPenalty()
                }
            }

            if (examRemainingSeconds <= 0) {
                finishExam(isExpired = true)
            }
        }
    }

    private fun handleViolation(reason: ViolationReason) {
        val banDuration = penaltyManager.triggerViolation(reason)

        runOnUiThread {
            // Sembunyikan kanvas soal seketika
            binding.zoomablePdfContainer.visibility = View.INVISIBLE
            binding.overlayViolationBan.visibility = View.VISIBLE
            binding.tvViolationDescription.text = reason.description

            if (penaltyManager.isPermanentLockout) {
                binding.tvBanCountdown.text = getString(R.string.msg_permanent_ban)
            } else {
                updateBanCountdownText(banDuration)
            }
        }
    }

    private fun tickBanPenalty() {
        if (penaltyManager.isPermanentLockout) return

        penaltyManager.tickBanSecond()
        val remainingBan = penaltyManager.remainingBanSeconds

        runOnUiThread {
            if (remainingBan > 0) {
                updateBanCountdownText(remainingBan)
            } else {
                // Selesai ban, pastikan semua syarat terpenuhi kembali
                val isAirplaneOk = environmentMonitor.checkAllImmediate()
                val isKioskOk = kioskEnforcer.isPinned()

                if (isAirplaneOk && isKioskOk) {
                    binding.overlayViolationBan.visibility = View.GONE
                    binding.zoomablePdfContainer.visibility = View.VISIBLE
                } else {
                    binding.tvBanCountdown.text = "Tunggu kepatuhan syarat ujian..."
                }
            }
        }
    }

    private fun updateBanCountdownText(seconds: Long) {
        val min = seconds / 60
        val sec = seconds % 60
        binding.tvBanCountdown.text = getString(R.string.countdown_ban, min, sec)
    }

    private fun showSupervisorDialog() {
        val dialog = SupervisorPinDialog.newInstance(
            onResetPenalty = {
                penaltyManager.resetViolationsAndBan()
                binding.overlayViolationBan.visibility = View.GONE
                binding.zoomablePdfContainer.visibility = View.VISIBLE
                Toast.makeText(this, "Penalti pelanggaran telah direset oleh pengawas.", Toast.LENGTH_SHORT).show()
            },
            onFinishEarly = {
                val confirmDialog = ConfirmFinishDialog.newInstance {
                    finishExam(isExpired = false)
                }
                confirmDialog.show(supportFragmentManager, ConfirmFinishDialog.TAG)
            }
        )
        dialog.show(supportFragmentManager, SupervisorPinDialog.TAG)
    }

    private fun finishExam(isExpired: Boolean) {
        timerJob?.cancel()

        // 1. Tandai ujian selesai di penyimpanan persisten
        val secureStorage = ExamApp.instance.secureStorage
        secureStorage.markExamFinished(examId)
        secureStorage.resetActiveExamSession()

        // 2. Lepaskan Kiosk Screen Pinning
        kioskEnforcer.stopEnforcement()

        val message = if (isExpired) {
            "Waktu ujian telah habis! Lembar soal ditutup."
        } else {
            "Ujian berhasil diselesaikan oleh pengawas."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()

        try {
            pdfRenderer?.close()
        } catch (_: Exception) {
        }

        try {
            pdfPfd?.close()
        } catch (_: Exception) {
        }

        try {
            activeTempPdfFile?.delete()
        } catch (_: Exception) {
        }

        kioskEnforcer.stopEnforcement()
        environmentMonitor.stopMonitoring()
    }

    companion object {
        const val EXTRA_EXAM_ID = "extra_exam_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_GRADE_LEVEL = "extra_grade_level"
        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_END_TIME = "extra_end_time"
        const val EXTRA_SALT_HEX = "extra_salt_hex"
        const val EXTRA_IV_HEX = "extra_iv_hex"
        const val EXTRA_TOKEN = "extra_token"
    }
}
