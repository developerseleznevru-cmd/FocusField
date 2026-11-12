package ru.seleznev.focusfield.util

/**
 * Извлечение домена из строки адресной строки браузера и сопоставление
 * с элементами списка блокировки. Чистая логика без Android-зависимостей —
 * покрывается юнит-тестами.
 */
object DomainMatcher {

    /**
     * Извлекает хост из произвольной строки URL/адреса.
     * Поддерживает как полный URL («https://vk.com/feed»), так и запись
     * адресной строки без схемы («vk.com»).
     * Возвращает null для локальных адресов, IP-адресов и прочего, что не является доменом.
     */
    fun extractHost(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null

        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) {
            s = s.substring(schemeIdx + 3)
        }
        // убрать userinfo вида user@host
        if (s.contains('@')) s = s.substringAfterLast('@')
        // убрать путь, запрос, фрагмент
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        // убрать порт
        s = s.substringBefore(':')
        // убираем ведущие точки
        s = s.trimStart('.')
        if (s.isEmpty()) return null
        if (!s.contains('.')) return null // «chrome://settings», localhost и т.п.
        return if (s.startsWith("www.")) s.substring(4) else s
    }

    /** Совпадает ли [host] с заблокированным [domain] (включая поддомены). */
    fun matches(host: String, domain: String): Boolean {
        val d = domain.trim().lowercase().let { if (it.startsWith("www.")) it.substring(4) else it }
        if (d.isEmpty()) return false
        return host == d || host.endsWith(".$d")
    }
}
