package id.sch.examkiosk.data.crypto

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoEngine(private val context: Context) {

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun deriveKey(token: String, saltHex: String): SecretKey {
        val salt = hexStringToByteArray(saltHex)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(token.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, AES_ALGORITHM)
    }

    suspend fun decryptToUnlinkedPfd(
        encryptedFile: File,
        token: String,
        saltHex: String,
        ivHex: String
    ): Result<ParcelFileDescriptor> = withContext(Dispatchers.IO) {
        val tempPdf = File(context.cacheDir, "temp_active_${System.currentTimeMillis()}.pdf")
        try {
            val key = deriveKey(token, saltHex)
            val iv = hexStringToByteArray(ivHex)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            FileInputStream(encryptedFile).use { fis ->
                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(tempPdf).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (cis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                        fos.flush()
                    }
                }
            }

            // Unlinked File Descriptor: buka descriptor lalu hapus file fisik seketika
            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            tempPdf.delete() // File terhapus dari storage, PFD tetap valid di memori OS
            Result.success(pfd)
        } catch (e: Exception) {
            if (tempPdf.exists()) {
                tempPdf.delete()
            }
            Result.failure(e)
        }
    }

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ITERATION_COUNT = 10000
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val BUFFER_SIZE = 8192
    }
}
