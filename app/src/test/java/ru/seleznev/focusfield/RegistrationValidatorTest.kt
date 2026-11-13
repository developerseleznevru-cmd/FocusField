package ru.seleznev.focusfield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.ui.auth.RegistrationValidator

class RegistrationValidatorTest {

    // ---- Имя / Фамилия / Отчество ----

    @Test
    fun `name with russian letters and capital first is valid`() {
        assertTrue(RegistrationValidator.isNameValid("Иван"))
        assertTrue(RegistrationValidator.isNameValid("Ёлкин"))
        assertTrue(RegistrationValidator.isNameValid("Пётр"))
    }

    @Test
    fun `name shorter than four letters is invalid`() {
        assertFalse(RegistrationValidator.isNameValid("Ива"))
        assertFalse(RegistrationValidator.isNameValid(""))
    }

    @Test
    fun `name starting with lowercase is invalid`() {
        assertFalse(RegistrationValidator.isNameValid("иван"))
        assertFalse(RegistrationValidator.isNameValid("ёлкин"))
    }

    @Test
    fun `name with non-cyrillic characters is invalid`() {
        assertFalse(RegistrationValidator.isNameValid("Ivan"))
        assertFalse(RegistrationValidator.isNameValid("Иван1"))
        assertFalse(RegistrationValidator.isNameValid("Ива н"))
        assertFalse(RegistrationValidator.isNameValid("Иван-Иванов"))
    }

    // ---- Отчество (необязательное) ----

    @Test
    fun `patronymic is optional but validated when filled`() {
        assertTrue(RegistrationValidator.isPatronymicValid(""))
        assertTrue(RegistrationValidator.isPatronymicValid("Иванович"))
        assertFalse(RegistrationValidator.isPatronymicValid("иванович"))
        assertFalse(RegistrationValidator.isPatronymicValid("Иванович1"))
    }

    // ---- Email ----

    @Test
    fun `email with latin letters and at sign is valid`() {
        assertTrue(RegistrationValidator.isEmailValid("user@mail.ru"))
        assertTrue(RegistrationValidator.isEmailValid("a@b"))
        assertTrue(RegistrationValidator.isEmailValid("user.1+tag@mail-domain.ru"))
    }

    @Test
    fun `email without at sign is invalid`() {
        assertFalse(RegistrationValidator.isEmailValid("usermail.ru"))
        assertFalse(RegistrationValidator.isEmailValid(""))
    }

    @Test
    fun `email with cyrillic letters is invalid`() {
        assertFalse(RegistrationValidator.isEmailValid("юзер@mail.ru"))
        assertFalse(RegistrationValidator.isEmailValid("user@мейл.ру"))
    }

    // ---- Пароль ----

    @Test
    fun `password with capital latin letter and special char is valid`() {
        assertTrue(RegistrationValidator.isPasswordValid("Abc123!"))
        assertTrue(RegistrationValidator.isPasswordValid("Qwerty?"))
        assertTrue(RegistrationValidator.isPasswordValid("A1!"))
    }

    @Test
    fun `password starting with lowercase letter is invalid`() {
        assertFalse(RegistrationValidator.isPasswordValid("abc123!"))
    }

    @Test
    fun `password without special character is invalid`() {
        assertFalse(RegistrationValidator.isPasswordValid("Abcdef1"))
    }

    @Test
    fun `password with cyrillic letters is invalid`() {
        assertFalse(RegistrationValidator.isPasswordValid("Абв123!"))
    }

    @Test
    fun `password without letters is invalid`() {
        assertFalse(RegistrationValidator.isPasswordValid("1234!"))
        assertFalse(RegistrationValidator.isPasswordValid(""))
    }
}
