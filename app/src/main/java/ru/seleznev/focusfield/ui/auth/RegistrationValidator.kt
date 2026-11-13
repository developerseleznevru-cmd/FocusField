package ru.seleznev.focusfield.ui.auth

/**
 * Правила валидации формы регистрации (ТЗ, раздел «Требования к валидации»):
 *
 * - Имя, Фамилия, Отчество: только русские буквы, первая — заглавная, не менее 4 букв;
 *   Отчество необязательно для заполнения.
 * - Email: обязателен знак «@», буквы только латинские.
 * - Пароль: буквы только латинские, первая буква — заглавная,
 *   обязательно наличие специального знака («!», «?» и т.д.).
 * - Подтверждение пароля должно совпадать с паролем (проверяется во фрагменте).
 */
object RegistrationValidator {

    private const val MIN_NAME_LENGTH = 4

    /** Имя/фамилия: русские буквы, первая заглавная, не менее [MIN_NAME_LENGTH] букв. */
    fun isNameValid(value: String): Boolean =
        value.length >= MIN_NAME_LENGTH &&
            value.first().isCyrillicUppercase() &&
            value.all { it.isCyrillic() }

    /** Отчество: необязательное поле; непустое значение проверяется по правилам имени. */
    fun isPatronymicValid(value: String): Boolean =
        value.isEmpty() || isNameValid(value)

    /** Email: знак «@» обязателен, буквы только латинские. */
    fun isEmailValid(value: String): Boolean =
        value.contains('@') &&
            value.none { it.isLetter() && !it.isLatinLetter() }

    /** Пароль: латинские буквы, первая буква заглавная, есть специальный знак. */
    fun isPasswordValid(value: String): Boolean {
        if (value.any { it.isLetter() && !it.isLatinLetter() }) return false
        val letters = value.filter { it.isLatinLetter() }
        if (letters.isEmpty()) return false
        if (letters.first() !in 'A'..'Z') return false
        return value.any { !it.isLetterOrDigit() }
    }

    private fun Char.isCyrillic(): Boolean =
        this in 'А'..'я' || this == 'Ё' || this == 'ё'

    private fun Char.isCyrillicUppercase(): Boolean =
        this in 'А'..'Я' || this == 'Ё'

    private fun Char.isLatinLetter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z'
}
