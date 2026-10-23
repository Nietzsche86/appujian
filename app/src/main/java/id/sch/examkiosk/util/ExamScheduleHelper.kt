package id.sch.examkiosk.util

import id.sch.examkiosk.data.model.ExamItem
import java.util.Calendar

object ExamScheduleHelper {

    enum class ScheduleState {
        NOT_STARTED_YET, // Belum masuk jam mulai
        ACTIVE,          // Sedang dalam rentang jam ujian
        EXPIRED          // Jam selesai telah lewat
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

    fun getScheduleState(exam: ExamItem): ScheduleState {
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
}
