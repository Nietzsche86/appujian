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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.crypto.CryptoEngine
import id.mtsrth.exam.data.sync.LocalSyncManager
import id.mtsrth.exam.notification.ExamReminderScheduler
import id.mtsrth.exam.security.EnvironmentMonitor
import id.mtsrth.exam.security.KioskEnforcer
import id.mtsrth.exam.security.PenaltyManager
import id.mtsrth.exam.security.ViolationReason
import id.mtsrth.exam.ui.theme.ExamKioskTheme
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class KioskExamActivity : AppCompatActivity() {

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
    private var isMakeupExam: Boolean = false

    private var pdfRenderer: PdfRenderer? = null
    private var pdfPfd: ParcelFileDescriptor? = null
    private var activeTempPdfFile: File? = null
    private var pdfAdapter: PdfPageAdapter? = null

    private var zoomableLayoutRef: ZoomableLayout? = null
    private var recyclerViewRef: RecyclerView? = null
    private var layoutManagerRef: LinearLayoutManager? = null
    private var isRecyclerAdapterAttached = false

    private val uiStateFlow = MutableStateFlow(KioskUiState())

    private var showPageJumperDialog by mutableStateOf(false)
    private var showSupervisorDialog by mutableStateOf(false)
    private var showConfirmFinishDialog by mutableStateOf(false)

    // Exam countdown
    private var timerJob: Job? = null
    private var examRemainingSeconds: Long = 0L

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
                uiStateFlow.update { it.copy(batteryPct = batteryPct) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val storage = ExamApp.instance.secureStorage
        val initialDarkMode = storage.isDarkMode
        applyEdgeToEdgeTheme(initialDarkMode)
        super.onCreate(savedInstanceState)

        // 1. Enforce Window Security Flags (Anti-Screenshot / Recording & Keep Screen On)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        extractIntentExtras()
        if (examId.isNotBlank()) {
            ExamReminderScheduler.cancelExamReminder(this, examId)
        }
        initSecurityEngines()
        setupInitialExamState()
        setupBackNavigationLock()

        setContent {
            val uiState by uiStateFlow.collectAsStateWithLifecycle()

            ExamKioskTheme(darkTheme = uiState.isDarkMode) {
                KioskExamScreen(
                    uiState = uiState,
                    onAttachPdfViews = { zoomable, recycler ->
                        zoomableLayoutRef = zoomable
                        if (recyclerViewRef !== recycler) {
                            recyclerViewRef = recycler
                            isRecyclerAdapterAttached = false
                        }
                        bindAdapterIfReady()
                    },
                    onToggleComfortMode = {
                        val nextComfort = !uiStateFlow.value.isComfortMode
                        ExamApp.instance.secureStorage.kioskComfortMode = nextComfort
                        uiStateFlow.update { it.copy(isComfortMode = nextComfort) }
                    },
                    onOpenPageJumper = {
                        if (uiStateFlow.value.totalPages > 1) {
                            showPageJumperDialog = true
                        }
                    },
                    onOpenSupervisorDialog = {
                        showSupervisorDialog = true
                    },
                    onReactivatePin = {
                        kioskEnforcer.startEnforcement()
                    }
                )

                if (showPageJumperDialog) {
                    JumpToPageDialog(
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        onJumpToPage = { page -> jumpToPage(page) },
                        onDismiss = { showPageJumperDialog = false }
                    )
                }

                if (showSupervisorDialog) {
                    SupervisorPinComposeDialog(
                        onResetPenalty = {
                            showSupervisorDialog = false
                            penaltyManager.resetViolationsAndBan()
                            uiStateFlow.update {
                                it.copy(
                                    isBanActive = false,
                                    isPermanentLockout = false,
                                    banRemainingSeconds = 0L,
                                    banStatusMessage = null
                                )
                            }
                            Toast.makeText(
                                this@KioskExamActivity,
                                "Penalti pelanggaran telah direset oleh pengawas.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFinishEarly = {
                            showSupervisorDialog = false
                            showConfirmFinishDialog = true
                        },
                        onDismiss = { showSupervisorDialog = false }
                    )
                }

                if (showConfirmFinishDialog) {
                    ConfirmFinishComposeDialog(
                        subjectName = uiState.subjectName,
                        onConfirm = {
                            showConfirmFinishDialog = false
                            finishExam(isExpired = false)
                        },
                        onDismiss = { showConfirmFinishDialog = false }
                    )
                }
            }
        }

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

    private fun applyEdgeToEdgeTheme(isDark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
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
        isMakeupExam = intent.getBooleanExtra(EXTRA_IS_MAKEUP_EXAM, false)
    }

    private fun initSecurityEngines() {
        penaltyManager = PenaltyManager(this)

        environmentMonitor = EnvironmentMonitor(this) { reason ->
            handleViolation(reason)
        }

        kioskEnforcer = KioskEnforcer(this) { isPinned ->
            runOnUiThread {
                uiStateFlow.update { it.copy(isPinned = isPinned) }
            }
        }
    }

    private fun setupInitialExamState() {
        val secureStorage = ExamApp.instance.secureStorage
        val savedRemaining = secureStorage.remainingExamSeconds
        val isResumingSameExam = savedRemaining > 0 && secureStorage.activeExamId == examId
        if (isResumingSameExam) {
            examRemainingSeconds = savedRemaining
            if (secureStorage.activeExamIsMakeup) {
                isMakeupExam = true
            }
        } else {
            examRemainingSeconds = durationMinutes * 60L
        }

        secureStorage.activeExamId = examId
        secureStorage.activeExamIsMakeup = isMakeupExam

        uiStateFlow.update {
            it.copy(
                subjectName = subjectName,
                gradeLevel = gradeLevel,
                remainingSeconds = examRemainingSeconds,
                isDarkMode = secureStorage.isDarkMode,
                isComfortMode = secureStorage.kioskComfortMode
            )
        }

        startExamTimer()
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
                Toast.makeText(
                    this@KioskExamActivity,
                    "Berkas soal terenkripsi tidak ditemukan!",
                    Toast.LENGTH_LONG
                ).show()
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
                    pdfAdapter = PdfPageAdapter(renderer, lifecycleScope)

                    val totalPages = renderer.pageCount
                    uiStateFlow.update {
                        it.copy(
                            totalPages = totalPages,
                            currentPage = 1,
                            isPdfLoading = false
                        )
                    }
                    bindAdapterIfReady()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@KioskExamActivity,
                        "Gagal merender PDF: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }.onFailure { err ->
                Toast.makeText(
                    this@KioskExamActivity,
                    "Dekripsi soal gagal: ${err.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun bindAdapterIfReady() {
        val recycler = recyclerViewRef ?: return
        val adapter = pdfAdapter ?: return
        val renderer = pdfRenderer ?: return
        if (isRecyclerAdapterAttached && recycler.adapter === adapter) return

        val layoutManager = LinearLayoutManager(this)
        layoutManagerRef = layoutManager
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        isRecyclerAdapterAttached = true

        val totalPages = renderer.pageCount
        updatePageIndicator(recycler, layoutManager, totalPages)

        recycler.clearOnScrollListeners()
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updatePageIndicator(recyclerView, layoutManager, totalPages)
            }
        })
    }

    private fun jumpToPage(pageNumber: Int) {
        val total = uiStateFlow.value.totalPages
        if (total <= 0) return
        val clamped = pageNumber.coerceIn(1, total)
        zoomableLayoutRef?.resetZoom()
        layoutManagerRef?.scrollToPositionWithOffset(clamped - 1, 0)
        uiStateFlow.update { it.copy(currentPage = clamped) }
    }

    private fun updatePageIndicator(
        recyclerView: RecyclerView,
        layoutManager: LinearLayoutManager,
        totalPages: Int
    ) {
        if (totalPages <= 0) return
        if (totalPages == 1) {
            uiStateFlow.update { it.copy(currentPage = 1, totalPages = 1) }
            return
        }

        if (!recyclerView.canScrollVertically(1)) {
            uiStateFlow.update { it.copy(currentPage = totalPages, totalPages = totalPages) }
            return
        }

        if (!recyclerView.canScrollVertically(-1)) {
            uiStateFlow.update { it.copy(currentPage = 1, totalPages = totalPages) }
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
        uiStateFlow.update { it.copy(currentPage = currentPage, totalPages = totalPages) }
    }

    private fun startExamTimer() {
        timerJob?.cancel()
        var heartbeatCounter = 0

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

                uiStateFlow.update { it.copy(remainingSeconds = examRemainingSeconds) }

                // Cek jam nyata di HP jika batas jam selesai ditentukan (dikecualikan untuk Ujian Susulan)
                if (!isMakeupExam && endTime.isNotBlank()) {
                    val endMin = ExamScheduleHelper.parseMinutesOfDay(endTime)
                    if (endMin != null) {
                        val now = Calendar.getInstance()
                        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
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
            uiStateFlow.update {
                it.copy(
                    isBanActive = true,
                    isPermanentLockout = penaltyManager.isPermanentLockout,
                    violationDescription = reason.description,
                    banRemainingSeconds = banDuration,
                    banStatusMessage = null
                )
            }
        }
    }

    private fun tickBanPenalty() {
        if (penaltyManager.isPermanentLockout) return

        penaltyManager.tickBanSecond()
        val remainingBan = penaltyManager.remainingBanSeconds

        runOnUiThread {
            if (remainingBan > 0) {
                uiStateFlow.update {
                    it.copy(
                        banRemainingSeconds = remainingBan,
                        banStatusMessage = null
                    )
                }
            } else {
                // Selesai ban, pastikan semua syarat terpenuhi kembali
                val isAirplaneOk = environmentMonitor.checkAllImmediate()
                val isKioskOk = kioskEnforcer.isPinned()

                if (isAirplaneOk && isKioskOk) {
                    uiStateFlow.update {
                        it.copy(
                            isBanActive = false,
                            banRemainingSeconds = 0L,
                            banStatusMessage = null
                        )
                    }
                } else {
                    uiStateFlow.update {
                        it.copy(
                            banRemainingSeconds = 0L,
                            banStatusMessage = "Tunggu kepatuhan syarat ujian..."
                        )
                    }
                }
            }
        }
    }

    private fun finishExam(isExpired: Boolean) {
        timerJob?.cancel()

        // 1. Tandai ujian selesai di penyimpanan persisten
        val secureStorage = ExamApp.instance.secureStorage
        secureStorage.markExamFinished(examId)
        secureStorage.resetActiveExamSession()
        ExamReminderScheduler.rescheduleFromStorage(this)

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
        const val EXTRA_IS_MAKEUP_EXAM = "extra_is_makeup_exam"
    }
}
