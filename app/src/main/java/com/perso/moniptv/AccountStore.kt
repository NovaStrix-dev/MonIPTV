package com.perso.moniptv

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** Stocke le compte IPTV chiffré sur l'appareil (Android Keystore). */
class AccountStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Fichier corrompu (ex. restauration) : on repart de zéro.
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences = EncryptedSharedPreferences.create(
        FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Account? {
        val type = prefs.getString("type", null) ?: return null
        return Account(
            type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.XTREAM),
            server = prefs.getString("server", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
        )
    }

    fun save(a: Account) {
        prefs.edit()
            .putString("type", a.type.name)
            .putString("server", a.server)
            .putString("username", a.username)
            .putString("password", a.password)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "account_secure"
    }
}
