package id.mtsrth.exam.data.crypto

import android.util.Base64
import id.mtsrth.exam.data.local.SecureStorage
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifikator tanda tangan digital ECDSA-P256 (SHA256withECDSA) untuk manifest.json
 * disertai mekanisme Trust-On-First-Use (TOFU) public key pinning agar siswa/MITM
 * tidak dapat menimpa Master PIN atau PIN Harian melalui server palsu.
 */
object ManifestSignatureVerifier {

    private const val KEY_SIGNATURE = "manifest_signature"
    private const val KEY_PUBLIC_KEY = "manifest_public_key_spki"

    sealed interface VerifyResult {
        data class Valid(val publicKeySpkiBase64: String, val isNewlyPinned: Boolean) : VerifyResult
        data class Invalid(val reason: String) : VerifyResult
    }

    fun verifyAndCheckPinning(rawManifestJson: String, storage: SecureStorage): VerifyResult {
        val root = try {
            JSONObject(rawManifestJson)
        } catch (_: Exception) {
            return VerifyResult.Invalid("Format JSON manifest tidak valid.")
        }

        val signatureB64 = root.optString(KEY_SIGNATURE, "").trim()
        val pubKeyB64 = root.optString(KEY_PUBLIC_KEY, "").trim()

        if (signatureB64.isEmpty() || pubKeyB64.isEmpty()) {
            return VerifyResult.Invalid(
                "Manifest tidak memiliki tanda tangan digital resmi dari server proktor. Pastikan server proktor sudah diperbarui."
            )
        }

        // Cek Public Key Pinning (TOFU)
        val pinnedKey = storage.trustedServerPublicKey
        if (pinnedKey.isNotBlank() && pinnedKey != pubKeyB64) {
            return VerifyResult.Invalid(
                "Identitas kunci kriptografi server BERBEDA dari server proktor terdaftar! Sinkronisasi ditolak untuk mencegah pemalsuan server/PIN."
            )
        }

        // Bangun payload kanonis (urut kunci alfabetis, tanpa field signature/public_key, tanpa spasi)
        val canonicalObject = JSONObject()
        val keys = root.keys().asSequence()
            .filter { it != KEY_SIGNATURE && it != KEY_PUBLIC_KEY }
            .toList()
            .sorted()
        for (key in keys) {
            canonicalObject.put(key, root.get(key))
        }
        val canonicalJson = serializeCanonical(canonicalObject)

        return try {
            val pubKeyBytes = Base64.decode(pubKeyB64, Base64.DEFAULT)
            val sigBytes = Base64.decode(signatureB64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(pubKeyBytes)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(keySpec)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(canonicalJson.toByteArray(Charsets.UTF_8))

            if (verifier.verify(sigBytes)) {
                val newlyPinned = pinnedKey.isBlank()
                VerifyResult.Valid(publicKeySpkiBase64 = pubKeyB64, isNewlyPinned = newlyPinned)
            } else {
                VerifyResult.Invalid("Tanda tangan digital manifest tidak valid! Data kemungkinan telah dimodifikasi.")
            }
        } catch (_: Exception) {
            VerifyResult.Invalid("Gagal memverifikasi tanda tangan digital manifest server.")
        }
    }

    /**
     * Serialisasi JSON kanonis yang deterministik (setara Python json.dumps(..., sort_keys=True, separators=(",", ":"), ensure_ascii=True)).
     */
    private fun serializeCanonical(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is Boolean -> if (value) "true" else "false"
            is Number -> {
                if (value is Double && value.isFinite() && value % 1.0 == 0.0) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            is String -> escapeJsonAscii(value)
            is JSONObject -> {
                val sortedKeys = value.keys().asSequence().toList().sorted()
                sortedKeys.joinToString(separator = ",", prefix = "{", postfix = "}") { k ->
                    "${escapeJsonAscii(k)}:${serializeCanonical(value.get(k))}"
                }
            }
            is JSONArray -> {
                (0 until value.length()).joinToString(separator = ",", prefix = "[", postfix = "]") { idx ->
                    serializeCanonical(value.get(idx))
                }
            }
            else -> escapeJsonAscii(value.toString())
        }
    }

    private fun escapeJsonAscii(str: String): String {
        val sb = StringBuilder(str.length + 2)
        sb.append('"')
        for (ch in str) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    val code = ch.code
                    if (code < 0x20 || code > 0x7E) {
                        sb.append(String.format("\\u%04x", code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
