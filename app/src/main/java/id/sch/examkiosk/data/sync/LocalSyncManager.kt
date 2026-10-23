package id.sch.examkiosk.data.sync

import android.content.Context
import id.sch.examkiosk.data.model.ExamManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
        return File(examsDirectory, "$examId.enc")
    }

    fun isExamFileDownloaded(examId: String): Boolean {
        val file = getExamFile(examId)
        return file.exists() && file.length() > 0
    }

    suspend fun testConnection(serverUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeUrl(serverUrl)

            // 1. Coba HEAD request terlebih dahulu
            try {
                val headReq = Request.Builder()
                    .url(normalizedUrl)
                    .head()
                    .build()

                client.newCall(headReq).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 200..404) {
                        return@runCatching true
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fallback uji langsung endpoint /manifest.json
            val manifestUrl = "${normalizedUrl.trimEnd('/')}/manifest.json"
            val getReq = Request.Builder()
                .url(manifestUrl)
                .get()
                .build()

            client.newCall(getReq).execute().use { resp ->
                resp.isSuccessful || resp.code in 200..404
            }
        }
    }

    suspend fun fetchManifest(serverUrl: String): Result<ExamManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBase = normalizeUrl(serverUrl).trimEnd('/')
            val manifestUrl = "$normalizedBase/manifest.json"

            val request = Request.Builder()
                .url(manifestUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Gagal mengunduh manifest (HTTP ${response.code})")
                }
                val bodyString = response.body?.string()
                    ?: throw IllegalStateException("Manifest response body kosong")

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
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalLength > 0 && onProgress != null) {
                                val progress = ((downloadedBytes * 100) / totalLength).toInt()
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Validasi checksum SHA-256
            val actualChecksum = calculateSha256(tempFile)
            if (expectedSha256.isNotBlank() && !actualChecksum.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                throw IllegalStateException("Checksum berkas tidak valid! File kemungkinan korup.")
            }

            // Ganti nama ke file .enc final
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                // Fallback copy if rename fails
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            destFile
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    companion object {
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
