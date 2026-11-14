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

        // Anti-clock-rollback check untuk mencegah bypass rotasi PIN harian dengan memundurkan tanggal HP
        val maxKnown = storage.maxKnownTimestampMillis
        if (maxKnown > 0L && nowMillis + CLOCK_DRIFT_TOLERANCE_MILLIS < maxKnown) {
            return PinVerifyResult.Failure(
                "Waktu/tanggal perangkat terdeteksi dimundurkan! Perbaiki jam perangkat terlebih dahulu."
            )
        }
        storage.advanceMaxKnownTimestamp(nowMillis)

        val actualHash = LocalSyncManager.calculateSha256(pin)
        val todayKey = getTodayDayKey(nowMillis)
        val dailyHashes = storage.getDailyPinHashes()
        val expectedDailyHash = if (todayKey != null) dailyHashes[todayKey].orEmpty() else ""

        if (expectedDailyHash.isNotBlank()) {
            return if (actualHash.equals(expectedDailyHash, ignoreCase = true)) {
                PinVerifyResult.Success
            } else {
                val dayLabel = getTodayDayLabelId(nowMillis)
                PinVerifyResult.Failure("PIN Pengawas hari $dayLabel tidak valid!")
            }
        }

        // Fallback ke Master PIN jika PIN harian untuk hari ini kosong atau hari Minggu
        val masterHash = storage.masterPinHash
        val isMasterValid = if (masterHash.isNotBlank()) {
            actualHash.equals(masterHash, ignoreCase = true)
        } else {
            pin == "888999"
        }

        return if (isMasterValid) {
            PinVerifyResult.Success
        } else {
            PinVerifyResult.Failure("Master PIN pengawas tidak valid!")
        }
    }
}
