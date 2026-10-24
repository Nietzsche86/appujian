package id.mtsrth.exam.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamManifest(
    @SerialName("school_name")
    val schoolName: String = "MTS ROUDHOTUT THOWALIB",

    @SerialName("school_tagline")
    val schoolTagline: String = "Madrasah Tsanawiyah Unggulan & Berkarakter",

    @SerialName("school_npsn")
    val schoolNpsn: String = "20278365",

    @SerialName("school_nsm")
    val schoolNsm: String = "121232050172",

    @SerialName("school_address")
    val schoolAddress: String = "Kp. Bojong Jaya, Des. Pasirkiamis, Kec. Pasirwangi, Kab. Garut, Jawa Barat.",

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
