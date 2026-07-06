package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's Gemini API key encrypted at rest using Jetpack Security's
 * [EncryptedSharedPreferences]. This is kept in its own preferences file
 * (separate from non-secret reader settings) so the encrypted store never
 * mixes with plaintext values.
 *
 * If the Android Keystore is unavailable or corrupted (a known failure mode on
 * some devices), construction falls back to a regular, unencrypted preferences
 * file so the app keeps working instead of crashing. The fallback is logged.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    fun getApiKey(): String = prefs.getString(KEY_API, "") ?: ""

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API, key).apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plaintext", e)
            context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val ENCRYPTED_FILE = "vault_secure_prefs"
        private const val FALLBACK_FILE = "vault_secure_prefs_fallback"
        private const val KEY_API = "gemini_api_key"
    }
}
