package id.sch.examkiosk.ui.dashboard

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import id.sch.examkiosk.ExamApp
import id.sch.examkiosk.R
import id.sch.examkiosk.data.model.ExamItem
import id.sch.examkiosk.data.model.ExamItemUiModel
import id.sch.examkiosk.data.model.ExamManifest
import id.sch.examkiosk.data.model.ExamStatus
import id.sch.examkiosk.data.sync.LocalSyncManager
import id.sch.examkiosk.databinding.ActivityDashboardBinding
import id.sch.examkiosk.ui.dialog.DeviceRequirementDialog
import id.sch.examkiosk.ui.dialog.TokenInputDialog
import id.sch.examkiosk.ui.kiosk.KioskExamActivity
import id.sch.examkiosk.util.ExamScheduleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var syncManager: LocalSyncManager
    private lateinit var adapter: ExamAdapter

    private var allExams: List<ExamItem> = emptyList()
    private var currentManifest: ExamManifest? = null
    private var selectedGradeFilter: Int? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Broadcast receiver untuk sensor hardware & jam real-time
    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateHardwareStatus()
            val action = intent?.action
            if (action == Intent.ACTION_TIME_TICK ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED) {
                refreshExamList()
            }
        }
    }

    private var timeTickerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        syncManager = LocalSyncManager(this)

        setupHeader()
        setupBottomNavigation()
        setupRecyclerView()
        setupServerConfig()
        setupGradeFilter()
        loadCachedManifest()
    }

    override fun onResume() {
        super.onResume()
        registerHardwareReceiver()
        updateHardwareStatus()
        refreshExamList()
        updateExamStatistics()
        updateSchoolIdentityUI()
        startTimeTicker()
    }

    override fun onPause() {
        super.onPause()
        stopTimeTicker()
        try {
            unregisterReceiver(hardwareReceiver)
        } catch (_: Exception) {
        }
    }

    private fun startTimeTicker() {
        timeTickerJob?.cancel()
        timeTickerJob = lifecycleScope.launch {
            while (isActive) {
                delay(3000L) // Periksa status jadwal ujian setiap 3 detik secara real-time
                refreshExamList()
            }
        }
    }

    private fun stopTimeTicker() {
        timeTickerJob?.cancel()
        timeTickerJob = null
    }

    private fun setupHeader() {
        val storage = ExamApp.instance.secureStorage
        binding.tvHeaderSchoolName.text = storage.schoolName
        binding.tvHeaderSubtitle.text = "Dashboard Kesiapan & Statistik"

        binding.ivHeaderQuickSync.setOnClickListener {
            val url = storage.serverUrl
            if (url.isNotBlank()) {
                syncManifestAndExams(url)
            } else {
                binding.bottomNavigation.selectedItemId = R.id.nav_settings
            }
        }
    }

    private fun updateSchoolIdentityUI() {
        val storage = ExamApp.instance.secureStorage
        binding.tvHeaderSchoolName.text = storage.schoolName
        binding.tvSettingsSchoolName.text = storage.schoolName
        binding.tvSettingsSchoolTagline.text = storage.schoolTagline
        binding.tvSettingsSchoolNpsn.text = storage.schoolNpsn
        binding.tvSettingsSchoolNsm.text = storage.schoolNsm
        binding.tvSettingsSchoolAddress.text = storage.schoolAddress

        currentManifest?.let { m ->
            if (m.examTitle.isNotBlank()) {
                binding.tvExamTitleBanner.text = m.examTitle
            }
        }
    }

    private fun setupBottomNavigation() {
        runCatching {
            binding.bottomNavigation.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.bottomNavigation.itemRippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    switchTab(0, "Dashboard Kesiapan & Statistik")
                    updateHardwareStatus()
                    updateExamStatistics()
                    true
                }
                R.id.nav_rules -> {
                    switchTab(1, "Panduan & Tata Tertib Ujian")
                    renderRulesList()
                    true
                }
                R.id.nav_exams -> {
                    switchTab(2, "Daftar Paket Soal Ujian")
                    refreshExamList()
                    true
                }
                R.id.nav_settings -> {
                    switchTab(3, "Pengaturan Server & Sinkronisasi")
                    true
                }
                else -> false
            }
        }

        binding.btnGoToExamsTab.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_exams
        }
    }

    private fun switchTab(tabIndex: Int, subtitle: String) {
        binding.tvHeaderSubtitle.text = subtitle
        binding.containerDashboard.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.containerRules.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.containerExams.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.containerSettings.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
    }

    private fun registerHardwareReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            hardwareReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Memperbarui Container 1 (Sensor Kesiapan Hardware) secara responsif & seketika.
     */
    private fun updateHardwareStatus() {
        // 1. Mode Pesawat (Wajib AKTIF)
        val isAirplaneOn = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        // 2. Wi-Fi (Wajib MATI saat ujian)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOff = wifiManager?.isWifiEnabled != true

        // 3. Bluetooth (Wajib MATI)
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        // 4. Mode Hening / Silent / Vibrate (Wajib HENING/GETAR)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRingerSilentOrVibrate = audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL

        // 5. Versi Android (Minimal Android 8 Oreo - API 26)
        val osRelease = Build.VERSION.RELEASE ?: "Unknown"
        val isAndroidVersionOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        // 6. RAM (Minimal 2 GB)
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalBytes = memInfo.totalMem
        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val formattedRam = String.format(Locale.US, "%.1f", totalGb).removeSuffix(".0")
        val isRamOk = totalBytes >= (1.75 * 1024 * 1024 * 1024)

        val isAllOk = isAirplaneOn && isWifiOff && isBluetoothOff && isRingerSilentOrVibrate && isAndroidVersionOk && isRamOk

        // Update Header Alert Card
        if (isAllOk) {
            binding.cardDeviceStatusHeader.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_sensor_ok_bg))
            binding.cardDeviceStatusHeader.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
            binding.ivDeviceStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.ivDeviceStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            binding.tvDeviceStatusTitle.text = "Perangkat Siap Ujian"
            binding.tvDeviceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.primary))
            binding.tvDeviceStatusDesc.text = "Semua pengaturan keamanan telah memenuhi standar madrasah."
        } else {
            binding.cardDeviceStatusHeader.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_sensor_bad_bg))
            binding.cardDeviceStatusHeader.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
            binding.ivDeviceStatusIcon.setImageResource(R.drawable.ic_warning)
            binding.ivDeviceStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.tvDeviceStatusTitle.text = "Pengaturan Belum Sesuai Standar"
            binding.tvDeviceStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.tvDeviceStatusDesc.text = "Harap sesuaikan pengaturan di bawah sebelum ujian dimulai."
        }

        // 1. Badge Mode Pesawat
        if (isAirplaneOn) {
            binding.tvAirplaneStatus.text = "Mode Pesawat: Aktif (Aman)"
            binding.tvAirplaneStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivAirplaneIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardAirplaneStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
        } else {
            binding.tvAirplaneStatus.text = "Mode Pesawat: Nonaktif (Wajib ON!)"
            binding.tvAirplaneStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivAirplaneIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardAirplaneStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
        }

        // 2. Badge Wi-Fi
        if (isWifiOff) {
            binding.tvWifiStatus.text = "Wi-Fi: Nonaktif (Aman)"
            binding.tvWifiStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivWifiIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardWifiStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
        } else {
            binding.tvWifiStatus.text = "Wi-Fi: Aktif (Wajib Dimatikan!)"
            binding.tvWifiStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivWifiIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardWifiStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
        }

        // 3. Badge Bluetooth
        if (isBluetoothOff) {
            binding.tvBluetoothStatus.text = "Bluetooth: Nonaktif (Aman)"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivBluetoothIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardBluetoothStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
        } else {
            binding.tvBluetoothStatus.text = "Bluetooth: Aktif (Wajib Dimatikan!)"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivBluetoothIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardBluetoothStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
        }

        // 4. Badge Mode Hening
        if (isRingerSilentOrVibrate) {
            binding.tvRingerStatus.text = "Mode Suara: Hening/Getar (Aman)"
            binding.tvRingerStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivRingerIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardRingerStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
        } else {
            binding.tvRingerStatus.text = "Mode Suara: Berdering (Wajib Hening!)"
            binding.tvRingerStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivRingerIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardRingerStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
        }

        // 5. Spesifikasi Versi Android
        if (isAndroidVersionOk) {
            binding.tvAndroidVersionStatus.text = "Versi Android: Android $osRelease (Memenuhi Standar)"
            binding.tvAndroidVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivAndroidVersionIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardAndroidVersionStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
            binding.tvAndroidVersionRequirement.text = "HP Siswa: Android $osRelease • Standar minimal: Android 8 (Oreo)"
        } else {
            binding.tvAndroidVersionStatus.text = "Versi Android: Android $osRelease (Di Bawah Standar!)"
            binding.tvAndroidVersionStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivAndroidVersionIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardAndroidVersionStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
            binding.tvAndroidVersionRequirement.text = "HP Siswa: Android $osRelease • Standar minimal: Android 8 (Oreo)"
        }

        // 6. Spesifikasi RAM
        if (isRamOk) {
            binding.tvRamStatus.text = "RAM HP: $formattedRam GB (Memenuhi Standar)"
            binding.tvRamStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivRamIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.cardRamStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_ok_border)
            binding.tvRamRequirement.text = "HP Siswa: $formattedRam GB RAM • Standar minimal: 2 GB RAM"
        } else {
            binding.tvRamStatus.text = "RAM HP: $formattedRam GB (Di Bawah Standar!)"
            binding.tvRamStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivRamIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.cardRamStatus.strokeColor = ContextCompat.getColor(this, R.color.card_sensor_bad_border)
            binding.tvRamRequirement.text = "HP Siswa: $formattedRam GB RAM • Standar minimal: 2 GB RAM"
        }
    }

    /**
     * Memperbarui Container 2 (Statistik Ujian) sesuai angkatan yang dipilih siswa.
     */
    private fun updateExamStatistics() {
        val secureStorage = ExamApp.instance.secureStorage
        val targetGrade = secureStorage.selectedGrade
        val gradeExams = allExams.filter { it.gradeLevel == targetGrade }
        val total = gradeExams.size
        val finished = gradeExams.count { secureStorage.isExamFinished(it.id) }
        val remaining = (total - finished).coerceAtLeast(0)

        binding.tvStatTotalExams.text = total.toString()
        binding.tvStatFinishedExams.text = finished.toString()
        binding.tvStatRemainingExams.text = remaining.toString()
    }

    /**
     * Menampilkan daftar tata tertib dinamis dari server pada Tab 2.
     */
    private fun renderRulesList() {
        binding.llRulesContainer.removeAllViews()

        val rules = currentManifest?.examRules?.ifEmpty { null } ?: listOf(
            "Siswa wajib memasuki ruang ujian 15 menit sebelum ujian dimulai.",
            "Perangkat Android WAJIB dalam Mode Pesawat, Mode Hening, dan Wi-Fi/Bluetooth mati.",
            "Aplikasi mengunci layar secara otomatis (Mode Kiosk). Dilarang keras mencoba keluar.",
            "Percobaan kecurangan (membuka aplikasi lain, reboot HP, atau swipe keluar) memicu PENALTI KUNCI LAYAR BERJENJANG.",
            "Waktu ujian tetap berjalan normal di latar belakang selama masa penalti.",
            "Soal ujian akan otomatis terkunci atau selesai ketika waktu jam ujian berakhir.",
            "Jawaban wajib disalin ke Lembar Jawaban Komputer (LJK) fisik yang disediakan pengawas.",
            "Jika ada kendala teknis atau baterai lemah, segera angkat tangan dan hubungi Pengawas Ujian.",
            "Pengawas memiliki Master PIN darurat untuk membuka penalti ban jika diperlukan.",
            "Berdoalah sebelum mengerjakan soal ujian. Kejujuran adalah cerminan akhlak mulia."
        )

        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        val density = resources.displayMetrics.density

        rules.forEachIndexed { index, ruleText ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            }

            // Circle number badge [1], [2], dst.
            val numBadge = TextView(this).apply {
                text = "${index + 1}"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.bg_badge_rounded)
                backgroundTintList = ColorStateList.valueOf(primaryColor)
                layoutParams = LinearLayout.LayoutParams(
                    (24 * density).toInt(),
                    (24 * density).toInt()
                ).apply {
                    topMargin = (2 * density).toInt()
                    marginEnd = (12 * density).toInt()
                }
            }

            // Rule text (dengan padding vertikal & font padding agar huruf berekor g, j, p, q, y tidak terpotong)
            val tvRule = TextView(this).apply {
                text = ruleText
                setTextColor(textPrimaryColor)
                textSize = 13.5f
                includeFontPadding = true
                setLineSpacing(5 * density, 1f)
                setPaddingRelative(0, 0, 0, (6 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            row.addView(numBadge)
            row.addView(tvRule)
            binding.llRulesContainer.addView(row)
        }
    }

    private fun setupRecyclerView() {
        adapter = ExamAdapter { itemUi ->
            handleExamAction(itemUi)
        }
        binding.rvExams.layoutManager = LinearLayoutManager(this)
        binding.rvExams.adapter = adapter
    }

    private fun setupServerConfig() {
        val secureStorage = ExamApp.instance.secureStorage
        val currentUrl = secureStorage.serverUrl

        // Ekstrak host dan port dari URL yang tersimpan di backend
        val (currentHost, currentPort) = parseHostAndPort(currentUrl)
        binding.etServerHost.setText(currentHost)
        binding.etServerPort.setText(currentPort)

        binding.btnTestConnection.setOnClickListener {
            val url = buildAndValidateServerUrl() ?: return@setOnClickListener
            testServerConnection(url)
        }

        binding.btnSyncExams.setOnClickListener {
            val url = buildAndValidateServerUrl() ?: return@setOnClickListener
            secureStorage.serverUrl = url
            syncManifestAndExams(url)
        }
    }

    private fun parseHostAndPort(url: String): Pair<String, String> {
        val clean = url.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")

        return if (clean.contains(":")) {
            val host = clean.substringBefore(":")
            val port = clean.substringAfter(":")
            Pair(host, port)
        } else if (clean.isNotBlank()) {
            Pair(clean, "8080")
        } else {
            Pair("", "")
        }
    }

    private fun buildAndValidateServerUrl(): String? {
        val host = binding.etServerHost.text?.toString()?.trim() ?: ""
        var port = binding.etServerPort.text?.toString()?.trim() ?: ""

        if (host.isEmpty()) {
            binding.tilServerHost.error = "IP server tidak boleh kosong"
            return null
        }
        binding.tilServerHost.error = null

        if (port.isEmpty()) {
            port = "8080"
        }
        binding.tilServerPort.error = null

        val combined = "$host:$port"
        return LocalSyncManager.normalizeUrl(combined)
    }

    private fun testServerConnection(url: String) {
        binding.tvServerStatus.text = "Menguji koneksi ke $url..."
        binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            val result = syncManager.testConnection(url)
            binding.btnTestConnection.isEnabled = true
            result.onSuccess { reachable ->
                if (reachable) {
                    binding.tvServerStatus.text = "Server online & dapat diakses"
                    binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_ready_text))
                } else {
                    binding.tvServerStatus.text = "Server merespons dengan kesalahan"
                    binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_danger_text))
                }
            }.onFailure { err ->
                binding.tvServerStatus.text = "Gagal terhubung: ${err.localizedMessage}"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_danger_text))
            }
        }
    }

    private fun syncManifestAndExams(url: String) {
        binding.tvServerStatus.text = "Menghubungi server untuk sinkronisasi..."
        binding.tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnSyncExams.isEnabled = false

        lifecycleScope.launch {
            val manifestResult = syncManager.fetchManifest(url)

            manifestResult.onSuccess { manifest ->
                val secureStorage = ExamApp.instance.secureStorage
                if (manifest.masterPinSha256.isNotBlank()) {
                    secureStorage.masterPinHash = manifest.masterPinSha256
                }
                if (manifest.schoolName.isNotBlank()) {
                    secureStorage.schoolName = manifest.schoolName
                }
                if (manifest.schoolTagline.isNotBlank()) {
                    secureStorage.schoolTagline = manifest.schoolTagline
                }
                if (manifest.schoolNpsn.isNotBlank()) {
                    secureStorage.schoolNpsn = manifest.schoolNpsn
                }
                if (manifest.schoolNsm.isNotBlank()) {
                    secureStorage.schoolNsm = manifest.schoolNsm
                }
                if (manifest.schoolAddress.isNotBlank()) {
                    secureStorage.schoolAddress = manifest.schoolAddress
                }

                secureStorage.cachedManifest = json.encodeToString(ExamManifest.serializer(), manifest)
                currentManifest = manifest
                allExams = manifest.exams

                // Hanya sinkronkan berkas soal untuk angkatan yang dipilih siswa
                val targetGrade = secureStorage.selectedGrade
                val targetExams = manifest.exams.filter { it.gradeLevel == targetGrade }

                // Bersihkan berkas fisik ujian lokal selain angkatan terpilih
                val activeExamIds = targetExams.map { it.id }.toSet()
                syncManager.cleanupOrphanedExams(activeExamIds)

                // Otomatis unduh seluruh berkas .enc yang belum ada / berubah untuk angkatan tersebut
                var downloadedCount = 0
                val toDownload = targetExams.filter { !syncManager.isExamFileValid(it.id, it.fileSha256) }
                val totalToDownload = toDownload.size

                if (totalToDownload > 0) {
                    binding.tvServerStatus.text = "Mengunduh paket soal Kelas $targetGrade (0/$totalToDownload)..."

                    for (exam in toDownload) {
                        val fullUrl = resolveExamDownloadUrl(url, exam)
                        syncManager.downloadExamFile(
                            downloadUrl = fullUrl,
                            examId = exam.id,
                            expectedSha256 = exam.fileSha256
                        )
                        downloadedCount++
                        binding.tvServerStatus.text = "Mengunduh paket soal Kelas $targetGrade ($downloadedCount/$totalToDownload)..."
                    }
                }

                binding.btnSyncExams.isEnabled = true
                binding.tvServerStatus.text = "Sinkronisasi selesai: ${targetExams.size} soal Kelas $targetGrade siap"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_ready_text))
                
                updateSchoolIdentityUI()
                refreshExamList()
                updateExamStatistics()
                renderRulesList()
                Toast.makeText(this@DashboardActivity, "Paket soal Kelas $targetGrade berhasil disinkronkan!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                binding.btnSyncExams.isEnabled = true
                binding.tvServerStatus.text = "Sinkronisasi gagal: ${err.localizedMessage}"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_danger_text))
                Toast.makeText(this@DashboardActivity, "Gagal mengunduh manifest: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadCachedManifest() {
        val cached = ExamApp.instance.secureStorage.cachedManifest
        if (!cached.isNullOrEmpty()) {
            try {
                val manifest = json.decodeFromString<ExamManifest>(cached)
                currentManifest = manifest
                allExams = manifest.exams
                updateSchoolIdentityUI()
                refreshExamList()
                updateExamStatistics()
                renderRulesList()
            } catch (_: Exception) {
            }
        }
    }

    private fun setupGradeFilter() {
        val storage = ExamApp.instance.secureStorage
        val currentGrade = storage.selectedGrade
        selectedGradeFilter = currentGrade

        // Tab 4 Settings Sync Target Grade Segmented Buttons
        updateSyncGradeChips(currentGrade)

        binding.toggleGroupSyncGrade.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val grade = when (checkedId) {
                    R.id.btnSyncGrade7 -> 7
                    R.id.btnSyncGrade8 -> 8
                    R.id.btnSyncGrade9 -> 9
                    else -> storage.selectedGrade
                }
                storage.selectedGrade = grade
                selectedGradeFilter = grade
                refreshExamList()
                updateExamStatistics()
            }
        }
    }

    private fun updateSyncGradeChips(grade: Int) {
        val targetId = when (grade) {
            7 -> R.id.btnSyncGrade7
            8 -> R.id.btnSyncGrade8
            9 -> R.id.btnSyncGrade9
            else -> R.id.btnSyncGrade7
        }
        binding.toggleGroupSyncGrade.check(targetId)
    }

    private fun refreshExamList() {
        val secureStorage = ExamApp.instance.secureStorage
        val targetGrade = selectedGradeFilter ?: secureStorage.selectedGrade
        val filtered = allExams.filter { it.gradeLevel == targetGrade }

        // Urutkan paket soal secara kronologis berdasarkan kalender (Tanggal, lalu Jam Mulai)
        val sortedExams = filtered.sortedWith(
            compareBy<ExamItem> { it.examDate.ifEmpty { "9999-99-99" } }
                .thenBy { it.startTime.ifEmpty { "99:99" } }
        )

        val uiModels = sortedExams.map { exam ->
            val isDownloaded = syncManager.isExamFileValid(exam.id, exam.fileSha256)
            val isFinished = secureStorage.isExamFinished(exam.id)
            val isActive = secureStorage.activeExamId == exam.id
            val scheduleState = ExamScheduleHelper.getScheduleState(exam)

            val status = when {
                isFinished -> ExamStatus.SELESAI
                isActive -> ExamStatus.SEDANG_UJIAN
                isDownloaded -> ExamStatus.TERSIMPAN
                else -> ExamStatus.BELUM_DIUNDUH
            }

            ExamItemUiModel(
                exam = exam,
                status = status,
                isFilePresent = isDownloaded,
                scheduleState = scheduleState
            )
        }

        adapter.submitList(uiModels)

        if (uiModels.isEmpty()) {
            binding.layoutEmptyExams.visibility = View.VISIBLE
            binding.rvExams.visibility = View.GONE
        } else {
            binding.layoutEmptyExams.visibility = View.GONE
            binding.rvExams.visibility = View.VISIBLE
        }
    }

    private fun handleExamAction(itemUi: ExamItemUiModel) {
        val exam = itemUi.exam
        val secureStorage = ExamApp.instance.secureStorage

        if (secureStorage.isExamFinished(exam.id)) {
            Toast.makeText(this, "Ujian ini telah selesai dikerjakan.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!itemUi.isFilePresent) {
            downloadExamFile(exam)
            return
        }

        // 1. Cek syarat hardware & spesifikasi HP sebelum masuk ujian
        val isAirplaneOn = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOff = wifiManager?.isWifiEnabled != true

        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRingerSilentOrVibrate = audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL

        val isAndroidVersionOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val isRamOk = memInfo.totalMem >= (1.75 * 1024 * 1024 * 1024)

        if (!isAirplaneOn || !isWifiOff || !isBluetoothOff || !isRingerSilentOrVibrate || !isAndroidVersionOk || !isRamOk) {
            val dialog = DeviceRequirementDialog.newInstance()
            dialog.show(supportFragmentManager, DeviceRequirementDialog.TAG)
            return
        }

        // 2. Cek Jadwal Waktu Kalender & Jam Nyata HP
        val scheduleState = ExamScheduleHelper.getScheduleState(exam)
        when (scheduleState) {
            ExamScheduleHelper.ScheduleState.NOT_STARTED_YET -> {
                val dateMsg = if (exam.examDate.isNotBlank()) {
                    val formatted = ExamScheduleHelper.formatDisplayDate(exam.examDate, exam.examDay)
                    "$formatted pukul ${exam.startTime.ifEmpty { "sesuai jadwal" }} WIB"
                } else if (exam.startTime.isNotBlank()) {
                    "pukul ${exam.startTime} WIB"
                } else {
                    "sesuai jadwal proktor"
                }
                Toast.makeText(
                    this,
                    "Ujian belum dimulai! Jadwal pengerjaan: $dateMsg.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            ExamScheduleHelper.ScheduleState.EXPIRED -> {
                val timeLabel = if (exam.endTime.isNotBlank()) "Batas pukul ${exam.endTime} WIB" else "Jadwal telah lewat"
                Toast.makeText(
                    this,
                    "Waktu ujian ini telah berakhir ($timeLabel)!",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            ExamScheduleHelper.ScheduleState.ACTIVE -> {
                // Tampilkan dialog token angkatan
                val dialog = TokenInputDialog.newInstance(exam) { token ->
                    startExamActivity(exam, token)
                }
                dialog.show(supportFragmentManager, TokenInputDialog.TAG)
            }
        }
    }

    private fun resolveExamDownloadUrl(baseServerUrl: String, exam: ExamItem): String {
        val cleanBase = LocalSyncManager.normalizeUrl(baseServerUrl)

        return if (exam.downloadUrl.startsWith("http://") || exam.downloadUrl.startsWith("https://")) {
            try {
                val uri = java.net.URI(exam.downloadUrl)
                val path = uri.rawPath.trimStart('/')
                "$cleanBase/$path"
            } catch (_: Exception) {
                "$cleanBase/files/${exam.id}.enc"
            }
        } else {
            "$cleanBase/${exam.downloadUrl.trimStart('/')}"
        }
    }

    private fun downloadExamFile(exam: ExamItem) {
        val fullUrl = resolveExamDownloadUrl(ExamApp.instance.secureStorage.serverUrl, exam)

        Toast.makeText(this, "Mengunduh berkas ${exam.subjectName}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = syncManager.downloadExamFile(
                downloadUrl = fullUrl,
                examId = exam.id,
                expectedSha256 = exam.fileSha256
            )
            result.onSuccess {
                Toast.makeText(this@DashboardActivity, "Berkas ${exam.subjectName} berhasil diunduh!", Toast.LENGTH_SHORT).show()
                refreshExamList()
            }.onFailure { err ->
                Toast.makeText(this@DashboardActivity, "Gagal mengunduh: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startExamActivity(exam: ExamItem, token: String) {
        // Hitung durasi aktual yang tersisa hingga jam selesai ujian
        val effectiveDuration = ExamScheduleHelper.calculateEffectiveDurationMinutes(exam)

        val intent = Intent(this, KioskExamActivity::class.java).apply {
            putExtra(KioskExamActivity.EXTRA_EXAM_ID, exam.id)
            putExtra(KioskExamActivity.EXTRA_SUBJECT_NAME, exam.subjectName)
            putExtra(KioskExamActivity.EXTRA_GRADE_LEVEL, exam.gradeLevel)
            putExtra(KioskExamActivity.EXTRA_DURATION_MINUTES, effectiveDuration)
            putExtra(KioskExamActivity.EXTRA_END_TIME, exam.endTime)
            putExtra(KioskExamActivity.EXTRA_SALT_HEX, exam.pbkdf2SaltHex)
            putExtra(KioskExamActivity.EXTRA_IV_HEX, exam.gcmIvHex)
            putExtra(KioskExamActivity.EXTRA_TOKEN, token)
        }
        startActivity(intent)
    }
}
