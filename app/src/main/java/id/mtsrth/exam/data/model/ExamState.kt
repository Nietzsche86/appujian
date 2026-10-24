package id.mtsrth.exam.data.model

import id.mtsrth.exam.util.ExamScheduleHelper

enum class ExamStatus {
    BELUM_DIUNDUH,
    TERSIMPAN,
    SEDANG_UJIAN,
    SELESAI
}

data class ExamItemUiModel(
    val exam: ExamItem,
    val status: ExamStatus,
    val isFilePresent: Boolean,
    val scheduleState: ExamScheduleHelper.ScheduleState = ExamScheduleHelper.ScheduleState.ACTIVE
)
