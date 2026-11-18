package ru.seleznev.focusfield.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.repo.AuthRepository

/**
 * Экран редактирования профиля после регистрации:
 * показывает email аккаунта, принимает отредактированное ФИО и фото профиля.
 */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** Email текущего аккаунта (только отображение). */
    val email: String get() = authRepository.currentEmail()

    /** Текущее сохранённое ФИО (для предзаполнения полей). */
    val fullName: String get() = authRepository.profileFullName()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    /** Одноразовое событие «профиль сохранён» — сигнал к переходу в приложение. */
    private val _saved = MutableLiveData(false)
    val saved: LiveData<Boolean> = _saved

    /** Выбранное фото профиля (null — показывается заглушка). */
    private val _avatar = MutableLiveData(authRepository.avatarBitmap())
    val avatar: LiveData<Bitmap?> = _avatar

    fun save(fullName: String) {
        viewModelScope.launch {
            _loading.value = true
            authRepository.updateProfile(fullName)
                .onSuccess { _saved.value = true }
                .onFailure { _message.value = it.message ?: "Ошибка" }
            _loading.value = false
        }
    }

    /** Сохранение фото, выбранного в системном Photo Picker. */
    fun saveAvatar(uri: Uri) {
        viewModelScope.launch {
            authRepository.saveAvatar(uri)
                .onSuccess { _avatar.value = authRepository.avatarBitmap() }
                .onFailure { _message.value = it.message ?: "Не удалось сохранить фото" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeSaved() {
        _saved.value = false
    }
}
