package id.sch.examkiosk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamManifest(
    @SerialName("school_name")
    val schoolName: String = "MTS ROUDHOTUT THOWALIB",

    @SerialName("sync_timestamp")
    val syncTimestamp: Long = 0L,

    @SerialName("master_pin_sha256")
    val masterPinSha256: String = "",

    @SerialName("exams")
    val exams: List<ExamItem> = emptyList()
)
