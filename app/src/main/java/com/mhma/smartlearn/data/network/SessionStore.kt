package com.mhma.smartlearn.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the MoodleSession cookie in encrypted shared preferences.
 * Nothing else in the app touches the cookie directly.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { openPrefs() }

    fun hasCookie(): Boolean = cookieHeader() != null

    /** Ready-to-send `Cookie` header value, e.g. `MoodleSession=abc123`. */
    fun cookieHeader(): String? {
        val name = prefs.getString(KEY_NAME, null)
        val value = prefs.getString(KEY_VALUE, null)
        if (name.isNullOrBlank() || value.isNullOrBlank()) return null
        return "$name=$value"
    }

    fun savedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    fun save(cookieName: String, cookieValue: String) {
        prefs.edit {
            putString(KEY_NAME, cookieName)
            putString(KEY_VALUE, cookieValue)
            putLong(KEY_SAVED_AT, System.currentTimeMillis())
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun openPrefs(): SharedPreferences = try {
        encrypted()
    } catch (e: Exception) {
        // The keystore entry can go bad after a device restore. The only thing
        // in here is a cookie, so wiping the file and starting over is fine.
        context.deleteSharedPreferences(FILE_NAME)
        encrypted()
    }

    private fun encrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE_NAME = "session"
        const val KEY_NAME = "cookie_name"
        const val KEY_VALUE = "cookie_value"
        const val KEY_SAVED_AT = "saved_at"
    }
}
