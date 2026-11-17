package ru.seleznev.focusfield

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.seleznev.focusfield.data.crypto.PasswordHasher
import ru.seleznev.focusfield.data.model.AuthState
import ru.seleznev.focusfield.data.prefs.SecurePrefs
import ru.seleznev.focusfield.data.repo.AuthRepository

/**
 * Инструментальная проверка сохранности зарегистрированных пользователей
 * при выходе из аккаунта (реальные EncryptedSharedPreferences + Keystore).
 *
 * Использует общий с приложением файл focusfield_auth_secure: тестовый
 * аккаунт создаётся и удаляется в конце. Сессия приложения сбрасывается
 * (это ожидаемо для сценария «выход»).
 */
@RunWith(AndroidJUnit4::class)
class AuthPersistenceTest {

    @Test
    fun registeredUserSurvivesSignOut() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = AuthRepository(context)

        val email = "uitest-${System.currentTimeMillis()}@example.com"
        val password = "Test123!"

        // 1. Регистрация нового пользователя.
        val registered = repo.register(email, password, "Тестов Тест")
        assertTrue("Регистрация должна пройти: ${registered.exceptionOrNull()?.message}", registered.isSuccess)
        assertEquals(AuthState.SignedIn(email, "local"), repo.state.value)

        // 2. Выход из аккаунта: сессия снимается, учётная запись остаётся.
        repo.signOut()
        assertEquals(AuthState.SignedOut, repo.state.value)

        // 3. Повторный вход теми же учётными данными — пользователь должен найтись.
        val back = repo.signIn(email, password)
        assertTrue(
            "Пользователь должен сохраняться после выхода: ${back.exceptionOrNull()?.message}",
            back.isSuccess
        )
        assertEquals(AuthState.SignedIn(email, "local"), repo.state.value)

        // 4. Уборка: удаляем тестовый аккаунт и снимаем сессию.
        val prefs = SecurePrefs.auth(context)
        prefs.edit()
            .remove("user:$email")
            .remove("profile:$email")
            .remove("last_email")
            .remove("last_profile")
            .remove("session_email")
            .apply()
    }

    @Test
    fun passwordHasherRoundTrip() {
        val stored = PasswordHasher.hash("Test123!")
        assertTrue(PasswordHasher.verify("Test123!", stored))
    }
}
