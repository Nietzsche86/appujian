package id.mtsrth.exam.security

import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.local.SecureStorage
import id.mtsrth.exam.data.sync.LocalSyncManager
import java.util.Calendar

object SupervisorPinHelper {

    private const val CLOCK_DRIFT_TOLERANCE_MILLIS = 5 * 60_000L

    sealed interface PinVerifyResult {
        data object Success : PinVerifyResult
        data class Failure(val reason: String) : PinVerifyResult
    }

    fun getTodayDayKey(nowMillis: Long = System.currentTimeMillis()): String? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            Calendar.SATURDAY -> "saturday"
            else -> null
        }
    }

    fun getTodayDayLabelId(nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Senin"
            Calendar.TUESDAY -> "Selasa"
            Calendar.WEDNESDAY -> "Rabu"
            Calendar.THURSDAY -> "Kamis"
            Calendar.FRIDAY -> "Jumat"
            Calendar.SATURDAY -> "Sabtu"
            else -> "Minggu"
        }
    }

    fun hasDailyPinForToday(
        storage: SecureStorage = ExamApp.instance.secureStorage,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val dayKey = getTodayDayKey(nowMillis) ?: return false
        return !storage.getDailyPinHashes()[dayKey].isNullOrBlank()
    }

    fun verifyPin(
        inputPin: String,
        storage: SecureStorage = ExamApp.instance.secureStorage,
        nowMillis: Long = System.currentTimeMillis()
    ): PinVerifyResult {
        val pin = inputPin.trim()
        if (pin.isEmpty()) {
            return PinVerifyResult.Failure("PIN pengawas tidak boleh kosong")
        }

        val actualHash = LocalSyncManager.calculateSha256(pin)

        // Lapis 1: PIN Darurat Permanen (Super Admin / Proktor)
        // Selalu aktif di semua hari (Senin-Minggu) & tetap tembus meski jam perangkat bermasalah.
        val masterHash = storage.masterPinHash
        val isEmergencyMaster = if (masterHash.isNotBlank()) {
            actualHash.equals(masterHash, ignoreCase = true)
        } else {
            pin == "888999"
        }
        if (isEmergencyMaster) {
            storage.advanceMaxKnownTimestamp(nowMillis)
            return PinVerifyResult.Success
        }

        // Lapis 2: PIN Pengawas Harian (Senin - Sabtu)
        // Dilindungi pengecekan anti-rollback waktu agar tanggal HP tidak bisa dimundurkan demi memakai PIN kemarin.
        val maxKnown = storage.maxKnownTimestampMillis
        if (maxKnown > 0L && nowMillis + CLOCK_DRIFT_TOLERANCE_MILLIS < maxKnown) {
            return PinVerifyResult.Failure(
                "Waktu/tanggal perangkat terdeteksi dimundurkan! Perbaiki jam atau gunakan PIN Darurat Permanen."
            )
        }
        storage.advanceMaxKnownTimestamp(nowMillis)

        val todayKey = getTodayDayKey(nowMillis)
        val dailyHashes = storage.getDailyPinHashes()
        val expectedDailyHash = if (todayKey != null) dailyHashes[todayKey].orEmpty() else ""

        if (expectedDailyHash.isNotBlank()) {
            return if (actualHash.equals(expectedDailyHash, ignoreCase = true)) {
                PinVerifyResult.Success
            } else {
                val dayLabel = getTodayDayLabelId(nowMillis)
                PinVerifyResult.Failure("PIN Pengawas hari $dayLabel atau PIN Darurat tidak valid!")
            }
        }

        return PinVerifyResult.Failure("PIN Darurat Permanen tidak valid!")
    }
}
