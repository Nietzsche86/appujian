package id.sch.examkiosk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamManifest(
    @SerialName("school_name")
    val schoolName: String = "MTS ROUDHOTUT THOWALIB",

    @SerialName("school_tagline")
    val schoolTagline: String = "Madrasah Tsanawiyah Unggulan & Berkarakter",

    @SerialName("school_npsn")
    val schoolNpsn: String = "20278144",

    @SerialName("school_nsm")
    val schoolNsm: String = "121232010001",

    @SerialName("school_address")
    val schoolAddress: String = "Jl. Raya Timur No. 45, Kec. Taraju, Kab. Tasikmalaya, Jawa Barat",

    @SerialName("exam_title")
    val examTitle: String = "Penilaian Akhir Semester (PAS) Ganjil",

    @SerialName("sync_timestamp")
    val syncTimestamp: Long = 0L,

    @SerialName("master_pin_sha256")
    val masterPinSha256: String = "",

    @SerialName("exam_rules")
    val examRules: List<String> = emptyList(),

    @SerialName("exams")
    val exams: List<ExamItem> = emptyList()
)
