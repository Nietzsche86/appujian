package id.mtsrth.exam.data.sync

import android.content.Context
import android.os.Build
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.data.crypto.ManifestSignatureVerifier
import id.mtsrth.exam.data.local.SecureStorage
import id.mtsrth.exam.data.model.ExamManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class ServerPingResult(
    val ok: Boolean = false,
    val app: String = "",
    @SerialName("school_name")
    val schoolName: String = "",
    @SerialName("exam_title")
    val examTitle: String = "",
    @SerialName("exam_count")
    val examCount: Int = 0
)

class LocalSyncManager(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val examsDirectory: File
        get() {
            val dir = File(context.filesDir, "exams")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getExamFile(examId: String): File {
        require(SAFE_EXAM_ID_REGEX.matches(examId)) {
            "Format ID ujian tidak valid."
        }
        val dirCanonical = examsDirectory.canonicalFile
        val target = File(dirCanonical, "$examId.enc").canonicalFile
        require(target.parentFile == dirCanonical) {
            "Path berkas ujian tidak diizinkan."
        }
        return target
    }

    fun isExamFileValid(examId: String, expectedSha256: String): Boolean {
        if (!SAFE_EXAM_ID_REGEX.matches(examId)) return false
        val cleanExpected = expectedSha256.trim()
        // Wajib memiliki checksum SHA-256 yang valid — jangan pernah melewati verifikasi integritas
        if (!SHA256_HEX_REGEX.matches(cleanExpected)) return false
        val file = getExamFile(examId)
        if (!file.exists() || file.length() == 0L) return false
        val actualSha256 = calculateSha256(file)
        return actualSha256.equals(cleanExpected, ignoreCase = true)
    }

    fun isExamFileDownloaded(examId: String): Boolean {
        if (!SAFE_EXAM_ID_REGEX.matches(examId)) return false
        val file = getExamFile(examId)
        return file.exists() && file.length() > 0
    }

    /**
     * Menghapus seluruh berkas .enc atau .tmp lokal yang sudah tidak ada
     * di manifest server (misal: soal telah dihapus oleh pengawas/proktor).
     */
    fun cleanupOrphanedExams(activeExamIds: Set<String>) {
        runCatching {
            val files = examsDirectory.listFiles() ?: return
            for (file in files) {
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                } else if (file.name.endsWith(".enc")) {
                    val examId = file.name.removeSuffix(".enc")
                    if (!activeExamIds.contains(examId)) {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun encodedDeviceLabel(): String {
        val raw = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifEmpty { "Android" }
        return URLEncoder.encode(raw, "UTF-8")
    }

    private fun readLimitedTextBody(response: okhttp3.Response, maxBytes: Long = MAX_JSON_RESPONSE_BYTES): String {
        val body = response.body ?: throw IllegalStateException("Respons server kosong")
        val contentLength = body.contentLength()
        if (contentLength > maxBytes) {
            throw IllegalStateException("Ukuran respons server melebihi batas aman.")
        }
        val source = body.source()
        source.request(maxBytes + 1)
        if (source.buffer.size > maxBytes) {
            throw IllegalStateException("Ukuran respons server melebihi batas aman.")
        }
        return source.readUtf8()
    }

    suspend fun testConnection(serverUrl: String): Result<ServerPingResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBase = normalizeUrl(serverUrl).trimEnd('/')
            val deviceParam = encodedDeviceLabel()

            // 1. Uji endpoint resmi /api/ping agar tidak salah mengenali web server lain di LAN
            try {
                val pingReq = Request.Builder()
                    .url("$normalizedBase/api/ping?device=$deviceParam")
                    .get()
                    .build()

                client.newCall(pingReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = readLimitedTextBody(resp)
                        val ping = json.decodeFromString<ServerPingResult>(body)
                        if (ping.ok && (ping.app.isBlank() || ping.app == "e-ujian-mts-rth")) {
                            return@runCatching ping
                        }
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fallback uji endpoint /manifest.json dan validasi skema JSON
            val manifestReq = Request.Builder()
                .url("$normalizedBase/manifest.json")
                .get()
                .build()

            client.newCall(manifestReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Server merespons HTTP ${resp.code}")
                }
                val body = readLimitedTextBody(resp)
                val manifest = json.decodeFromString<ExamManifest>(body)
                ServerPingResult(
                    ok = true,
                    app = "e-ujian-mts-rth",
                    schoolName = manifest.schoolName,
                    examTitle = manifest.examTitle,
                    examCount = manifest.exams.size
                )
            }
        }
    }

    suspend fun fetchManifest(
        serverUrl: String,
        gradeLevel: Int? = null,
        storage: SecureStorage = ExamApp.instance.secureStorage
    ): Result<ExamManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBase = normalizeUrl(serverUrl).trimEnd('/')
            val queryParams = buildList {
                if (gradeLevel != null) add("grade=$gradeLevel")
                add("device=${encodedDeviceLabel()}")
            }.joinToString("&")
            val manifestUrl = "$normalizedBase/manifest.json?$queryParams"

            val request = Request.Builder()
                .url(manifestUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengunduh manifest (HTTP ${response.code})")
                }
                val bodyString = readLimitedTextBody(response)

                // Verifikasi tanda tangan digital ECDSA-P256 & Trust-On-First-Use (TOFU) public key pinning
                when (val sigCheck = ManifestSignatureVerifier.verifyAndCheckPinning(bodyString, storage)) {
                    is ManifestSignatureVerifier.VerifyResult.Invalid -> {
                        throw SecurityException(sigCheck.reason)
                    }
                    is ManifestSignatureVerifier.VerifyResult.Valid -> {
                        if (sigCheck.isNewlyPinned) {
                            storage.trustedServerPublicKey = sigCheck.publicKeySpkiBase64
                        }
                    }
                }

                json.decodeFromString<ExamManifest>(bodyString)
            }
        }
    }

    suspend fun downloadExamFile(
        downloadUrl: String,
        examId: String,
        expectedSha256: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(SAFE_EXAM_ID_REGEX.matches(examId)) {
                "ID paket soal tidak valid."
            }
            val cleanExpectedSha256 = expectedSha256.trim()
            if (!SHA256_HEX_REGEX.matches(cleanExpectedSha256)) {
                throw SecurityException("Checksum SHA-256 berkas soal wajib tersedia dan valid.")
            }

            val destFile = getExamFile(examId)
            val tempFile = File(examsDirectory, "$examId.tmp")

            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengunduh berkas soal (HTTP ${response.code})")
                }

                val body = response.body ?: throw IllegalStateException("Response body kosong")
                val totalLength = body.contentLength()
                if (totalLength > MAX_EXAM_FILE_BYTES) {
                    throw IllegalStateException("Ukuran berkas soal melebihi batas maksimum (60 MB).")
                }
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            downloadedBytes += bytesRead
                            if (downloadedBytes > MAX_EXAM_FILE_BYTES) {
                                throw IllegalStateException("Ukuran berkas soal melebihi batas maksimum (60 MB).")
                            }
                            output.write(buffer, 0, bytesRead)
                            if (totalLength > 0 && onProgress != null) {
                                val progress = ((downloadedBytes * 100) / totalLength).toInt()
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Validasi checksum SHA-256 secara wajib
            val actualChecksum = calculateSha256(tempFile)
            if (!actualChecksum.equals(cleanExpectedSha256, ignoreCase = true)) {
                tempFile.delete()
                throw IllegalStateException("Checksum berkas tidak valid! File kemungkinan korup atau dimodifikasi.")
            }

            // Ganti nama ke file .enc final
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            destFile
        }
    }

    private fun normalizeUrl(url: String): String = Companion.normalizeUrl(url)

    companion object {
        private val SAFE_EXAM_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,80}$")
        private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private const val MAX_JSON_RESPONSE_BYTES = 2L * 1024L * 1024L // 2 MB
        private const val MAX_EXAM_FILE_BYTES = 60L * 1024L * 1024L    // 60 MB

        fun normalizeUrl(url: String): String {
            var trimmed = url.trim()
            if (trimmed.isEmpty()) return ""
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://$trimmed"
            }
            try {
                val uri = java.net.URI(trimmed)
                if (uri.port == -1) {
                    val host = uri.host ?: trimmed.substringAfter("://").substringBefore("/").substringBefore(":")
                    val path = uri.path ?: ""
                    trimmed = "${uri.scheme}://$host:8080$path"
                }
            } catch (_: Exception) {
                val authority = trimmed.substringAfter("://").substringBefore("/")
                if (!authority.contains(":")) {
                    trimmed = trimmed.replace(authority, "$authority:8080")
                }
            }
            return trimmed.trimEnd('/')
        }

        fun calculateSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun calculateSha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
