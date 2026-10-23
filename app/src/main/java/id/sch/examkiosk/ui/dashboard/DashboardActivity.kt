package id.sch.examkiosk.ui.dashboard

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import id.sch.examkiosk.ui.dialog.TokenInputDialog
import id.sch.examkiosk.ui.kiosk.KioskExamActivity
import id.sch.examkiosk.util.ExamScheduleHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var syncManager: LocalSyncManager
    private lateinit var adapter: ExamAdapter

    private var allExams: List<ExamItem> = emptyList()
    private var currentManifest: ExamManifest? = null
    private var selectedGradeFilter: Int? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Broadcast receiver untuk sensor hardware real-time
    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateHardwareStatus()
        }
    }

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
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(hardwareReceiver)
        } catch (_: Exception) {
        }
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
        binding.tvRulesSchoolHeader.text = "${storage.schoolName} • Wajib dipatuhi seluruh peserta ujian."
        binding.tvExamSubtitleBanner.text = "Tahun Ajaran 2026/2027 • ${storage.schoolName}"

        currentManifest?.let { m ->
            if (m.examTitle.isNotBlank()) {
                binding.tvExamTitleBanner.text = m.examTitle
            }
        }
    }

    private fun setupBottomNavigation() {
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

        val isAllOk = isAirplaneOn && isWifiOff && isBluetoothOff && isRingerSilentOrVibrate

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

            // Rule text
            val tvRule = TextView(this).apply {
                text = ruleText
                setTextColor(textPrimaryColor)
                textSize = 13f
                setLineSpacing(4 * density, 1f)
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
        binding.etServerUrl.setText(secureStorage.serverUrl)

        binding.btnTestConnection.setOnClickListener {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                binding.tilServerUrl.error = "IP server tidak boleh kosong"
                return@setOnClickListener
            }
            binding.tilServerUrl.error = null
            testServerConnection(url)
        }

        binding.btnSyncExams.setOnClickListener {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                binding.tilServerUrl.error = "IP server tidak boleh kosong"
                return@setOnClickListener
            }
            binding.tilServerUrl.error = null
            secureStorage.serverUrl = url
            syncManifestAndExams(url)
        }
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
                        val fullUrl = if (exam.downloadUrl.startsWith("http://") || exam.downloadUrl.startsWith("https://")) {
                            exam.downloadUrl
                        } else {
                            val base = url.trimEnd('/')
                            "$base/${exam.downloadUrl.trimStart('/')}"
                        }
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

        // Tab 3 Filter Chips (Kelas 7, 8, 9)
        when (currentGrade) {
            7 -> binding.chipGrade7.isChecked = true
            8 -> binding.chipGrade8.isChecked = true
            9 -> binding.chipGrade9.isChecked = true
            else -> binding.chipGrade7.isChecked = true
        }

        binding.chipGroupGrade.setOnCheckedStateChangeListener { _, checkedIds ->
            val grade = when {
                checkedIds.contains(R.id.chipGrade7) -> 7
                checkedIds.contains(R.id.chipGrade8) -> 8
                checkedIds.contains(R.id.chipGrade9) -> 9
                else -> storage.selectedGrade
            }
            selectedGradeFilter = grade
            storage.selectedGrade = grade
            updateSyncGradeChips(grade)
            refreshExamList()
            updateExamStatistics()
        }

        // Tab 4 Settings Sync Target Grade Chips
        updateSyncGradeChips(currentGrade)

        binding.chipGroupSyncGrade.setOnCheckedStateChangeListener { _, checkedIds ->
            val grade = when {
                checkedIds.contains(R.id.chipSyncGrade7) -> 7
                checkedIds.contains(R.id.chipSyncGrade8) -> 8
                checkedIds.contains(R.id.chipSyncGrade9) -> 9
                else -> storage.selectedGrade
            }
            storage.selectedGrade = grade
            selectedGradeFilter = grade
            when (grade) {
                7 -> binding.chipGrade7.isChecked = true
                8 -> binding.chipGrade8.isChecked = true
                9 -> binding.chipGrade9.isChecked = true
            }
            refreshExamList()
            updateExamStatistics()
        }
    }

    private fun updateSyncGradeChips(grade: Int) {
        when (grade) {
            7 -> binding.chipSyncGrade7.isChecked = true
            8 -> binding.chipSyncGrade8.isChecked = true
            9 -> binding.chipSyncGrade9.isChecked = true
            else -> binding.chipSyncGrade7.isChecked = true
        }
    }

    private fun refreshExamList() {
        val secureStorage = ExamApp.instance.secureStorage
        val filterGrade = selectedGradeFilter ?: secureStorage.selectedGrade
        val filtered = allExams.filter { it.gradeLevel == filterGrade }

        val uiModels = filtered.map { exam ->
            val isDownloaded = syncManager.isExamFileValid(exam.id, exam.fileSha256)
            val isFinished = secureStorage.isExamFinished(exam.id)
            val isActive = secureStorage.activeExamId == exam.id

            val status = when {
                isFinished -> ExamStatus.SELESAI
                isActive -> ExamStatus.SEDANG_UJIAN
                isDownloaded -> ExamStatus.TERSIMPAN
                else -> ExamStatus.BELUM_DIUNDUH
            }

            ExamItemUiModel(
                exam = exam,
                status = status,
                isFilePresent = isDownloaded
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

        // 1. Cek syarat hardware sebelum masuk ujian
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

        if (!isAirplaneOn || !isWifiOff || !isBluetoothOff || !isRingerSilentOrVibrate) {
            AlertDialog.Builder(this)
                .setTitle("Pengaturan Belum Sesuai Standar")
                .setMessage("Sebelum ujian dimulai, pastikan:\n• Mode Pesawat AKTIF\n• Wi-Fi NONAKTIF\n• Bluetooth NONAKTIF\n• Mode Suara HENING / GETAR\n\nSilakan sesuaikan perangkat Anda.")
                .setPositiveButton("Mengerti", null)
                .show()
            return
        }

        // 2. Cek Jadwal Waktu Jam Nyata HP
        val scheduleState = ExamScheduleHelper.getScheduleState(exam)
        when (scheduleState) {
            ExamScheduleHelper.ScheduleState.NOT_STARTED_YET -> {
                Toast.makeText(
                    this,
                    "Ujian belum dimulai! Jadwal pengerjaan pukul ${exam.startTime} WIB.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            ExamScheduleHelper.ScheduleState.EXPIRED -> {
                Toast.makeText(
                    this,
                    "Waktu ujian ini telah berakhir (Batas pukul ${exam.endTime} WIB)!",
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

    private fun downloadExamFile(exam: ExamItem) {
        val fullUrl = if (exam.downloadUrl.startsWith("http://") || exam.downloadUrl.startsWith("https://")) {
            exam.downloadUrl
        } else {
            val base = ExamApp.instance.secureStorage.serverUrl.trimEnd('/')
            "$base/${exam.downloadUrl.trimStart('/')}"
        }

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
