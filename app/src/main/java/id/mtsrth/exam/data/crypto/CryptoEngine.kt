package id.mtsrth.exam.data.crypto

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class DecryptedPdfSession(
    val pfd: ParcelFileDescriptor,
    val tempFile: File
)

class CryptoEngine(private val context: Context) {

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.trim()
        require(cleanHex.isNotEmpty() && cleanHex.length % 2 == 0) {
            "Parameter kriptografi hex tidak valid."
        }
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val high = Character.digit(cleanHex[i], 16)
            val low = Character.digit(cleanHex[i + 1], 16)
            require(high >= 0 && low >= 0) {
                "Karakter hex kriptografi tidak valid."
            }
            data[i / 2] = ((high shl 4) + low).toByte()
            i += 2
        }
        return data
    }

    fun deriveKey(
        token: String,
        saltHex: String,
        iterationCount: Int = DEFAULT_ITERATION_COUNT
    ): SecretKey {
        val safeIterations = iterationCount.coerceIn(MIN_ITERATION_COUNT, MAX_ITERATION_COUNT)
        val salt = hexStringToByteArray(saltHex)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val passwordChars = token.toCharArray()
        val spec: KeySpec = PBEKeySpec(passwordChars, salt, safeIterations, KEY_LENGTH_BITS)
        return try {
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, AES_ALGORITHM)
        } finally {
            passwordChars.fill('\u0000')
            (spec as? PBEKeySpec)?.clearPassword()
        }
    }

    suspend fun decryptToUnlinkedPfd(
        encryptedFile: File,
        token: String,
        saltHex: String,
        ivHex: String,
        iterationCount: Int = LEGACY_ITERATION_COUNT
    ): Result<DecryptedPdfSession> = withContext(Dispatchers.IO) {
        val tempPdf = File(context.cacheDir, "$TEMP_PDF_PREFIX${System.nanoTime()}.pdf")
        try {
            val key = deriveKey(token, saltHex, iterationCount)
            val iv = hexStringToByteArray(ivHex)
            require(iv.size == 12) { "Panjang IV AES-GCM tidak valid." }

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            // Stream decrypt via update() + doFinal() untuk kehandalan autentikasi AES-GCM
            FileInputStream(encryptedFile).use { fis ->
                FileOutputStream(tempPdf).use { fos ->
                    val inBuffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(inBuffer).also { bytesRead = it } != -1) {
                        val chunk = cipher.update(inBuffer, 0, bytesRead)
                        if (chunk != null && chunk.isNotEmpty()) {
                            fos.write(chunk)
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        fos.write(finalChunk)
                    }
                    fos.flush()
                }
            }

            // Validasi format Magic Bytes (%PDF-)
            if (tempPdf.length() < 4) {
                tempPdf.delete()
                return@withContext Result.failure(IllegalStateException("Berkas hasil dekripsi kosong."))
            }

            val header = ByteArray(4)
            FileInputStream(tempPdf).use { it.read(header) }
            val isPdf = header[0] == 0x25.toByte() && // '%'
                        header[1] == 0x50.toByte() && // 'P'
                        header[2] == 0x44.toByte() && // 'D'
                        header[3] == 0x46.toByte()    // 'F'

            if (!isPdf) {
                tempPdf.delete()
                return@withContext Result.failure(
                    IllegalStateException("Berkas yang didekripsi bukan format PDF yang valid! Pastikan berkas yang diunggah ke server proktor adalah file PDF asli (bukan halaman error web).")
                )
            }

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            // Segera unlink file dari direktori cache Linux agar plaintext PDF tidak tersisa di disk
            // selama sesi ujian maupun saat aplikasi dihentikan paksa (FD tetap valid hingga pfd.close()).
            runCatching { tempPdf.delete() }
            Result.success(DecryptedPdfSession(pfd, tempPdf))
        } catch (e: Exception) {
            runCatching {
                if (tempPdf.exists()) {
                    tempPdf.delete()
                }
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val LEGACY_ITERATION_COUNT = 10_000
        const val DEFAULT_ITERATION_COUNT = 210_000
        private const val MIN_ITERATION_COUNT = 10_000
        private const val MAX_ITERATION_COUNT = 1_000_000
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val BUFFER_SIZE = 8192
        private const val TEMP_PDF_PREFIX = "temp_active_"

        fun cleanupStaleTempPdfs(context: Context) {
            runCatching {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(TEMP_PDF_PREFIX) && file.name.endsWith(".pdf")) {
                        file.delete()
                    }
                }
            }
        }
    }
}
