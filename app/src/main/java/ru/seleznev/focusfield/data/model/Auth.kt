package ru.seleznev.focusfield.data.model

/** Состояние аутентификации пользователя. */
sealed interface AuthState {
    /** Проверяем сохранённую сессию / обращаемся к Firebase. */
    data object Loading : AuthState

    /** Пользователь не аутентифицирован. */
    data object SignedOut : AuthState

    data class SignedIn(val email: String, val provider: String) : AuthState
}

/** Аккаунт, сохранённый в локальном хранилище (для экрана входа после выхода). */
data class LocalAccount(
    val email: String,
    val fullName: String
)
