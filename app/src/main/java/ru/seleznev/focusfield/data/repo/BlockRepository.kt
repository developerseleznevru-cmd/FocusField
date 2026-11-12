package ru.seleznev.focusfield.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.seleznev.focusfield.data.db.dao.BlockedItemDao
import ru.seleznev.focusfield.data.db.entity.BlockedItemEntity
import ru.seleznev.focusfield.data.model.BlockedItem
import ru.seleznev.focusfield.util.DomainMatcher

/** Репозиторий списка блокировки приложений и сайтов. */
class BlockRepository(private val dao: BlockedItemDao) {

    fun observeItems(): Flow<List<BlockedItem>> =
        dao.observeAll().map { list -> list.map(BlockedItem::fromEntity) }

    /** Активные (включённые) элементы — их коллекционирует BlockingAccessibilityService. */
    fun observeEnabled(): Flow<List<BlockedItem>> =
        dao.observeEnabled().map { list -> list.map(BlockedItem::fromEntity) }

    /** Добавляет приложение. Возвращает false, если оно уже в списке. */
    suspend fun addApp(packageName: String, label: String): Boolean {
        if (dao.appExists(packageName)) return false
        dao.insert(
            BlockedItemEntity(
                type = BlockedItemEntity.TYPE_APP,
                packageName = packageName,
                label = label,
                addedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * Добавляет сайт по домену. Домен нормализуется («WWW.Vk.com/...» → «vk.com»).
     * Возвращает false, если домен некорректный или уже в списке.
     */
    suspend fun addSite(rawDomain: String): Boolean {
        val domain = DomainMatcher.extractHost(rawDomain) ?: return false
        if (dao.domainExists(domain)) return false
        dao.insert(
            BlockedItemEntity(
                type = BlockedItemEntity.TYPE_SITE,
                domain = domain,
                label = domain,
                addedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(item: BlockedItem) {
        dao.delete(
            BlockedItemEntity(
                id = item.id, type = item.type, packageName = item.packageName,
                domain = item.domain, label = item.label, enabled = item.enabled,
                addedAt = item.addedAt
            )
        )
    }
}
