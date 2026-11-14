package id.mtsrth.exam.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.model.ExamItem
import id.mtsrth.exam.data.model.ExamManifest
import id.mtsrth.exam.ui.dashboard.DashboardActivity
import id.mtsrth.exam.util.ExamScheduleHelper
import kotlinx.serialization.json.Json

object ExamReminderScheduler {

    const val CHANNEL_ID = "exam_reminder_5min"
    private const val CHANNEL_NAME = "Pengingat Ujian"
    private const val CHANNEL_DESC = "Notifikasi pengingat 5 menit sebelum ujian dimulai"
    private const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun reminderKey(examId: String, examDate: String, startTime: String): String {
        return "${examId}|${examDate.trim()}|${startTime.trim()}"
    }

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
                    enableLights(true)
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

        val validKeys = allExams.map { reminderKey(it.id, it.examDate, it.startTime) }.toSet()
        storage.pruneNotifiedReminderKeys(validKeys)

        val now = System.currentTimeMillis()
        val newlyScheduledIds = mutableSetOf<String>()

        val targetExams = allExams.filter { exam ->
            exam.gradeLevel == selectedGrade && !storage.isExamFinished(exam.id)
        }

        for (exam in targetExams) {
            val startMillis = ExamScheduleHelper.getExamStartMillis(exam, now) ?: continue
            if (now >= startMillis) continue

            val triggerMillis = startMillis - FIVE_MINUTES_MILLIS
            val key = reminderKey(exam.id, exam.examDate, exam.startTime)

            if (now >= triggerMillis) {
                // Sudah berada di jendela H-5 menit sebelum ujian dimulai -> langsung tampilkan pengingat
                ExamReminderReceiver.showNotificationNow(
                    context = context.applicationContext,
                    examId = exam.id,
                    subjectName = exam.subjectName,
                    gradeLevel = exam.gradeLevel,
                    examDate = exam.examDate,
                    startTime = exam.startTime,
                    startMillis = startMillis
                )
                continue
            }

            if (storage.isReminderNotified(key)) continue

            val pendingIntent = buildReceiverPendingIntent(
                context = context,
                exam = exam,
                startMillis = startMillis,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleAlarmSafely(context, alarmManager, triggerMillis, exam.id, pendingIntent)
            newlyScheduledIds.add(exam.id)
        }

        storage.setScheduledReminderIds(newlyScheduledIds)
    }

    /**
     * Dipanggil berkala saat aplikasi terbuka agar notifikasi tepat waktu detik-demi-detik
     * meskipun OEM menahan broadcast alarm di latar belakang.
     */
    fun checkDueRemindersNow(context: Context) {
        val storage = ExamApp.instance.secureStorage
        if (!storage.activeExamId.isNullOrBlank()) return
        val cached = storage.cachedManifest ?: return

        val manifest = try {
            json.decodeFromString<ExamManifest>(cached)
        } catch (_: Exception) {
            return
        }

        val now = System.currentTimeMillis()
        val selectedGrade = storage.selectedGrade

        for (exam in manifest.exams) {
            if (exam.gradeLevel != selectedGrade || storage.isExamFinished(exam.id)) continue
            val startMillis = ExamScheduleHelper.getExamStartMillis(exam, now) ?: continue
            val triggerMillis = startMillis - FIVE_MINUTES_MILLIS
            if (now in triggerMillis until startMillis) {
                val key = reminderKey(exam.id, exam.examDate, exam.startTime)
                if (!storage.isReminderNotified(key)) {
                    ExamReminderReceiver.showNotificationNow(
                        context = context.applicationContext,
                        examId = exam.id,
                        subjectName = exam.subjectName,
                        gradeLevel = exam.gradeLevel,
                        examDate = exam.examDate,
                        startTime = exam.startTime,
                        startMillis = startMillis
                    )
                }
            }
        }
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
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        examId: String,
        operation: PendingIntent
    ) {
        val canExact = canScheduleExactAlarms(context)

        try {
            if (canExact) {
                // setAlarmClock paling andal menembus Doze & penghemat baterai OEM (Xiaomi/Samsung/Oppo/Vivo)
                val showIntent = Intent(context, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(DashboardActivity.EXTRA_OPEN_TAB, DashboardActivity.TAB_EXAMS)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCodeForExam(examId) + 10000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(clockInfo, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun buildReceiverPendingIntent(
        context: Context,
        exam: ExamItem,
        startMillis: Long,
        flags: Int
    ): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            action = ExamReminderReceiver.ACTION_EXAM_REMINDER_5M
            putExtra(ExamReminderReceiver.EXTRA_EXAM_ID, exam.id)
            putExtra(ExamReminderReceiver.EXTRA_SUBJECT_NAME, exam.subjectName)
            putExtra(ExamReminderReceiver.EXTRA_GRADE_LEVEL, exam.gradeLevel)
            putExtra(ExamReminderReceiver.EXTRA_EXAM_DATE, exam.examDate)
            putExtra(ExamReminderReceiver.EXTRA_START_TIME, exam.startTime)
            putExtra(ExamReminderReceiver.EXTRA_START_MILLIS, startMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeForExam(exam.id),
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
