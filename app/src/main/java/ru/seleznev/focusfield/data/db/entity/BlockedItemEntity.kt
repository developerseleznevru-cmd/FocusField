package ru.seleznev.focusfield.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Элемент списка блокировки: приложение или сайт (домен).
 * Доступ ограничивается, когда уровень концентрации низкий.
 */
@Entity(tableName = "blocked_items")
data class BlockedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** TYPE_APP или TYPE_SITE. */
    val type: String,
    /** Имя пакета приложения (для TYPE_APP). */
    val packageName: String? = null,
    /** Домен сайта без «www» (для TYPE_SITE), например vk.com. */
    val domain: String? = null,
    /** Отображаемое имя (название приложения или домен). */
    val label: String,
    val enabled: Boolean = true,
    val addedAt: Long
) {
    companion object {
        const val TYPE_APP = "APP"
        const val TYPE_SITE = "SITE"
    }
}
