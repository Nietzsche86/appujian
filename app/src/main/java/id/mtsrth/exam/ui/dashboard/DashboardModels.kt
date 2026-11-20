package id.mtsrth.exam.ui.dashboard

import id.mtsrth.exam.data.model.ExamItemUiModel

enum class DashboardTab(
    val title: String,
    val subtitle: String
) {
    DASHBOARD("Perangkat", "Kesiapan & Spesifikasi Perangkat"),
    RULES("Panduan", "Panduan & Tata Tertib Ujian"),
    EXAMS("Soal", "Daftar Paket Soal Ujian"),
    SETTINGS("Pengaturan", "Pengaturan Server & Sinkronisasi")
}

data class HardwareStatusState(
    val isAirplaneOn: Boolean = false,
    val isWifiOff: Boolean = true,
    val isBluetoothOff: Boolean = true,
    val isRingerSilentOrVibrate: Boolean = false,
    val osRelease: String = "",
    val isAndroidVersionOk: Boolean = true,
    val formattedRamGb: String = "0",
    val isRamOk: Boolean = true
) {
    val isAllCompliant: Boolean
        get() = isAirplaneOn && isWifiOff && isBluetoothOff && isRingerSilentOrVibrate && isAndroidVersionOk && isRamOk
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data class Testing(val url: String) : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Failed(val message: String) : ConnectionTestState
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(
        val message: String,
        val current: Int = 0,
        val total: Int = 0
    ) : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val message: String) : SyncState
}

enum class ExamFilterOption(val label: String) {
    ACTIVE("Aktif"),
    MAKEUP("Susulan"),
    FINISHED("Selesai"),
    ALL("Semua")
}

data class DashboardUiState(
    val currentTab: DashboardTab = DashboardTab.DASHBOARD,
    val schoolName: String = "MTS ROUDHOTUT THOWALIB",
    val schoolTagline: String = "Madrasah Tsanawiyah Unggulan & Berkarakter",
    val schoolNpsn: String = "20278365",
    val schoolNsm: String = "121232050172",
    val schoolAddress: String = "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat.",
    val examTitle: String = "Penilaian Akhir Semester (PAS) Ganjil",
    val hardwareStatus: HardwareStatusState = HardwareStatusState(),
    val selectedGrade: Int = 7,
    val totalExams: Int = 0,
    val finishedExams: Int = 0,
    val remainingExams: Int = 0,
    val examItems: List<ExamItemUiModel> = emptyList(),
    val examFilter: ExamFilterOption = ExamFilterOption.ACTIVE,
    val examRules: List<String> = emptyList(),
    val rulesAcknowledged: Boolean = false,
    val serverHost: String = "",
    val serverPort: String = "8080",
    val serverHistory: List<String> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val downloadedExamCount: Int = 0,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
    val syncState: SyncState = SyncState.Idle,
    val downloadingExamId: String? = null,
    val isDarkMode: Boolean = false,
    val reminderMinutesBefore: Int = 5,
    val isNotificationPermissionGranted: Boolean = true,
    val isExactAlarmAllowed: Boolean = true
)
