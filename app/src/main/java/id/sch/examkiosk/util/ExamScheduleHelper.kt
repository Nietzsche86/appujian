package id.sch.examkiosk.util

import id.sch.examkiosk.data.model.ExamItem
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
        val parts = timeStr.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        return null
    }

    /**
     * Membandingkan tanggal ujian (YYYY-MM-DD) dengan tanggal hari ini.
     * Mengembalikan:
     * -1 jika examDate adalah hari yang akan datang (belum mulai)
     *  0 jika examDate adalah hari ini
     *  1 jika examDate sudah lewat (kemarin atau sebelumnya)
     * null jika format tanggal kosong/tidak valid
     */
    fun compareExamDateWithToday(dateStr: String): Int? {
        if (dateStr.isBlank()) return null
        try {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) return null
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1 // Calendar month is 0-based
            val day = parts[2].toInt()

            val now = Calendar.getInstance()
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
                examCal.after(todayCal) -> -1 // Tanggal masa depan
                examCal.before(todayCal) -> 1  // Tanggal masa lalu
                else -> 0                      // Hari ini
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun getScheduleState(exam: ExamItem): ScheduleState {
        // 1. Validasi kalender tanggal terlebih dahulu
        val dateCompare = compareExamDateWithToday(exam.examDate)
        if (dateCompare != null) {
            if (dateCompare < 0) {
                return ScheduleState.NOT_STARTED_YET // Hari ujian belum tiba
            } else if (dateCompare > 0) {
                return ScheduleState.EXPIRED // Hari ujian sudah lewat
            }
        }

        // 2. Jika hari ini (atau examDate tidak disetel), validasi jam
        val startMin = parseMinutesOfDay(exam.startTime) ?: return ScheduleState.ACTIVE
        val endMin = parseMinutesOfDay(exam.endTime) ?: (startMin + exam.durationMinutes)

        val now = Calendar.getInstance()
        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return when {
            currentMin < startMin -> ScheduleState.NOT_STARTED_YET
            currentMin >= endMin -> ScheduleState.EXPIRED
            else -> ScheduleState.ACTIVE
        }
    }

    fun calculateEffectiveDurationMinutes(exam: ExamItem): Int {
        val startMin = parseMinutesOfDay(exam.startTime)
        val endMin = parseMinutesOfDay(exam.endTime) ?: (startMin?.plus(exam.durationMinutes))

        if (endMin == null) {
            return exam.durationMinutes
        }

        val now = Calendar.getInstance()
        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val remainingMin = endMin - currentMin

        return remainingMin.coerceIn(1, exam.durationMinutes)
    }

    /**
     * Memformat tanggal untuk tampilan card siswa, contoh: "Senin, 26 Okt"
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
                val sdf = SimpleDateFormat("d MMM", Locale("id", "ID"))
                val dateFormatted = sdf.format(cal.time)
                if (dayName.isNotBlank()) "$dayName, $dateFormatted" else dateFormatted
            } else {
                dayName
            }
        } catch (_: Exception) {
            dayName
        }
    }
}
