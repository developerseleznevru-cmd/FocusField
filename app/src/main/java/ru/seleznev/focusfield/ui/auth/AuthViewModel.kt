package ru.seleznev.focusfield.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.AuthState
import ru.seleznev.focusfield.data.model.LocalAccount
import ru.seleznev.focusfield.data.repo.AuthRepository

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val state: StateFlow<AuthState> = authRepository.state

    val isFirebaseAvailable: Boolean get() = authRepository.isFirebaseAvailable

    /** Последний использованный аккаунт (для предзаполнения email после выхода). */
    val lastAccount: LocalAccount? get() = authRepository.lastAccount()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun signIn(email: String, password: String) = launch { authRepository.signIn(email, password) }

    fun register(email: String, password: String, fullName: String = "") =
        launch { authRepository.register(email, password, fullName) }

    fun sendPasswordReset(email: String) = launch { authRepository.sendPasswordReset(email) }

    fun signInWithGoogle() = launch { authRepository.signInWithGoogle() }

    fun signInWithVk() = launch { authRepository.signInWithVk() }

    fun consumeMessage() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _loading.value = true
            val result = block()
            result.fold(
                onSuccess = { _message.value = null },
                onFailure = { _message.value = it.message ?: "Ошибка" }
            )
            _loading.value = false
        }
    }
}
