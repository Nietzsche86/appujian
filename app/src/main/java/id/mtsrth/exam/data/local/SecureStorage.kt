package id.mtsrth.exam.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefsSafely(context.applicationContext)

    private fun createEncryptedPrefsSafely(appContext: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(appContext)
        } catch (_: GeneralSecurityException) {
            recoverCorruptedPrefs(appContext)
            buildEncryptedPrefs(appContext)
        } catch (_: IOException) {
            recoverCorruptedPrefs(appContext)
            buildEncryptedPrefs(appContext)
        }
    }

    private fun buildEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun recoverCorruptedPrefs(appContext: Context) {
        runCatching {
            val prefsFile = File(appContext.filesDir.parentFile, "shared_prefs/$PREFS_FILENAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
            appContext.deleteSharedPreferences(PREFS_FILENAME)
        }
    }

    var schoolName: String
        get() = prefs.getString(KEY_SCHOOL_NAME, "MTS ROUDHOTUT THOWALIB") ?: "MTS ROUDHOTUT THOWALIB"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NAME, value).apply()

    var schoolTagline: String
        get() = prefs.getString(KEY_SCHOOL_TAGLINE, "Madrasah Tsanawiyah Unggulan & Berkarakter") ?: "Madrasah Tsanawiyah Unggulan & Berkarakter"
        set(value) = prefs.edit().putString(KEY_SCHOOL_TAGLINE, value).apply()

    var schoolNpsn: String
        get() = prefs.getString(KEY_SCHOOL_NPSN, "20278365") ?: "20278365"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NPSN, value).apply()

    var schoolNsm: String
        get() = prefs.getString(KEY_SCHOOL_NSM, "121232050172") ?: "121232050172"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NSM, value).apply()

    var schoolAddress: String
        get() = prefs.getString(KEY_SCHOOL_ADDRESS, "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat.") ?: "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat."
        set(value) = prefs.edit().putString(KEY_SCHOOL_ADDRESS, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var trustedServerPublicKey: String
        get() = prefs.getString(KEY_TRUSTED_SERVER_PUBLIC_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRUSTED_SERVER_PUBLIC_KEY, value.trim()).apply()

    var masterPinHash: String
        get() = prefs.getString(KEY_MASTER_PIN_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MASTER_PIN_HASH, value).apply()

    var reminderMinutesBefore: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTES_BEFORE, 5).coerceIn(0, 120)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTES_BEFORE, value.coerceIn(0, 120)).apply()

    var maxKnownTimestampMillis: Long
        get() = prefs.getLong(KEY_MAX_KNOWN_TIMESTAMP_MILLIS, 0L)
        private set(value) = prefs.edit().putLong(KEY_MAX_KNOWN_TIMESTAMP_MILLIS, value).apply()

    var lastSyncServerMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC_SERVER_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_SERVER_MILLIS, value).apply()

    var lastSyncElapsedRealtime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_ELAPSED_REALTIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_ELAPSED_REALTIME, value).apply()

    var pinFailedAttempts: Int
        get() = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, value.coerceAtLeast(0)).apply()

    var pinLockoutUntilMillis: Long
        get() = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL_MILLIS, value.coerceAtLeast(0L)).apply()

    fun advanceMaxKnownTimestamp(candidateMillis: Long) {
        if (candidateMillis > maxKnownTimestampMillis) {
            maxKnownTimestampMillis = candidateMillis
        }
    }

    fun getDailyPinHashes(): Map<String, String> {
        val raw = prefs.getString(KEY_DAILY_PIN_HASHES, null) ?: return emptyMap()
        return runCatching {
            val jsonObj = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObj.optString(key, "").trim()
                if (value.isNotEmpty()) {
                    result[key] = value
                }
            }
            result.toMap()
        }.getOrDefault(emptyMap())
    }

    fun setDailyPinHashes(hashes: Map<String, String>) {
        val jsonObj = JSONObject()
        for ((day, hash) in hashes) {
            val clean = hash.trim()
            if (clean.isNotEmpty()) {
                jsonObj.put(day, clean)
            }
        }
        prefs.edit().putString(KEY_DAILY_PIN_HASHES, jsonObj.toString()).apply()
    }

    var activeExamId: String?
        get() = prefs.getString(KEY_ACTIVE_EXAM_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_EXAM_ID, value).apply()

    var activeExamIsMakeup: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE_EXAM_IS_MAKEUP, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVE_EXAM_IS_MAKEUP, value).apply()

    var remainingExamSeconds: Long
        get() = prefs.getLong(KEY_REMAINING_EXAM_SECONDS, 0L)
        set(value) = prefs.edit().putLong(KEY_REMAINING_EXAM_SECONDS, value).apply()

    var violationCount: Int
        get() = prefs.getInt(KEY_VIOLATION_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_VIOLATION_COUNT, value).apply()

    var remainingBanSeconds: Long
        get() = prefs.getLong(KEY_REMAINING_BAN_SECONDS, 0L)
        set(value) = prefs.edit().putLong(KEY_REMAINING_BAN_SECONDS, value).apply()

    var lastBootCount: Int
        get() = prefs.getInt(KEY_LAST_BOOT_COUNT, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_BOOT_COUNT, value).apply()

    var selectedGrade: Int
        get() = prefs.getInt(KEY_SELECTED_GRADE, 7)
        set(value) = prefs.edit().putInt(KEY_SELECTED_GRADE, value).apply()

    var cachedManifest: String?
        get() = prefs.getString(KEY_CACHED_MANIFEST, null)
        set(value) = prefs.edit().putString(KEY_CACHED_MANIFEST, value).apply()

    var rulesAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_RULES_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_RULES_ACKNOWLEDGED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    var kioskComfortMode: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_COMFORT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_COMFORT_MODE, value).apply()

    var notificationPermissionPrompted: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPTED, value).apply()

    fun getScheduledReminderIds(): Set<String> {
        return prefs.getStringSet(KEY_SCHEDULED_REMINDERS, emptySet())?.toSet() ?: emptySet()
    }

    fun setScheduledReminderIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SCHEDULED_REMINDERS, ids).apply()
    }

    fun isReminderNotified(key: String): Boolean {
        val current = prefs.getStringSet(KEY_NOTIFIED_REMINDERS, emptySet()) ?: emptySet()
        return current.contains(key)
    }

    fun markReminderNotified(key: String) {
        val current = prefs.getStringSet(KEY_NOTIFIED_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(key)) {
            prefs.edit().putStringSet(KEY_NOTIFIED_REMINDERS, current).apply()
        }
    }

    fun pruneNotifiedReminderKeys(validKeys: Set<String>) {
        val current = prefs.getStringSet(KEY_NOTIFIED_REMINDERS, emptySet()) ?: return
        val retained = current.filter { validKeys.contains(it) }.toSet()
        if (retained.size != current.size) {
            prefs.edit().putStringSet(KEY_NOTIFIED_REMINDERS, retained).apply()
        }
    }

    fun getServerHistory(): List<String> {
        val raw = prefs.getString(KEY_SERVER_HISTORY, "") ?: ""
        return raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addServerToHistory(hostPort: String) {
        val clean = hostPort.trim()
        if (clean.isEmpty()) return
        val updated = (listOf(clean) + getServerHistory().filterNot { it.equals(clean, ignoreCase = true) }).take(3)
        prefs.edit().putString(KEY_SERVER_HISTORY, updated.joinToString("|")).apply()
    }

    fun getFinishedExams(): Set<String> {
        return prefs.getStringSet(KEY_FINISHED_EXAMS, emptySet()) ?: emptySet()
    }

    fun markExamFinished(examId: String) {
        val current = getFinishedExams().toMutableSet()
        current.add(examId)
        prefs.edit().putStringSet(KEY_FINISHED_EXAMS, current).apply()
    }

    fun isExamFinished(examId: String): Boolean {
        return getFinishedExams().contains(examId)
    }

    fun resetActiveExamSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_EXAM_ID)
            .remove(KEY_ACTIVE_EXAM_IS_MAKEUP)
            .remove(KEY_REMAINING_EXAM_SECONDS)
            .remove(KEY_VIOLATION_COUNT)
            .remove(KEY_REMAINING_BAN_SECONDS)
            .apply()
    }

    companion object {
        private const val PREFS_FILENAME = "secure_exam_prefs"
        private const val DEFAULT_SERVER_URL = ""

        private const val KEY_SCHOOL_NAME = "school_name"
        private const val KEY_SCHOOL_TAGLINE = "school_tagline"
        private const val KEY_SCHOOL_NPSN = "school_npsn"
        private const val KEY_SCHOOL_NSM = "school_nsm"
        private const val KEY_SCHOOL_ADDRESS = "school_address"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TRUSTED_SERVER_PUBLIC_KEY = "trusted_server_public_key"
        private const val KEY_MASTER_PIN_HASH = "master_pin_hash"
        private const val KEY_REMINDER_MINUTES_BEFORE = "reminder_minutes_before"
        private const val KEY_DAILY_PIN_HASHES = "daily_pin_hashes"
        private const val KEY_MAX_KNOWN_TIMESTAMP_MILLIS = "max_known_timestamp_millis"
        private const val KEY_LAST_SYNC_SERVER_MILLIS = "last_sync_server_millis"
        private const val KEY_LAST_SYNC_ELAPSED_REALTIME = "last_sync_elapsed_realtime"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL_MILLIS = "pin_lockout_until_millis"
        private const val KEY_ACTIVE_EXAM_ID = "active_exam_id"
        private const val KEY_ACTIVE_EXAM_IS_MAKEUP = "active_exam_is_makeup"
        private const val KEY_REMAINING_EXAM_SECONDS = "remaining_exam_seconds"
        private const val KEY_VIOLATION_COUNT = "violation_count"
        private const val KEY_REMAINING_BAN_SECONDS = "remaining_ban_seconds"
        private const val KEY_LAST_BOOT_COUNT = "last_boot_count"
        private const val KEY_FINISHED_EXAMS = "finished_exams"
        private const val KEY_CACHED_MANIFEST = "cached_manifest"
        private const val KEY_SELECTED_GRADE = "selected_grade"
        private const val KEY_RULES_ACKNOWLEDGED = "rules_acknowledged"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_SERVER_HISTORY = "server_history"
        private const val KEY_KIOSK_COMFORT_MODE = "kiosk_comfort_mode"
        private const val KEY_NOTIFICATION_PROMPTED = "notification_permission_prompted"
        private const val KEY_SCHEDULED_REMINDERS = "scheduled_reminder_ids"
        private const val KEY_NOTIFIED_REMINDERS = "notified_reminder_keys"
    }
}
