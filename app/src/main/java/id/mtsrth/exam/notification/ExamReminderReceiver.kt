package id.mtsrth.exam.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.R
import id.mtsrth.exam.ui.dashboard.DashboardActivity

class ExamReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXAM_REMINDER_5M) return

        val examId = intent.getStringExtra(EXTRA_EXAM_ID) ?: return
        val subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Ujian"
        val gradeLevel = intent.getIntExtra(EXTRA_GRADE_LEVEL, 7)
        val startTime = intent.getStringExtra(EXTRA_START_TIME) ?: ""

        val storage = ExamApp.instance.secureStorage

        // Jangan tampilkan notifikasi jika siswa sedang dalam sesi ujian Kiosk aktif
        if (!storage.activeExamId.isNullOrBlank()) return

        // Jangan tampilkan jika soal sudah selesai dikerjakan atau kelas berbeda
        if (storage.isExamFinished(examId)) return
        if (storage.selectedGrade != gradeLevel) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ExamReminderScheduler.ensureChannel(context)

        val tapIntent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DashboardActivity.EXTRA_OPEN_TAB, DashboardActivity.TAB_EXAMS)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            ExamReminderScheduler.requestCodeForExam(examId),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scheduleDetail = if (startTime.isNotBlank()) "pukul $startTime WIB" else "sebentar lagi"
        val bodyText = "Ujian $subjectName (Kelas $gradeLevel) dimulai $scheduleDetail. " +
            "Segera aktifkan Mode Pesawat, Mode Hening, serta matikan Wi-Fi & Bluetooth."

        val notification = NotificationCompat.Builder(context, ExamReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(0xFF0B6E4F.toInt())
            .setContentTitle("Ujian $subjectName 5 Menit Lagi")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(ExamReminderScheduler.requestCodeForExam(examId), notification)
    }

    companion object {
        const val ACTION_EXAM_REMINDER_5M = "id.mtsrth.exam.ACTION_EXAM_REMINDER_5M"
        const val EXTRA_EXAM_ID = "extra_exam_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_GRADE_LEVEL = "extra_grade_level"
        const val EXTRA_START_TIME = "extra_start_time"
    }
}
