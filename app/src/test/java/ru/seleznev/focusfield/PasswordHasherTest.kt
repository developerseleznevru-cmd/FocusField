package ru.seleznev.focusfield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.data.crypto.PasswordHasher

class PasswordHasherTest {

    @Test
    fun `hash and verify roundtrip`() {
        val stored = PasswordHasher.hash("пароль123")
        assertTrue(PasswordHasher.verify("пароль123", stored))
    }

    @Test
    fun `wrong password fails`() {
        val stored = PasswordHasher.hash("correct-horse")
        assertFalse(PasswordHasher.verify("wrong-horse", stored))
    }

    @Test
    fun `same password produces different hashes`() {
        val first = PasswordHasher.hash("password")
        val second = PasswordHasher.hash("password")
        assertNotEquals(first, second)
    }

    @Test
    fun `malformed stored value fails safely`() {
        assertFalse(PasswordHasher.verify("password", "garbage"))
    }
}
