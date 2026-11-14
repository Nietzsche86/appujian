package id.mtsrth.exam.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.model.ExamItem
import id.mtsrth.exam.data.model.ExamManifest
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.serialization.json.Json
import java.util.Calendar

object ExamReminderScheduler {

    const val CHANNEL_ID = "exam_reminder_5min"
    private const val CHANNEL_NAME = "Pengingat Ujian"
    private const val CHANNEL_DESC = "Notifikasi pengingat 5 menit sebelum ujian dimulai"
    private const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun rescheduleFromStorage(context: Context) {
        val storage = ExamApp.instance.secureStorage
        val cached = storage.cachedManifest
        if (cached.isNullOrBlank()) {
            cancelAllScheduled(context)
            return
        }
        try {
            val manifest = json.decodeFromString<ExamManifest>(cached)
            rescheduleForExams(context, manifest.exams, storage.selectedGrade)
        } catch (_: Exception) {
        }
    }

    fun rescheduleForExams(
        context: Context,
        allExams: List<ExamItem>,
        selectedGrade: Int
    ) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val storage = ExamApp.instance.secureStorage

        // Batalkan alarm lama sebelum menjadwalkan ulang agar sinkron dengan kelas & status terbaru
        cancelAllScheduled(context, alarmManager)

        val now = System.currentTimeMillis()
        val newlyScheduledIds = mutableSetOf<String>()

        val targetExams = allExams.filter { exam ->
            exam.gradeLevel == selectedGrade && !storage.isExamFinished(exam.id)
        }

        for (exam in targetExams) {
            val triggerMillis = computeFiveMinutesBeforeMillis(exam) ?: continue
            if (triggerMillis <= now) continue

            val pendingIntent = buildReceiverPendingIntent(
                context = context,
                examId = exam.id,
                subjectName = exam.subjectName,
                gradeLevel = exam.gradeLevel,
                startTime = exam.startTime,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleAlarmSafely(alarmManager, triggerMillis, pendingIntent)
            newlyScheduledIds.add(exam.id)
        }

        storage.setScheduledReminderIds(newlyScheduledIds)
    }

    fun cancelExamReminder(context: Context, examId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val storage = ExamApp.instance.secureStorage

        val existing = buildExistingPendingIntent(context, examId)
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }

        val currentIds = storage.getScheduledReminderIds().toMutableSet()
        if (currentIds.remove(examId)) {
            storage.setScheduledReminderIds(currentIds)
        }
    }

    private fun cancelAllScheduled(
        context: Context,
        alarmManager: AlarmManager? = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    ) {
        val storage = ExamApp.instance.secureStorage
        val previousIds = storage.getScheduledReminderIds()
        if (alarmManager != null) {
            for (examId in previousIds) {
                val existing = buildExistingPendingIntent(context, examId)
                if (existing != null) {
                    alarmManager.cancel(existing)
                    existing.cancel()
                }
            }
        }
        storage.setScheduledReminderIds(emptySet())
    }

    private fun scheduleAlarmSafely(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operation
            )
        }
    }

    private fun computeFiveMinutesBeforeMillis(exam: ExamItem): Long? {
        val startMin = ExamScheduleHelper.parseMinutesOfDay(exam.startTime) ?: return null
        val hour = startMin / 60
        val minute = startMin % 60

        val startCal = Calendar.getInstance()
        if (exam.examDate.isNotBlank()) {
            val parts = exam.examDate.trim().split("-")
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = (parts[1].toIntOrNull() ?: return null) - 1
            val day = parts[2].toIntOrNull() ?: return null
            startCal.set(year, month, day, hour, minute, 0)
        } else {
            startCal.set(Calendar.HOUR_OF_DAY, hour)
            startCal.set(Calendar.MINUTE, minute)
            startCal.set(Calendar.SECOND, 0)
        }
        startCal.set(Calendar.MILLISECOND, 0)

        return startCal.timeInMillis - FIVE_MINUTES_MILLIS
    }

    private fun buildReceiverPendingIntent(
        context: Context,
        examId: String,
        subjectName: String,
        gradeLevel: Int,
        startTime: String,
        flags: Int
    ): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            action = ExamReminderReceiver.ACTION_EXAM_REMINDER_5M
            putExtra(ExamReminderReceiver.EXTRA_EXAM_ID, examId)
            putExtra(ExamReminderReceiver.EXTRA_SUBJECT_NAME, subjectName)
            putExtra(ExamReminderReceiver.EXTRA_GRADE_LEVEL, gradeLevel)
            putExtra(ExamReminderReceiver.EXTRA_START_TIME, startTime)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeForExam(examId),
            intent,
            flags
        )
    }

    private fun buildExistingPendingIntent(context: Context, examId: String): PendingIntent? {
        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            action = ExamReminderReceiver.ACTION_EXAM_REMINDER_5M
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeForExam(examId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestCodeForExam(examId: String): Int = 50000 + (examId.hashCode() and 0x0FFF_FFFF)
}
