package id.sch.examkiosk.ui.dashboard

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var syncManager: LocalSyncManager
    private lateinit var adapter: ExamAdapter

    private var allExams: List<ExamItem> = emptyList()
    private var selectedGradeFilter: Int? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Broadcast receiver for hardware states (Airplane mode & Bluetooth)
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
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(hardwareReceiver)
        } catch (_: Exception) {
        }
    }

    private fun registerHardwareReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            hardwareReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateHardwareStatus() {
        val isAirplaneOn = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        // Airplane Badge UI
        if (isAirplaneOn) {
            binding.tvAirplaneStatus.text = "Pesawat: ON"
            binding.tvAirplaneStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivAirplaneIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.badgeAirplaneStatus.setBackgroundResource(R.drawable.bg_badge_rounded)
        } else {
            binding.tvAirplaneStatus.text = "Pesawat: OFF (Wajib ON)"
            binding.tvAirplaneStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivAirplaneIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
        }

        // Bluetooth Badge UI
        if (isBluetoothOff) {
            binding.tvBluetoothStatus.text = "BT: OFF"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_ready_text))
            binding.ivBluetoothIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_ready_text))
        } else {
            binding.tvBluetoothStatus.text = "BT: ON (Wajib OFF)"
            binding.tvBluetoothStatus.setTextColor(ContextCompat.getColor(this, R.color.badge_danger_text))
            binding.ivBluetoothIcon.setColorFilter(ContextCompat.getColor(this, R.color.badge_danger_text))
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
            if (url.contains("localhost") || url.contains("127.0.0.1")) {
                binding.tilServerUrl.error = "Gunakan IP Wi-Fi laptop (contoh: 192.168.0.109:8080), bukan localhost!"
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
            if (url.contains("localhost") || url.contains("127.0.0.1")) {
                binding.tilServerUrl.error = "Gunakan IP Wi-Fi laptop (contoh: 192.168.0.109:8080), bukan localhost!"
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
                    binding.tvServerStatus.text = "✓ Server online & dapat diakses"
                    binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_ready_text))
                } else {
                    binding.tvServerStatus.text = "✗ Server merespons dengan kesalahan"
                    binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_danger_text))
                }
            }.onFailure { err ->
                binding.tvServerStatus.text = "✗ Gagal terhubung: ${err.localizedMessage}"
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
                secureStorage.cachedManifest = json.encodeToString(ExamManifest.serializer(), manifest)
                allExams = manifest.exams

                // Otomatis unduh seluruh berkas .enc yang belum ada / berubah
                var downloadedCount = 0
                val totalToDownload = manifest.exams.count { !syncManager.isExamFileValid(it.id, it.fileSha256) }

                if (totalToDownload > 0) {
                    binding.tvServerStatus.text = "Mengunduh paket soal (0/$totalToDownload)..."

                    for (exam in manifest.exams) {
                        if (!syncManager.isExamFileValid(exam.id, exam.fileSha256)) {
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
                            binding.tvServerStatus.text = "Mengunduh paket soal ($downloadedCount/$totalToDownload)..."
                        }
                    }
                }

                binding.btnSyncExams.isEnabled = true
                binding.tvServerStatus.text = "✓ Sinkronisasi selesai (${manifest.exams.size} soal siap)"
                binding.tvServerStatus.setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.badge_ready_text))
                refreshExamList()
                Toast.makeText(this@DashboardActivity, "Seluruh paket soal berhasil disinkronkan!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                binding.btnSyncExams.isEnabled = true
                binding.tvServerStatus.text = "✗ Sinkronisasi gagal: ${err.localizedMessage}"
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
                allExams = manifest.exams
                refreshExamList()
            } catch (_: Exception) {
            }
        }
    }

    private fun setupGradeFilter() {
        binding.chipGroupGrade.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedGradeFilter = when {
                checkedIds.contains(R.id.chipGrade7) -> 7
                checkedIds.contains(R.id.chipGrade8) -> 8
                checkedIds.contains(R.id.chipGrade9) -> 9
                else -> null
            }
            refreshExamList()
        }
    }

    private fun refreshExamList() {
        val secureStorage = ExamApp.instance.secureStorage
        val filtered = if (selectedGradeFilter != null) {
            allExams.filter { it.gradeLevel == selectedGradeFilter }
        } else {
            allExams
        }

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
        binding.tvExamCount.text = "${uiModels.size} Ujian"

        if (uiModels.isEmpty()) {
            binding.cardEmptyState.visibility = View.VISIBLE
            binding.rvExams.visibility = View.GONE
        } else {
            binding.cardEmptyState.visibility = View.GONE
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

        // Cek syarat hardware sebelum masuk ujian
        val isAirplaneOn = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        if (!isAirplaneOn || !isBluetoothOff) {
            AlertDialog.Builder(this)
                .setTitle("Syarat Ujian Belum Terpenuhi")
                .setMessage("Sebelum memulai ujian:\n• Mode Pesawat WAJIB AKTIF\n• Bluetooth WAJIB DIMATIKAN\n\nSilakan atur perangkat Anda terlebih dahulu.")
                .setPositiveButton("Mengerti", null)
                .show()
            return
        }

        // Tampilkan dialog token
        val dialog = TokenInputDialog.newInstance(exam) { token ->
            startExamActivity(exam, token)
        }
        dialog.show(supportFragmentManager, TokenInputDialog.TAG)
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
        val intent = Intent(this, KioskExamActivity::class.java).apply {
            putExtra(KioskExamActivity.EXTRA_EXAM_ID, exam.id)
            putExtra(KioskExamActivity.EXTRA_SUBJECT_NAME, exam.subjectName)
            putExtra(KioskExamActivity.EXTRA_GRADE_LEVEL, exam.gradeLevel)
            putExtra(KioskExamActivity.EXTRA_DURATION_MINUTES, exam.durationMinutes)
            putExtra(KioskExamActivity.EXTRA_SALT_HEX, exam.pbkdf2SaltHex)
            putExtra(KioskExamActivity.EXTRA_IV_HEX, exam.gcmIvHex)
            putExtra(KioskExamActivity.EXTRA_TOKEN, token)
        }
        startActivity(intent)
    }
}
