package ru.seleznev.focusfield.ui.profile

/**
 * Разбор и сборка строки профиля «Фамилия Имя Отчество».
 * Используется для предзаполнения формы редактирования профиля
 * и для сохранения отредактированных полей. Проверяется юнит-тестами
 * (см. ProfileFormTest).
 */
object ProfileForm {

    /** Части ФИО: отчество может быть пустым. */
    data class Parts(
        val lastName: String,
        val firstName: String,
        val patronymic: String
    )

    /** Разбирает «Фамилия Имя Отчество» на части; лишние слова уходят в отчество. */
    fun parse(fullName: String): Parts {
        val parts = fullName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return Parts(
            lastName = parts.getOrNull(0).orEmpty(),
            firstName = parts.getOrNull(1).orEmpty(),
            patronymic = parts.drop(2).joinToString(" ")
        )
    }

    /** Собирает части в «Фамилия Имя Отчество», пропуская пустые. */
    fun format(lastName: String, firstName: String, patronymic: String): String =
        listOf(lastName.trim(), firstName.trim(), patronymic.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
