package id.sch.examkiosk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamItem(
    @SerialName("id")
    val id: String,

    @SerialName("subject_name")
    val subjectName: String,

    @SerialName("grade_level")
    val gradeLevel: Int,

    @SerialName("duration_minutes")
    val durationMinutes: Int,

    @SerialName("download_url")
    val downloadUrl: String,

    @SerialName("file_sha256")
    val fileSha256: String,

    @SerialName("pbkdf2_salt_hex")
    val pbkdf2SaltHex: String,

    @SerialName("gcm_iv_hex")
    val gcmIvHex: String
)
