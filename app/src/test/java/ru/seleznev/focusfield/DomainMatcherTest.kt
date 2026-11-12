package ru.seleznev.focusfield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.util.DomainMatcher

class DomainMatcherTest {

    @Test
    fun `extracts host from full url`() {
        assertEquals("vk.com", DomainMatcher.extractHost("https://www.vk.com/feed?q=1#top"))
    }

    @Test
    fun `extracts host from address bar without scheme`() {
        assertEquals("vk.com", DomainMatcher.extractHost("vk.com:8080/path"))
    }

    @Test
    fun `uppercase and www are normalized`() {
        assertEquals("youtube.com", DomainMatcher.extractHost("WWW.YouTube.com"))
    }

    @Test
    fun `non domain input returns null`() {
        assertNull(DomainMatcher.extractHost("chrome://settings"))
        assertNull(DomainMatcher.extractHost(""))
        assertNull(DomainMatcher.extractHost("localhost"))
    }

    @Test
    fun `subdomain matches blocked domain`() {
        assertTrue(DomainMatcher.matches("m.vk.com", "vk.com"))
        assertTrue(DomainMatcher.matches("vk.com", "vk.com"))
    }

    @Test
    fun `similar but different domain does not match`() {
        assertFalse(DomainMatcher.matches("notvk.com", "vk.com"))
        assertFalse(DomainMatcher.matches("evk.com", "vk.com"))
    }
}
