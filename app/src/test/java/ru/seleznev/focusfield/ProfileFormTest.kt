package ru.seleznev.focusfield

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.seleznev.focusfield.ui.profile.ProfileForm

/** Тесты разбора/сборки ФИО на экране редактирования профиля. */
class ProfileFormTest {

    @Test
    fun `parse splits full name into parts`() {
        val parts = ProfileForm.parse("Иванов Иван Иванович")
        assertEquals("Иванов", parts.lastName)
        assertEquals("Иван", parts.firstName)
        assertEquals("Иванович", parts.patronymic)
    }

    @Test
    fun `parse handles missing patronymic`() {
        val parts = ProfileForm.parse("Иванов Иван")
        assertEquals("Иванов", parts.lastName)
        assertEquals("Иван", parts.firstName)
        assertEquals("", parts.patronymic)
    }

    @Test
    fun `parse empty string gives empty parts`() {
        val parts = ProfileForm.parse("   ")
        assertEquals("", parts.lastName)
        assertEquals("", parts.firstName)
        assertEquals("", parts.patronymic)
    }

    @Test
    fun `parse tolerates extra whitespace`() {
        val parts = ProfileForm.parse("  Иванов   Иван  ")
        assertEquals("Иванов", parts.lastName)
        assertEquals("Иван", parts.firstName)
        assertEquals("", parts.patronymic)
    }

    @Test
    fun `format joins non-empty parts`() {
        assertEquals(
            "Иванов Иван Иванович",
            ProfileForm.format("Иванов", "Иван", "Иванович")
        )
    }

    @Test
    fun `format skips empty patronymic`() {
        assertEquals("Иванов Иван", ProfileForm.format("Иванов", "Иван", "  "))
    }

    @Test
    fun `format trims parts`() {
        assertEquals("Иванов Иван", ProfileForm.format(" Иванов ", " Иван ", ""))
    }

    @Test
    fun `round trip parse then format preserves name`() {
        val original = "Иванов Иван Иванович"
        val parts = ProfileForm.parse(original)
        assertEquals(original, ProfileForm.format(parts.lastName, parts.firstName, parts.patronymic))
    }
}
