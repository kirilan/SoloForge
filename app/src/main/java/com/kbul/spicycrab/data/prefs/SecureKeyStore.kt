package com.kbul.spicycrab.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getOpenRouterKey(): String? = prefs.getString(KEY_OPENROUTER, null)

    fun setOpenRouterKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_OPENROUTER) else putString(KEY_OPENROUTER, value)
        }.apply()
    }

    fun hasOpenRouterKey(): Boolean = !getOpenRouterKey().isNullOrBlank()

    private companion object {
        const val KEY_OPENROUTER = "openrouter_api_key"
    }
}
