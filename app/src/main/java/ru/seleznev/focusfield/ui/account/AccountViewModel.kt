package ru.seleznev.focusfield.ui.account

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import ru.seleznev.focusfield.data.repo.AuthRepository

/**
 * Экран «Аккаунт» (вкладка нижнего меню): данные текущего аккаунта
 * и выход. Раздел вынесен из настроек.
 */
class AccountViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** Email текущего аккаунта (только отображение). */
    val email: String get() = authRepository.currentEmail()

    /** Сохранённое ФИО профиля; пусто, если не заполнено. */
    val fullName: String get() = authRepository.profileFullName()

    /** Фото профиля; null — показывается заглушка. */
    val avatar: Bitmap? get() = authRepository.avatarBitmap()

    /** Выход: MainActivity наблюдает состояние аутентификации и уводит на экран входа. */
    fun signOut() = authRepository.signOut()
}
