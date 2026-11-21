package com.odiousapps.gardencam.onvif

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ONVIFSecurity {
    private const val TAG = "ONVIFSecurity"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "onvif_credentials_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128
    // Encrypted values carry this prefix; anything else is the legacy/portable Base64 form
    private const val PREFIX = "v2:"
    // Left behind by the old implementation, which never actually encrypted anything
    private const val LEGACY_PREFS_NAME = "onvif_secure_prefs"

    fun encryptCredentials(credentials: ONVIFCredentials): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encryptedBytes = cipher.doFinal(serialize(credentials).toByteArray())
            PREFIX + Base64.encodeToString(cipher.iv + encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Can't encrypt credentials (${e.localizedMessage})")
            null
        }
    }

    fun decryptCredentials(encryptedData: String): ONVIFCredentials? {
        if (!encryptedData.startsWith(PREFIX))
            return decodePortable(encryptedData)
        return try {
            val bytes = Base64.decode(encryptedData.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LEN_BITS, bytes, 0, IV_LEN)
            )
            deserialize(String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN)))
        } catch (e: Exception) {
            Log.e(TAG, "Can't decrypt credentials (${e.localizedMessage})")
            null
        }
    }

    fun isEncrypted(data: String): Boolean = data.startsWith(PREFIX)

    /**
     * Device-independent form used in settings exports (the export file has its own
     * password protection). Also the format written by older versions of the app.
     */
    fun encodePortable(credentials: ONVIFCredentials): String {
        return Base64.encodeToString(serialize(credentials).toByteArray(), Base64.NO_WRAP)
    }

    private fun decodePortable(data: String): ONVIFCredentials? {
        return try {
            deserialize(String(Base64.decode(data, Base64.DEFAULT)))
        } catch (e: Exception) {
            null
        }
    }

    fun deleteLegacyKey(context: Context) {
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun serialize(credentials: ONVIFCredentials) =
        "${credentials.username}:${credentials.password}"

    // Usernames can't contain ':' in a URL, but passwords can
    private fun deserialize(str: String): ONVIFCredentials? {
        val parts = str.split(":", limit = 2)
        return if (parts.size == 2) ONVIFCredentials(parts[0], parts[1]) else null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    fun validateInput(input: String): String {
        // Basic input sanitization for ONVIF operations
        return input.replace(Regex("[<>&\"']"), "")
            .trim()
            .take(1000) // Limit length
    }

    fun isValidUrl(url: String): Boolean {
        return try {
            // Fix common URL issues
            val fixedUrl = when {
                url.startsWith("onvif://") -> url.replace("onvif://", "http://")
                !url.startsWith("http://") && !url.startsWith("https://") -> "http://$url"
                else -> url
            }
            
            val uri = java.net.URI(fixedUrl)
            uri.scheme in listOf("http", "https") && uri.host != null
        } catch (e: Exception) {
            false
        }
    }

    fun sanitizeDeviceResponse(response: String): String {
        // Remove potentially dangerous XML content
        return response.replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]*javascript:[^>]*>", RegexOption.IGNORE_CASE), "")
    }
}
