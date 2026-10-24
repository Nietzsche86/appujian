package id.sch.examkiosk.data.model

import id.sch.examkiosk.util.ExamScheduleHelper

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
