package ru.seleznev.focusfield.data.repo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import ru.seleznev.focusfield.data.crypto.PasswordHasher
import ru.seleznev.focusfield.data.model.AuthState
import ru.seleznev.focusfield.data.model.LocalAccount
import ru.seleznev.focusfield.data.prefs.SecurePrefs
import java.io.File
import java.io.FileOutputStream

/**
 * Аутентификация: Email/пароль + провайдеры Google/Facebook.
 *
 * Основной режим — Firebase Authentication. Если Firebase не настроен
 * (нет google-services.json), репозиторий прозрачно переходит в локальный
 * режим: учётные записи хранятся в зашифрованных SharedPreferences
 * с хешированием пароля (см. [PasswordHasher]).
 */
class AuthRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = SecurePrefs.auth(appContext)

    private val firebaseAuth: FirebaseAuth? by lazy {
        if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseAuth.getInstance() else null
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Одноразовый флаг: после успешной регистрации показывается экран
     * редактирования профиля (см. MainActivity, `consumeProfileSetup`).
     * Устанавливается ДО создания пользователя, чтобы поймать событие
     * Firebase-слушателя; сбрасывается при ошибке.
     */
    @Volatile
    private var profileSetupNeeded = false

    init {
        val firebase = firebaseAuth
        if (firebase != null) {
            firebase.addAuthStateListener { auth ->
                _state.value = auth.currentUser
                    ?.let { AuthState.SignedIn(it.email.orEmpty(), PROVIDER_FIREBASE) }
                    ?: AuthState.SignedOut
            }
        } else {
            val email = prefs.getString(KEY_SESSION_EMAIL, null)
            _state.value = if (email != null) {
                AuthState.SignedIn(email, PROVIDER_LOCAL)
            } else {
                AuthState.SignedOut
            }
        }
    }

    val isFirebaseAvailable: Boolean get() = firebaseAuth != null

    /**
     * Регистрация по email. [fullName] — ФИО профиля («Фамилия Имя Отчество»);
     * сохраняется в displayName пользователя Firebase или в локальном хранилище.
     */
    suspend fun register(email: String, password: String, fullName: String = ""): Result<Unit> {
        val normalized = normalizeEmail(email)
        val validation = validate(normalized, password)
        if (validation != null) return Result.failure(IllegalArgumentException(validation))
        profileSetupNeeded = true
        val result = if (firebaseAuth != null) {
            runCatching {
                val created = firebaseAuth!!.createUserWithEmailAndPassword(normalized, password).await()
                if (fullName.isNotBlank()) {
                    created.user?.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName.trim())
                            .build()
                    )?.await()
                }
                rememberLast(normalized, fullName.trim())
            }.map { }
        } else {
            localRegister(normalized, password, fullName)
        }
        if (result.isFailure) profileSetupNeeded = false
        return result
    }

    /**
     * Нужен ли экран редактирования профиля (true один раз после регистрации).
     * Чтение сбрасывает флаг.
     */
    fun consumeProfileSetup(): Boolean {
        val needed = profileSetupNeeded
        profileSetupNeeded = false
        return needed
    }

    /** Email текущего аккаунта (для отображения на экране профиля). */
    fun currentEmail(): String {
        val firebase = firebaseAuth
        if (firebase != null) return firebase.currentUser?.email.orEmpty()
        return prefs.getString(KEY_SESSION_EMAIL, null).orEmpty()
    }

    /** Сохранённое ФИО профиля («Фамилия Имя Отчество»), пусто если не заполнено. */
    fun profileFullName(): String {
        val firebase = firebaseAuth
        if (firebase != null) return firebase.currentUser?.displayName.orEmpty()
        val email = prefs.getString(KEY_SESSION_EMAIL, null).orEmpty()
        return prefs.getString(profileKey(email), null).orEmpty()
    }

    /** Обновление ФИО профиля текущего аккаунта. */
    suspend fun updateProfile(fullName: String): Result<Unit> {
        val trimmed = fullName.trim()
        val firebase = firebaseAuth
        if (firebase != null) {
            return runCatching {
                firebase.currentUser?.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()
                )?.await()
            }.map { }
        }
        val email = prefs.getString(KEY_SESSION_EMAIL, null)
            ?: return Result.failure(IllegalStateException("Нет активной сессии"))
        val editor = prefs.edit().putString(profileKey(email), trimmed)
        if (email == prefs.getString(KEY_LAST_EMAIL, null)) {
            editor.putString(KEY_LAST_PROFILE, trimmed)
        }
        editor.apply()
        return Result.success(Unit)
    }

    // ---- Аватар профиля ----

    /**
     * Файл с фото профиля аккаунта [email] (создаётся при первом выборе фото).
     * Имя файла — безопасная трансформация email.
     */
    fun avatarFile(email: String = currentEmail()): File? {
        if (email.isBlank()) return null
        val safe = email.replace(Regex("[^A-Za-z0-9._@-]"), "_")
        return File(appContext.filesDir, "avatar_$safe.jpg")
    }

    /**
     * Сохраняет выбранное изображение (системный Photo Picker) как аватар
     * текущего аккаунта: уменьшается до [MAX_AVATAR_PX] px и кладётся в JPEG,
     * чтобы не занимать много места.
     */
    suspend fun saveAvatar(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val file = avatarFile()
            ?: return@withContext Result.failure(IllegalStateException("Нет активной сессии"))
        runCatching {
            val bytes = appContext.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }
                ?: error("Не удалось открыть изображение")
            val bitmap = decodeScaled(bytes) ?: error("Некорректное изображение")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
        }
    }

    /** Аватар текущего аккаунта; null, если фото ещё не выбиралось. */
    fun avatarBitmap(): Bitmap? {
        val file = avatarFile() ?: return null
        if (!file.exists()) return null
        return runCatching { decodeScaled(file.readBytes()) }.getOrNull()
    }

    /** Декодирует изображение с даунсемплингом не больше [MAX_AVATAR_PX] px по длинной стороне. */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // Наименьшая степень двойки, при которой длинная сторона <= MAX_AVATAR_PX.
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_AVATAR_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Вход по email. */
    suspend fun signIn(email: String, password: String): Result<Unit> {
        val normalized = normalizeEmail(email)
        val result = if (firebaseAuth != null) {
            runCatching {
                firebaseAuth!!.signInWithEmailAndPassword(normalized, password).await()
                rememberLast(normalized, firebaseAuth!!.currentUser?.displayName.orEmpty())
            }.map { }
        } else {
            localSignIn(normalized, password)
        }
        if (result.isSuccess) profileSetupNeeded = false
        return result
    }

    /** Восстановление пароля (только Firebase; в локальном режиме недоступно). */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val firebase = firebaseAuth
            ?: return Result.failure(IllegalStateException(ERROR_OFFLINE))
        return runCatching {
            firebase.sendPasswordResetEmail(normalizeEmail(email)).await()
        }.map { }
    }

    /** Вход через Google. Требует настроенной Firebase-консоли. */
    suspend fun signInWithGoogle(): Result<Unit> {
        firebaseAuth ?: return Result.failure(IllegalStateException(ERROR_PROVIDER_NOT_CONFIGURED))
        // Полный поток требует GoogleSignInAccount с веб-клиентом (см. README, раздел Firebase).
        return Result.failure(NotImplementedError("Настройте Firebase и клиент Google (README)"))
    }

    /**
     * Вход через VK ID. Требует зарегистрированного приложения VK ID
     * (client_id и redirect URI) — см. README, раздел «Вход через ВКонтакте».
     */
    suspend fun signInWithVk(): Result<Unit> =
        Result.failure(IllegalStateException(ERROR_VK_NOT_CONFIGURED))

    fun signOut() {
        profileSetupNeeded = false
        firebaseAuth?.signOut()
        if (firebaseAuth == null) {
            prefs.edit().remove(KEY_SESSION_EMAIL).apply()
            _state.value = AuthState.SignedOut
        }
    }

    // ---- Локальный режим ----

    private fun localRegister(email: String, password: String, fullName: String): Result<Unit> {
        val validation = validate(email, password)
        if (validation != null) return Result.failure(IllegalArgumentException(validation))
        if (prefs.getString(userKey(email), null) != null) {
            return Result.failure(IllegalArgumentException("Пользователь уже зарегистрирован"))
        }
        val editor = prefs.edit()
            .putString(userKey(email), PasswordHasher.hash(password))
            .putString(KEY_SESSION_EMAIL, email)
        if (fullName.isNotBlank()) {
            editor.putString(profileKey(email), fullName.trim())
        }
        editor.apply()
        rememberLast(email, fullName.trim())
        _state.value = AuthState.SignedIn(email, PROVIDER_LOCAL)
        return Result.success(Unit)
    }

    private fun localSignIn(email: String, password: String): Result<Unit> {
        val stored = prefs.getString(userKey(email), null)
            ?: return Result.failure(IllegalArgumentException("Пользователь не найден"))
        if (!PasswordHasher.verify(password, stored)) {
            return Result.failure(IllegalArgumentException("Неверный пароль"))
        }
        prefs.edit().putString(KEY_SESSION_EMAIL, email).apply()
        rememberLast(email, prefs.getString(profileKey(email), null).orEmpty())
        _state.value = AuthState.SignedIn(email, PROVIDER_LOCAL)
        return Result.success(Unit)
    }

    // ---- Сохранённые аккаунты ----

    /**
     * Запоминает последний использованный аккаунт. Ключи last_* НЕ удаляются
     * при выходе: экран входа после logout показывает сохранённых пользователей.
     */
    private fun rememberLast(email: String, fullName: String) {
        prefs.edit()
            .putString(KEY_LAST_EMAIL, email)
            .putString(KEY_LAST_PROFILE, fullName)
            .apply()
    }

    /** Последний использованный аккаунт (для предзаполнения экрана входа). */
    fun lastAccount(): LocalAccount? {
        val email = prefs.getString(KEY_LAST_EMAIL, null) ?: return null
        return LocalAccount(email, prefs.getString(KEY_LAST_PROFILE, null).orEmpty())
    }

    /**
     * Зарегистрированные локально аккаунты (резервный режим без Firebase).
     * В Firebase-режиме список пуст: учётные записи хранятся на сервере.
     */
    fun registeredAccounts(): List<LocalAccount> {
        if (firebaseAuth != null) return emptyList()
        return prefs.all.keys
            .filter { it.startsWith("user:") }
            .map { key ->
                val email = key.removePrefix("user:")
                LocalAccount(email, prefs.getString(profileKey(email), null).orEmpty())
            }
            .sortedBy { it.email }
    }

    private fun validate(email: String, password: String): String? = when {
        !EMAIL_REGEX.matches(email) -> "Введите корректный email"
        password.length < MIN_PASSWORD_LENGTH -> "Пароль не короче $MIN_PASSWORD_LENGTH символов"
        else -> null
    }

    private fun userKey(email: String) = "user:$email"

    private fun profileKey(email: String) = "profile:$email"

    private fun normalizeEmail(email: String) = email.trim().lowercase()

    private companion object {
        const val KEY_SESSION_EMAIL = "session_email"
        const val KEY_LAST_EMAIL = "last_email"
        const val KEY_LAST_PROFILE = "last_profile"
        const val PROVIDER_LOCAL = "local"
        const val PROVIDER_FIREBASE = "firebase"
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_AVATAR_PX = 512
        const val EMAIL_REGEX_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        const val ERROR_OFFLINE =
            "Восстановление пароля недоступно в локальном режиме: настройте Firebase (см. README)"
        const val ERROR_PROVIDER_NOT_CONFIGURED =
            "Социальный вход требует настройки Firebase (см. README)"
        const val ERROR_VK_NOT_CONFIGURED =
            "Вход через ВКонтакте требует регистрации приложения VK ID (см. README)"
        val EMAIL_REGEX = Regex(EMAIL_REGEX_PATTERN)
    }
}
