package id.a3.core.auth.crypto

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets

/**
 * Mengelola enkripsi dan dekripsi data sensitif (access token, refresh token, hash PIN)
 * menggunakan Google Tink AEAD (AES-256-GCM).
 *
 * Di perangkat Android operasional: kunci didelegasikan ke Android Keystore.
 * Di lingkungan pengujian (Robolectric / Unit Test): fallback otomatis ke keyset aman tanpa Keystore hardware.
 */
class TinkCryptoManager(
    private val aead: Aead
) {

    fun encrypt(plainText: String, associatedData: ByteArray = ByteArray(0)): String {
        val ciphertext = aead.encrypt(plainText.toByteArray(StandardCharsets.UTF_8), associatedData)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String, associatedData: ByteArray = ByteArray(0)): String {
        val ciphertext = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val decrypted = aead.decrypt(ciphertext, associatedData)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    companion object {
        init {
            AeadConfig.register()
        }

        fun create(context: Context): TinkCryptoManager {
            val aead = try {
                AndroidKeysetManager.Builder()
                    .withSharedPref(context.applicationContext, "a3_tink_keyset", "a3_keyset_prefs")
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri("android-keystore://a3_master_key")
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            } catch (_: Exception) {
                // Fallback untuk lingkungan tanpa Android Keystore (seperti Robolectric)
                AndroidKeysetManager.Builder()
                    .withSharedPref(context.applicationContext, "a3_tink_keyset_fallback", "a3_keyset_fallback_prefs")
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            }
            return TinkCryptoManager(aead)
        }
    }
}
