package id.mtsrth.exam.util

import id.mtsrth.exam.data.model.ExamItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ExamScheduleHelper {

    enum class ScheduleState {
        NOT_STARTED_YET, // Belum masuk tanggal/jam mulai
        ACTIVE,          // Sedang dalam rentang tanggal dan jam ujian
        EXPIRED          // Tanggal atau jam selesai telah lewat
    }

    fun parseMinutesOfDay(timeStr: String): Int? {
        if (timeStr.isBlank()) return null
        val normalized = timeStr.trim().replace('.', ':')
        val parts = normalized.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) {
                return h * 60 + m
            }
        }
        return null
    }

    fun getExamStartMillis(exam: ExamItem, nowMillis: Long = System.currentTimeMillis()): Long? {
        val startMin = parseMinutesOfDay(exam.startTime) ?: return null
        val hour = startMin / 60
        val minute = startMin % 60
        return buildDateTimeMillis(exam.examDate, hour, minute, nowMillis)
    }

    fun getExamEndMillis(exam: ExamItem, nowMillis: Long = System.currentTimeMillis()): Long? {
        val endMin = parseMinutesOfDay(exam.endTime)
        if (endMin != null) {
            val hour = endMin / 60
            val minute = endMin % 60
            return buildDateTimeMillis(exam.examDate, hour, minute, nowMillis)
        }
        val startMillis = getExamStartMillis(exam, nowMillis) ?: return null
        val totalAllocated = getTotalDurationMinutes(exam)
        return startMillis + totalAllocated * 60_000L
    }

    private fun buildDateTimeMillis(
        dateStr: String,
        hour: Int,
        minute: Int,
        referenceNowMillis: Long
    ): Long? {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceNowMillis
        }
        if (dateStr.isNotBlank()) {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = (parts[1].toIntOrNull() ?: return null) - 1
            val day = parts[2].toIntOrNull() ?: return null
            cal.set(year, month, day, hour, minute, 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
        }
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Membandingkan tanggal ujian (YYYY-MM-DD) dengan tanggal hari ini.
     * Mengembalikan:
     * -1 jika examDate adalah hari yang akan datang (belum mulai)
     *  0 jika examDate adalah hari ini
     *  1 jika examDate sudah lewat (kemarin atau sebelumnya)
     * null jika format tanggal kosong/tidak valid
     */
    fun compareExamDateWithToday(
        dateStr: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int? {
        if (dateStr.isBlank()) return null
        try {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) return null
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1
            val day = parts[2].toInt()

            val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val todayYear = now.get(Calendar.YEAR)
            val todayMonth = now.get(Calendar.MONTH)
            val todayDay = now.get(Calendar.DAY_OF_MONTH)

            val examCal = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayCal = Calendar.getInstance().apply {
                set(todayYear, todayMonth, todayDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return when {
                examCal.after(todayCal) -> -1
                examCal.before(todayCal) -> 1
                else -> 0
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun getScheduleState(
        exam: ExamItem,
        nowMillis: Long = System.currentTimeMillis()
    ): ScheduleState {
        val dateCompare = compareExamDateWithToday(exam.examDate, nowMillis)
        if (dateCompare != null) {
            if (dateCompare < 0) return ScheduleState.NOT_STARTED_YET
            if (dateCompare > 0) return ScheduleState.EXPIRED
        }

        val startMillis = getExamStartMillis(exam, nowMillis) ?: return ScheduleState.ACTIVE
        val endMillis = getExamEndMillis(exam, nowMillis)
            ?: (startMillis + getTotalDurationMinutes(exam) * 60_000L)

        return when {
            nowMillis < startMillis -> ScheduleState.NOT_STARTED_YET
            nowMillis >= endMillis -> ScheduleState.EXPIRED
            else -> ScheduleState.ACTIVE
        }
    }

    /**
     * Menghitung total durasi ujian yang dialokasikan dalam menit.
     */
    fun getTotalDurationMinutes(exam: ExamItem): Int {
        val startMin = parseMinutesOfDay(exam.startTime)
        val endMin = parseMinutesOfDay(exam.endTime)
        if (startMin != null && endMin != null && endMin > startMin) {
            return endMin - startMin
        }
        return if (exam.durationMinutes > 0) exam.durationMinutes else 60
    }

    fun calculateEffectiveDurationMinutes(exam: ExamItem): Int {
        val totalAllocated = getTotalDurationMinutes(exam)
        val nowMillis = System.currentTimeMillis()
        val endMillis = getExamEndMillis(exam, nowMillis) ?: return totalAllocated
        val remainingMillis = endMillis - nowMillis
        val remainingMin = ((remainingMillis + 59_999L) / 60_000L).toInt()
        return remainingMin.coerceIn(1, totalAllocated)
    }

    /**
     * Memformat tanggal untuk tampilan card siswa, contoh: "Senin, 26 Oktober 2026"
     */
    fun formatDisplayDate(dateStr: String, dayName: String): String {
        if (dateStr.isBlank()) {
            return if (dayName.isNotBlank()) dayName else "Jadwal Ujian"
        }
        return try {
            val parts = dateStr.trim().split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val day = parts[2].toInt()
                val cal = Calendar.getInstance().apply { set(year, month, day) }
                val sdf = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
                val dateFormatted = sdf.format(cal.time)
                if (dayName.isNotBlank()) "$dayName, $dateFormatted" else dateFormatted
            } else {
                dayName
            }
        } catch (_: Exception) {
            dayName
        }
    }

    /**
     * Menghasilkan hitung mundur dinamis real-time hingga hitungan detik:
     * - Menjelang mulai (<= 3 jam): "Mulai 4m 32d lagi"
     * - Sedang berlangsung: "Sisa 1j 29m 15d"
     */
    fun getScheduleUrgencyLabel(
        exam: ExamItem,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val dateCompare = compareExamDateWithToday(exam.examDate, nowMillis)
        if (dateCompare != null && dateCompare != 0) return null

        val startMillis = getExamStartMillis(exam, nowMillis) ?: return null
        val endMillis = getExamEndMillis(exam, nowMillis)
            ?: (startMillis + getTotalDurationMinutes(exam) * 60_000L)

        return when {
            nowMillis < startMillis -> {
                val diffMillis = startMillis - nowMillis
                if (diffMillis <= 3 * 60 * 60 * 1000L) {
                    "Mulai ${formatDynamicCountdown(diffMillis)} lagi"
                } else null
            }
            nowMillis < endMillis -> {
                val leftMillis = endMillis - nowMillis
                "Sisa ${formatDynamicCountdown(leftMillis)}"
            }
            else -> null
        }
    }

    private fun formatDynamicCountdown(millis: Long): String {
        val totalSeconds = ((millis + 999L) / 1000L).coerceAtLeast(1L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0 -> "${hours}j ${minutes}m ${seconds}d"
            minutes > 0 -> "${minutes}m ${seconds}d"
            else -> "${seconds}d"
        }
    }

    fun formatSyncTimestamp(timestampMillis: Long): String {
        if (timestampMillis <= 0L) return "Belum pernah sinkron"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
        return sdf.format(java.util.Date(timestampMillis))
    }
}
