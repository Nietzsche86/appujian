package id.sch.examkiosk.data.model

enum class ExamStatus {
    BELUM_DIUNDUH,
    TERSIMPAN,
    SEDANG_UJIAN,
    SELESAI
}

data class ExamItemUiModel(
    val exam: ExamItem,
    val status: ExamStatus,
    val isFilePresent: Boolean
)
