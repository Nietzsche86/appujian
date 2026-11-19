package id.mtsrth.exam.security

import android.os.SystemClock
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.local.SecureStorage
import java.security.MessageDigest
import java.util.Calendar
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SupervisorPinHelper {

    private const val CLOCK_DRIFT_TOLERANCE_MILLIS = 5 * 60_000L
    private const val PBKDF2_PREFIX = "pbkdf2_sha256$"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val MIN_PBKDF2_ITERATIONS = 10_000
    private const val MAX_PBKDF2_ITERATIONS = 1_000_000
    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 3

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

    /**
     * Memverifikasi PIN terhadap hash yang tersimpan.
     * Mendukung format kuat bersalt "pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>"
     * serta kompatibilitas mundur sementara untuk hash SHA-256 64-hex lama sebelum sinkronisasi ulang.
     */
    fun matchesStoredPinHash(pin: String, storedHash: String): Boolean {
        val cleanStored = storedHash.trim()
        if (cleanStored.isEmpty()) return false

        if (cleanStored.startsWith(PBKDF2_PREFIX, ignoreCase = true)) {
            val parts = cleanStored.split("$")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) return false
            val salt = hexToBytesOrNull(parts[2]) ?: return false
            val expectedKey = hexToBytesOrNull(parts[3]) ?: return false
            if (salt.isEmpty() || expectedKey.isEmpty()) return false

            val chars = pin.toCharArray()
            val spec = PBEKeySpec(chars, salt, iterations, expectedKey.size * 8)
            return try {
                val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                val derived = factory.generateSecret(spec).encoded
                MessageDigest.isEqual(derived, expectedKey)
            } catch (_: Exception) {
                false
            } finally {
                chars.fill('\u0000')
                spec.clearPassword()
            }
        }

        // Legacy fallback hanya untuk hash SHA-256 64-hex yang sudah tersinkronisasi dari server sebelumnya
        if (cleanStored.length == 64) {
            val expectedBytes = hexToBytesOrNull(cleanStored) ?: return false
            val actualBytes = MessageDigest.getInstance("SHA-256")
                .digest(pin.toByteArray(Charsets.UTF_8))
            return MessageDigest.isEqual(actualBytes, expectedBytes)
        }

        return false
    }

    private fun hexToBytesOrNull(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val hi = Character.digit(clean[i], 16)
            val lo = Character.digit(clean[i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return out
    }

    private fun currentMonotonicClockOrWall(nowMillis: Long): Long {
        return try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            nowMillis
        }
    }

    private fun recordFailedAttempt(storage: SecureStorage, nowRefMillis: Long): String {
        val attempts = storage.pinFailedAttempts + 1
        storage.pinFailedAttempts = attempts
        if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            val backoffSeconds = when (attempts) {
                3 -> 15L
                4 -> 30L
                5 -> 60L
                else -> 120L
            }
            storage.pinLockoutUntilMillis = nowRefMillis + backoffSeconds * 1000L
            return "Terlalu banyak percobaan PIN salah ($attempts kali). Coba lagi dalam $backoffSeconds detik."
        }
        return ""
    }

    private fun resetFailedAttempts(storage: SecureStorage) {
        if (storage.pinFailedAttempts > 0 || storage.pinLockoutUntilMillis > 0L) {
            storage.pinFailedAttempts = 0
            storage.pinLockoutUntilMillis = 0L
        }
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

        // Cek rate-limit / cooldown percobaan PIN salah
        val monotonicNow = currentMonotonicClockOrWall(nowMillis)
        val lockoutUntil = storage.pinLockoutUntilMillis
        if (lockoutUntil > 0L) {
            // Reset jika perangkat baru saja reboot (elapsedRealtime lebih kecil jauh dari lockout)
            if (lockoutUntil - monotonicNow > 10 * 60_000L) {
                storage.pinLockoutUntilMillis = 0L
            } else if (monotonicNow < lockoutUntil) {
                val waitSec = ((lockoutUntil - monotonicNow + 999L) / 1000L).coerceAtLeast(1L)
                return PinVerifyResult.Failure(
                    "Input PIN dikunci sementara karena salah berulang. Tunggu $waitSec detik."
                )
            }
        }

        // Lapis 1: PIN Darurat Permanen (Super Admin / Proktor)
        // Tidak ada PIN default hardcoded! Wajib tersinkronisasi dari Server Proktor resmi.
        val masterHash = storage.masterPinHash
        if (masterHash.isBlank()) {
            return PinVerifyResult.Failure(
                "PIN Pengawas belum tersinkronisasi dari server proktor. Lakukan sinkronisasi terlebih dahulu."
            )
        }

        if (matchesStoredPinHash(pin, masterHash)) {
            resetFailedAttempts(storage)
            storage.advanceMaxKnownTimestamp(nowMillis)
            return PinVerifyResult.Success
        }

        // Lapis 2: PIN Pengawas Harian (Senin - Sabtu)
        // Dilindungi pengecekan anti-rollback waktu agar tanggal HP tidak bisa dimundurkan demi memakai PIN kemarin.
        val maxKnown = storage.maxKnownTimestampMillis
        if (maxKnown > 0L && nowMillis + CLOCK_DRIFT_TOLERANCE_MILLIS < maxKnown) {
            val lockoutMsg = recordFailedAttempt(storage, monotonicNow)
            return PinVerifyResult.Failure(
                lockoutMsg.ifEmpty {
                    "Waktu/tanggal perangkat terdeteksi dimundurkan! Perbaiki jam atau gunakan PIN Darurat Permanen."
                }
            )
        }
        storage.advanceMaxKnownTimestamp(nowMillis)

        val todayKey = getTodayDayKey(nowMillis)
        val dailyHashes = storage.getDailyPinHashes()
        val expectedDailyHash = if (todayKey != null) dailyHashes[todayKey].orEmpty() else ""

        if (expectedDailyHash.isNotBlank()) {
            return if (matchesStoredPinHash(pin, expectedDailyHash)) {
                resetFailedAttempts(storage)
                PinVerifyResult.Success
            } else {
                val lockoutMsg = recordFailedAttempt(storage, monotonicNow)
                val dayLabel = getTodayDayLabelId(nowMillis)
                PinVerifyResult.Failure(
                    lockoutMsg.ifEmpty { "PIN Pengawas hari $dayLabel atau PIN Darurat tidak valid!" }
                )
            }
        }

        val lockoutMsg = recordFailedAttempt(storage, monotonicNow)
        return PinVerifyResult.Failure(
            lockoutMsg.ifEmpty { "PIN Darurat Permanen tidak valid!" }
        )
    }
}
