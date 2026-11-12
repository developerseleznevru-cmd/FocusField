// Jetpack Security (EncryptedSharedPreferences/MasterKey) объявлен deprecated upstream,
// но остаётся рабочим: см. README, раздел «Безопасность» (миграция на DataStore+Keystore в roadmap).
@file:Suppress("DEPRECATION")

package ru.seleznev.focusfield.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Зашифрованные SharedPreferences (AES-256, ключи в Android Keystore).
 * Требование ТЗ: шифрование пользовательских данных.
 */
object SecurePrefs {

    private const val SETTINGS_FILE = "focusfield_settings_secure"
    private const val AUTH_FILE = "focusfield_auth_secure"
    private const val STATE_FILE = "focusfield_state_secure"

    @Volatile private var settings: SharedPreferences? = null
    @Volatile private var auth: SharedPreferences? = null
    @Volatile private var state: SharedPreferences? = null

    /** Настройки приложения (тема, пороги блокировки, синхронизация и т.д.). */
    fun settings(context: Context): SharedPreferences =
        settings ?: create(context, SETTINGS_FILE).also { settings = it }

    /** Данные локальной аутентификации (резервный режим без Firebase). */
    fun auth(context: Context): SharedPreferences =
        auth ?: create(context, AUTH_FILE).also { auth = it }

    /** Состояние блокировки: активная сессия, уровень концентрации, «помехи». */
    fun state(context: Context): SharedPreferences =
        state ?: create(context, STATE_FILE).also { state = it }

    private fun create(context: Context, name: String): SharedPreferences {
        synchronized(SecurePrefs) {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            @Suppress("DEPRECATION")
            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
