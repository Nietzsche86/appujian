package id.sch.examkiosk.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var schoolName: String
        get() = prefs.getString(KEY_SCHOOL_NAME, "MTS ROUDHOTUT THOWALIB") ?: "MTS ROUDHOTUT THOWALIB"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NAME, value).apply()

    var schoolTagline: String
        get() = prefs.getString(KEY_SCHOOL_TAGLINE, "Madrasah Tsanawiyah Unggulan & Berkarakter") ?: "Madrasah Tsanawiyah Unggulan & Berkarakter"
        set(value) = prefs.edit().putString(KEY_SCHOOL_TAGLINE, value).apply()

    var schoolNpsn: String
        get() = prefs.getString(KEY_SCHOOL_NPSN, "20278144") ?: "20278144"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NPSN, value).apply()

    var schoolNsm: String
        get() = prefs.getString(KEY_SCHOOL_NSM, "121232010001") ?: "121232010001"
        set(value) = prefs.edit().putString(KEY_SCHOOL_NSM, value).apply()

    var schoolAddress: String
        get() = prefs.getString(KEY_SCHOOL_ADDRESS, "Jl. Raya Timur No. 45, Kec. Taraju, Kab. Tasikmalaya, Jawa Barat") ?: "Jl. Raya Timur No. 45, Kec. Taraju, Kab. Tasikmalaya, Jawa Barat"
        set(value) = prefs.edit().putString(KEY_SCHOOL_ADDRESS, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var masterPinHash: String
        get() = prefs.getString(KEY_MASTER_PIN_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MASTER_PIN_HASH, value).apply()

    var activeExamId: String?
        get() = prefs.getString(KEY_ACTIVE_EXAM_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_EXAM_ID, value).apply()

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
        private const val KEY_MASTER_PIN_HASH = "master_pin_hash"
        private const val KEY_ACTIVE_EXAM_ID = "active_exam_id"
        private const val KEY_REMAINING_EXAM_SECONDS = "remaining_exam_seconds"
        private const val KEY_VIOLATION_COUNT = "violation_count"
        private const val KEY_REMAINING_BAN_SECONDS = "remaining_ban_seconds"
        private const val KEY_LAST_BOOT_COUNT = "last_boot_count"
        private const val KEY_FINISHED_EXAMS = "finished_exams"
        private const val KEY_CACHED_MANIFEST = "cached_manifest"
        private const val KEY_SELECTED_GRADE = "selected_grade"
    }
}
