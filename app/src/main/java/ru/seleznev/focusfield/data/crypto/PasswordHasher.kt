package ru.seleznev.focusfield.data.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Хеширование пароля для локального резервного режима аутентификации
 * (когда Firebase не настроен). Итеративный SHA-512 со случайной солью:
 * работает на всех уровнях API и в JVM-тестах без зависимостей от провайдеров.
 *
 * Формат: base64(salt) + '$' + base64(hash).
 */
object PasswordHasher {

    private const val ITERATIONS = 30_000

    fun hash(password: String, salt: ByteArray = newSalt()): String {
        require(password.isNotEmpty()) { "Пустой пароль" }
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        var digest = MessageDigest.getInstance("SHA-512")
        digest.update(salt)
        digest.update(passwordBytes)
        var current = digest.digest()
        repeat(ITERATIONS) {
            val md = MessageDigest.getInstance("SHA-512")
            md.update(current)
            md.update(salt)
            md.update(passwordBytes)
            current = md.digest()
        }
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(salt) + "$" + encoder.encodeToString(current)
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 2) return false
        return try {
            val salt = Base64.getDecoder().decode(parts[0])
            MessageDigest.isEqual(
                hash(password, salt).toByteArray(StandardCharsets.UTF_8),
                stored.toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
}
