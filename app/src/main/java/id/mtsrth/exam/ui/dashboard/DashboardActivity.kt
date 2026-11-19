package id.mtsrth.exam.ui.dashboard

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.model.ExamItem
import id.mtsrth.exam.data.model.ExamItemUiModel
import id.mtsrth.exam.data.model.ExamManifest
import id.mtsrth.exam.data.model.ExamStatus
import id.mtsrth.exam.data.sync.LocalSyncManager
import id.mtsrth.exam.notification.ExamReminderScheduler
import id.mtsrth.exam.ui.kiosk.KioskExamActivity
import id.mtsrth.exam.ui.splash.BrandedSplashOverlay
import id.mtsrth.exam.ui.theme.ExamKioskTheme
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var syncManager: LocalSyncManager

    private var allExams: List<ExamItem> = emptyList()
    private var currentManifest: ExamManifest? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val uiStateFlow = MutableStateFlow(DashboardUiState())

    private var showBrandedSplash by mutableStateOf(false)
    private var showDeviceRequirementDialog by mutableStateOf(false)
    private var pendingTokenExam by mutableStateOf<ExamItem?>(null)
    private var pendingMakeupExam by mutableStateOf<ExamItem?>(null)
    private var pendingGradeSwitch by mutableStateOf<Int?>(null)

    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateHardwareStatus()
            val action = intent?.action
            if (action == Intent.ACTION_TIME_TICK ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED
            ) {
                refreshExamList()
            }
        }
    }

    private var timeTickerJob: Job? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationPermissionState()
        ExamReminderScheduler.rescheduleFromStorage(this)
        ExamReminderScheduler.checkDueRemindersNow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        val initialDarkMode = ExamApp.instance.secureStorage.isDarkMode
        applyEdgeToEdgeTheme(initialDarkMode)
        super.onCreate(savedInstanceState)

        // Tampilkan branded splash overlay hanya saat cold start (bukan saat rotasi layar)
        val isFreshLaunch = savedInstanceState == null
        showBrandedSplash = isFreshLaunch

        syncManager = LocalSyncManager(this)
        initializeStateFromStorage()
        loadCachedManifest()
        handleDeepLinkIntent(intent)
        if (!isFreshLaunch) {
            requestNotificationPermissionIfNeeded()
        }

        setContent {
            val uiState by uiStateFlow.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.isDarkMode) {
                applyEdgeToEdgeTheme(uiState.isDarkMode)
            }

            ExamKioskTheme(darkTheme = uiState.isDarkMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(
                    uiState = uiState,
                    onSelectTab = { tab ->
                        uiStateFlow.update { it.copy(currentTab = tab) }
                        if (tab == DashboardTab.DASHBOARD) {
                            updateHardwareStatus()
                            updateExamStatistics()
                        } else if (tab == DashboardTab.EXAMS) {
                            refreshExamList()
                            updateExamStatistics()
                        }
                    },
                    onQuickSyncClick = {
                        val url = ExamApp.instance.secureStorage.serverUrl
                        if (url.isNotBlank()) {
                            syncManifestAndExams(url)
                        } else {
                            uiStateFlow.update { it.copy(currentTab = DashboardTab.SETTINGS) }
                            Toast.makeText(
                                this@DashboardActivity,
                                "Atur alamat IP server proktor terlebih dahulu",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onToggleDarkMode = {
                        val nextDark = !uiStateFlow.value.isDarkMode
                        ExamApp.instance.secureStorage.isDarkMode = nextDark
                        uiStateFlow.update { it.copy(isDarkMode = nextDark) }
                    },
                    onOpenSystemSettings = { action -> openSystemSettingsSafe(action) },
                    onToggleRulesAcknowledge = { ack ->
                        ExamApp.instance.secureStorage.rulesAcknowledged = ack
                        uiStateFlow.update { it.copy(rulesAcknowledged = ack) }
                    },
                    onExamAction = { item -> handleExamAction(item) },
                    onSelectFilter = { filter ->
                        uiStateFlow.update { it.copy(examFilter = filter) }
                    },
                    onSelectGrade = { grade ->
                        val currentState = uiStateFlow.value
                        if (grade != currentState.selectedGrade && currentState.downloadedExamCount > 0) {
                            pendingGradeSwitch = grade
                        } else {
                            applyGradeSelection(grade)
                        }
                    },
                    onServerHostChange = { host ->
                        uiStateFlow.update {
                            it.copy(
                                serverHost = host,
                                connectionTestState = ConnectionTestState.Idle
                            )
                        }
                    },
                    onServerPortChange = { port ->
                        uiStateFlow.update {
                            it.copy(
                                serverPort = port,
                                connectionTestState = ConnectionTestState.Idle
                            )
                        }
                    },
                    onSelectServerPreset = { host, port ->
                        uiStateFlow.update {
                            it.copy(
                                serverHost = host,
                                serverPort = port,
                                connectionTestState = ConnectionTestState.Idle
                            )
                        }
                    },
                    onTestConnection = {
                        val url = buildAndValidateServerUrl()
                        if (url != null) {
                            testServerConnection(url)
                        }
                    },
                    onSyncExams = {
                        val url = buildAndValidateServerUrl()
                        if (url != null) {
                            ExamApp.instance.secureStorage.serverUrl = url
                            syncManifestAndExams(url)
                        }
                    },
                    onFixNotificationPermission = {
                        fixNotificationOrExactAlarmPermission()
                    }
                )

                if (showDeviceRequirementDialog) {
                    DeviceRequirementComposeDialog(
                        hardwareStatus = uiState.hardwareStatus,
                        onOpenSettings = { action -> openSystemSettingsSafe(action) },
                        onDismiss = { showDeviceRequirementDialog = false }
                    )
                }

                pendingTokenExam?.let { exam ->
                    TokenInputComposeDialog(
                        exam = exam,
                        onDismiss = { pendingTokenExam = null },
                        onTokenVerified = { token ->
                            pendingTokenExam = null
                            startExamActivity(exam, token, isMakeup = false)
                        }
                    )
                }

                pendingMakeupExam?.let { exam ->
                    MakeupExamComposeDialog(
                        exam = exam,
                        onDismiss = { pendingMakeupExam = null },
                        onVerified = { token ->
                            pendingMakeupExam = null
                            startExamActivity(exam, token, isMakeup = true)
                        }
                    )
                }

                pendingGradeSwitch?.let { targetGrade ->
                    GradeSwitchConfirmationDialog(
                        targetGrade = targetGrade,
                        downloadedCount = uiState.downloadedExamCount,
                        onConfirm = {
                            pendingGradeSwitch = null
                            applyGradeSelection(targetGrade)
                        },
                        onDismiss = { pendingGradeSwitch = null }
                    )
                }

                BrandedSplashOverlay(
                    visible = showBrandedSplash,
                    schoolName = uiState.schoolName,
                    tagline = uiState.schoolTagline,
                    onSplashFinished = {
                        showBrandedSplash = false
                        requestNotificationPermissionIfNeeded()
                    }
                )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerHardwareReceiver()
        updateHardwareStatus()
        updateNotificationPermissionState()
        refreshExamList()
        updateExamStatistics()
        syncSchoolIdentityToUiState()
        ExamReminderScheduler.rescheduleFromStorage(this)
        ExamReminderScheduler.checkDueRemindersNow(this)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val targetTab = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        if (targetTab == TAB_EXAMS) {
            uiStateFlow.update { it.copy(currentTab = DashboardTab.EXAMS) }
            refreshExamList()
            updateExamStatistics()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ExamReminderScheduler.canPostNotifications(this)
            val storage = ExamApp.instance.secureStorage
            if (!granted && !storage.notificationPermissionPrompted) {
                storage.notificationPermissionPrompted = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateNotificationPermissionState() {
        uiStateFlow.update {
            it.copy(
                isNotificationPermissionGranted = ExamReminderScheduler.canPostNotifications(this),
                isExactAlarmAllowed = ExamReminderScheduler.canScheduleExactAlarms(this)
            )
        }
    }

    private fun fixNotificationOrExactAlarmPermission() {
        if (!ExamReminderScheduler.canPostNotifications(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val storage = ExamApp.instance.secureStorage
            if (!storage.notificationPermissionPrompted ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                storage.notificationPermissionPrompted = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings()
            }
            return
        }

        if (!ExamReminderScheduler.canScheduleExactAlarms(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                openAppDetailsSettings()
            }
            return
        }

        ExamReminderScheduler.rescheduleFromStorage(this)
        ExamReminderScheduler.checkDueRemindersNow(this)
        updateNotificationPermissionState()
        Toast.makeText(
            this,
            "Izin notifikasi dan pengingat ujian sudah aktif",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
            openSystemSettingsSafe(Settings.ACTION_SETTINGS)
        }
    }

    private fun applyGradeSelection(grade: Int) {
        ExamApp.instance.secureStorage.selectedGrade = grade
        uiStateFlow.update { it.copy(selectedGrade = grade) }
        refreshExamList()
        updateExamStatistics()
        ExamReminderScheduler.rescheduleForExams(this, allExams, grade)
    }

    private fun initializeStateFromStorage() {
        val storage = ExamApp.instance.secureStorage
        val (host, port) = parseHostAndPort(storage.serverUrl)
        uiStateFlow.update {
            it.copy(
                selectedGrade = storage.selectedGrade,
                rulesAcknowledged = storage.rulesAcknowledged,
                isDarkMode = storage.isDarkMode,
                serverHost = host,
                serverPort = port.ifBlank { "8080" },
                serverHistory = storage.getServerHistory(),
                lastSyncTimestamp = storage.lastSyncTimestamp,
                reminderMinutesBefore = storage.reminderMinutesBefore,
                isNotificationPermissionGranted = ExamReminderScheduler.canPostNotifications(this),
                isExactAlarmAllowed = ExamReminderScheduler.canScheduleExactAlarms(this)
            )
        }
    }

    private fun startTimeTicker() {
        timeTickerJob?.cancel()
        timeTickerJob = lifecycleScope.launch {
            while (isActive) {
                ExamReminderScheduler.checkDueRemindersNow(this@DashboardActivity)
                refreshExamList()
                delay(1000L)
            }
        }
    }

    private fun stopTimeTicker() {
        timeTickerJob?.cancel()
        timeTickerJob = null
    }

    private fun openSystemSettingsSafe(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun syncSchoolIdentityToUiState() {
        val storage = ExamApp.instance.secureStorage
        uiStateFlow.update { state ->
            state.copy(
                schoolName = storage.schoolName,
                schoolTagline = storage.schoolTagline,
                schoolNpsn = storage.schoolNpsn,
                schoolNsm = storage.schoolNsm,
                schoolAddress = storage.schoolAddress,
                examTitle = currentManifest?.examTitle?.takeIf { it.isNotBlank() } ?: state.examTitle,
                examRules = currentManifest?.examRules ?: emptyList()
            )
        }
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

    private fun readCurrentHardwareStatus(): HardwareStatusState {
        val isAirplaneOn = Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiOff = wifiManager?.isWifiEnabled != true

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isRingerSilentOrVibrate = audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL

        val osRelease = Build.VERSION.RELEASE ?: "Unknown"
        val isAndroidVersionOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalBytes = memInfo.totalMem
        val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val formattedRam = String.format(Locale.US, "%.1f", totalGb).removeSuffix(".0")
        val isRamOk = totalBytes >= (1.75 * 1024 * 1024 * 1024)

        return HardwareStatusState(
            isAirplaneOn = isAirplaneOn,
            isWifiOff = isWifiOff,
            isBluetoothOff = isBluetoothOff,
            isRingerSilentOrVibrate = isRingerSilentOrVibrate,
            osRelease = osRelease,
            isAndroidVersionOk = isAndroidVersionOk,
            formattedRamGb = formattedRam,
            isRamOk = isRamOk
        )
    }

    private fun updateHardwareStatus() {
        val status = readCurrentHardwareStatus()
        uiStateFlow.update { it.copy(hardwareStatus = status) }
    }

    private fun updateExamStatistics() {
        val secureStorage = ExamApp.instance.secureStorage
        val targetGrade = secureStorage.selectedGrade
        val gradeExams = allExams.filter { it.gradeLevel == targetGrade }
        val total = gradeExams.size
        val finished = gradeExams.count { secureStorage.isExamFinished(it.id) }
        val remaining = (total - finished).coerceAtLeast(0)

        uiStateFlow.update {
            it.copy(
                totalExams = total,
                finishedExams = finished,
                remainingExams = remaining
            )
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
            Pair("", "8080")
        }
    }

    private fun buildAndValidateServerUrl(): String? {
        val state = uiStateFlow.value
        val host = state.serverHost.trim()
        val port = state.serverPort.trim().ifEmpty { "8080" }

        if (host.isEmpty()) {
            uiStateFlow.update {
                it.copy(connectionTestState = ConnectionTestState.Failed("IP server tidak boleh kosong"))
            }
            return null
        }

        return LocalSyncManager.normalizeUrl("$host:$port")
    }

    private fun testServerConnection(url: String) {
        uiStateFlow.update {
            it.copy(
                connectionTestState = ConnectionTestState.Testing(url),
                syncState = SyncState.Idle
            )
        }

        lifecycleScope.launch {
            val result = syncManager.testConnection(url)
            result.onSuccess { pingInfo ->
                val storage = ExamApp.instance.secureStorage
                storage.serverUrl = url
                val (h, p) = parseHostAndPort(url)
                if (h.isNotBlank()) {
                    storage.addServerToHistory("$h:${p.ifBlank { "8080" }}")
                }
                val schoolLabel = pingInfo.schoolName.ifBlank { "Server Proktor" }
                uiStateFlow.update {
                    it.copy(
                        serverHistory = storage.getServerHistory(),
                        connectionTestState = ConnectionTestState.Success(
                            "Terhubung ke $schoolLabel • ${pingInfo.examCount} paket soal ($url)"
                        )
                    )
                }
            }.onFailure { err ->
                uiStateFlow.update {
                    it.copy(
                        connectionTestState = ConnectionTestState.Failed(
                            "Gagal terhubung: ${err.localizedMessage ?: "Periksa IP, Port & Wi-Fi LAN"}"
                        )
                    )
                }
            }
        }
    }

    private fun syncManifestAndExams(url: String) {
        uiStateFlow.update {
            it.copy(syncState = SyncState.Syncing("Menghubungi server proktor..."))
        }

        lifecycleScope.launch {
            val initialGrade = ExamApp.instance.secureStorage.selectedGrade
            val manifestResult = syncManager.fetchManifest(url, initialGrade)

            manifestResult.onSuccess { manifest ->
                val secureStorage = ExamApp.instance.secureStorage
                if (manifest.masterPinSha256.isNotBlank()) {
                    secureStorage.masterPinHash = manifest.masterPinSha256
                }
                secureStorage.reminderMinutesBefore = manifest.reminderMinutesBefore
                secureStorage.setDailyPinHashes(manifest.dailyPinHashes)
                if (manifest.syncTimestamp > 0L) {
                    val serverMillis = manifest.syncTimestamp * 1000L
                    secureStorage.advanceMaxKnownTimestamp(serverMillis)
                    secureStorage.lastSyncServerMillis = serverMillis
                    secureStorage.lastSyncElapsedRealtime = SystemClock.elapsedRealtime()
                }
                if (manifest.schoolName.isNotBlank()) secureStorage.schoolName = manifest.schoolName
                if (manifest.schoolTagline.isNotBlank()) secureStorage.schoolTagline = manifest.schoolTagline
                if (manifest.schoolNpsn.isNotBlank()) secureStorage.schoolNpsn = manifest.schoolNpsn
                if (manifest.schoolNsm.isNotBlank()) secureStorage.schoolNsm = manifest.schoolNsm
                if (manifest.schoolAddress.isNotBlank()) secureStorage.schoolAddress = manifest.schoolAddress

                secureStorage.cachedManifest = json.encodeToString(ExamManifest.serializer(), manifest)
                currentManifest = manifest
                allExams = manifest.exams

                val targetGrade = secureStorage.selectedGrade
                val targetExams = manifest.exams.filter { it.gradeLevel == targetGrade }
                val activeExamIds = targetExams.map { it.id }.toSet()
                syncManager.cleanupOrphanedExams(activeExamIds)

                val toDownload = targetExams.filter { !syncManager.isExamFileValid(it.id, it.fileSha256) }
                val totalToDownload = toDownload.size
                val failedDownloads = mutableListOf<String>()

                if (totalToDownload > 0) {
                    var processedCount = 0
                    uiStateFlow.update {
                        it.copy(
                            syncState = SyncState.Syncing(
                                message = "Mengunduh paket soal Kelas $targetGrade (0/$totalToDownload)...",
                                current = 0,
                                total = totalToDownload
                            )
                        )
                    }

                    for (exam in toDownload) {
                        val fullUrl = resolveExamDownloadUrl(url, exam)
                        val downloadResult = syncManager.downloadExamFile(
                            downloadUrl = fullUrl,
                            examId = exam.id,
                            expectedSha256 = exam.fileSha256
                        )
                        if (downloadResult.isFailure) {
                            failedDownloads.add(exam.subjectName)
                        }
                        processedCount++
                        uiStateFlow.update {
                            it.copy(
                                syncState = SyncState.Syncing(
                                    message = "Mengunduh paket soal Kelas $targetGrade ($processedCount/$totalToDownload)...",
                                    current = processedCount,
                                    total = totalToDownload
                                )
                            )
                        }
                    }
                }

                val syncNow = System.currentTimeMillis()
                secureStorage.lastSyncTimestamp = syncNow
                val (h, p) = parseHostAndPort(url)
                if (h.isNotBlank()) {
                    secureStorage.addServerToHistory("$h:${p.ifBlank { "8080" }}")
                }

                val readyOfflineCount = targetExams.count { syncManager.isExamFileValid(it.id, it.fileSha256) }
                val finalSyncState = if (failedDownloads.isEmpty()) {
                    SyncState.Success(
                        "Sinkronisasi selesai: $readyOfflineCount/${targetExams.size} paket soal Kelas $targetGrade siap"
                    )
                } else {
                    SyncState.Error(
                        "Sebagian gagal diunduh (${failedDownloads.joinToString(", ")}). Tersedia $readyOfflineCount/${targetExams.size} paket Kelas $targetGrade."
                    )
                }

                uiStateFlow.update {
                    it.copy(
                        lastSyncTimestamp = syncNow,
                        serverHistory = secureStorage.getServerHistory(),
                        reminderMinutesBefore = manifest.reminderMinutesBefore,
                        syncState = finalSyncState
                    )
                }

                syncSchoolIdentityToUiState()
                refreshExamList()
                updateExamStatistics()
                ExamReminderScheduler.rescheduleForExams(
                    context = this@DashboardActivity,
                    allExams = manifest.exams,
                    selectedGrade = targetGrade,
                    leadMinutes = manifest.reminderMinutesBefore
                )
                Toast.makeText(
                    this@DashboardActivity,
                    if (failedDownloads.isEmpty()) {
                        "Paket soal Kelas $targetGrade berhasil disinkronkan!"
                    } else {
                        "Sinkronisasi selesai dengan ${failedDownloads.size} gagal unduh"
                    },
                    if (failedDownloads.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
            }.onFailure { err ->
                uiStateFlow.update {
                    it.copy(
                        syncState = SyncState.Error(
                            "Sinkronisasi gagal: ${err.localizedMessage ?: "Tidak dapat menjangkau server"}"
                        )
                    )
                }
                Toast.makeText(
                    this@DashboardActivity,
                    "Gagal mengunduh manifest: ${err.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadCachedManifest() {
        val secureStorage = ExamApp.instance.secureStorage
        val cached = secureStorage.cachedManifest
        if (!cached.isNullOrEmpty()) {
            try {
                val manifest = json.decodeFromString<ExamManifest>(cached)
                secureStorage.reminderMinutesBefore = manifest.reminderMinutesBefore
                secureStorage.setDailyPinHashes(manifest.dailyPinHashes)
                if (manifest.syncTimestamp > 0L) {
                    secureStorage.advanceMaxKnownTimestamp(manifest.syncTimestamp * 1000L)
                }
                currentManifest = manifest
                allExams = manifest.exams
                uiStateFlow.update { it.copy(reminderMinutesBefore = manifest.reminderMinutesBefore) }
                syncSchoolIdentityToUiState()
                refreshExamList()
                updateExamStatistics()
                ExamReminderScheduler.rescheduleForExams(
                    context = this,
                    allExams = manifest.exams,
                    selectedGrade = secureStorage.selectedGrade,
                    leadMinutes = manifest.reminderMinutesBefore
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshExamList() {
        val secureStorage = ExamApp.instance.secureStorage
        val targetGrade = secureStorage.selectedGrade
        val filtered = allExams.filter { it.gradeLevel == targetGrade }

        val sortedExams = filtered.sortedWith(
            compareBy<ExamItem> { it.examDate.ifEmpty { "9999-99-99" } }
                .thenBy { it.startTime.ifEmpty { "99:99" } }
        )

        val trustedNow = ExamScheduleHelper.getTrustedNowMillis(secureStorage)
        val uiModels = sortedExams.map { exam ->
            val isDownloaded = syncManager.isExamFileValid(exam.id, exam.fileSha256)
            val isFinished = secureStorage.isExamFinished(exam.id)
            val isActive = secureStorage.activeExamId == exam.id
            val scheduleState = ExamScheduleHelper.getScheduleState(exam, trustedNow)

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

        val downloadedCount = uiModels.count { it.isFilePresent }
        uiStateFlow.update {
            it.copy(
                examItems = uiModels,
                downloadedExamCount = downloadedCount
            )
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

        val currentHw = readCurrentHardwareStatus()
        uiStateFlow.update { it.copy(hardwareStatus = currentHw) }
        if (!currentHw.isAllCompliant) {
            showDeviceRequirementDialog = true
            return
        }

        if (ExamScheduleHelper.isClockTampered(secureStorage)) {
            Toast.makeText(
                this,
                "Jam/tanggal perangkat terdeteksi tidak sesuai atau dimundurkan! Aktifkan Tanggal & Waktu Otomatis lalu sinkronisasi ulang.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val trustedNow = ExamScheduleHelper.getTrustedNowMillis(secureStorage)
        when (ExamScheduleHelper.getScheduleState(exam, trustedNow)) {
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
            }
            ExamScheduleHelper.ScheduleState.EXPIRED -> {
                pendingMakeupExam = exam
            }
            ExamScheduleHelper.ScheduleState.ACTIVE -> {
                pendingTokenExam = exam
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
        uiStateFlow.update { it.copy(downloadingExamId = exam.id) }
        Toast.makeText(this, "Mengunduh berkas ${exam.subjectName}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = syncManager.downloadExamFile(
                downloadUrl = fullUrl,
                examId = exam.id,
                expectedSha256 = exam.fileSha256
            )
            uiStateFlow.update { it.copy(downloadingExamId = null) }
            result.onSuccess {
                Toast.makeText(
                    this@DashboardActivity,
                    "Berkas ${exam.subjectName} berhasil diunduh!",
                    Toast.LENGTH_SHORT
                ).show()
                refreshExamList()
            }.onFailure { err ->
                Toast.makeText(
                    this@DashboardActivity,
                    "Gagal mengunduh: ${err.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startExamActivity(exam: ExamItem, token: String, isMakeup: Boolean = false) {
        val effectiveDuration = if (isMakeup) {
            ExamScheduleHelper.getTotalDurationMinutes(exam)
        } else {
            ExamScheduleHelper.calculateEffectiveDurationMinutes(exam)
        }

        val intent = Intent(this, KioskExamActivity::class.java).apply {
            putExtra(KioskExamActivity.EXTRA_EXAM_ID, exam.id)
            putExtra(KioskExamActivity.EXTRA_SUBJECT_NAME, exam.subjectName)
            putExtra(KioskExamActivity.EXTRA_GRADE_LEVEL, exam.gradeLevel)
            putExtra(KioskExamActivity.EXTRA_DURATION_MINUTES, effectiveDuration)
            putExtra(KioskExamActivity.EXTRA_END_TIME, exam.endTime)
            putExtra(KioskExamActivity.EXTRA_SALT_HEX, exam.pbkdf2SaltHex)
            putExtra(KioskExamActivity.EXTRA_IV_HEX, exam.gcmIvHex)
            putExtra(KioskExamActivity.EXTRA_PBKDF2_ITERATIONS, exam.pbkdf2Iterations)
            putExtra(KioskExamActivity.EXTRA_TOKEN, token)
            putExtra(KioskExamActivity.EXTRA_IS_MAKEUP_EXAM, isMakeup)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_EXAMS = "EXAMS"
    }
}
