package ru.seleznev.focusfield.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.seleznev.focusfield.data.db.entity.BlockedItemEntity

@Dao
interface BlockedItemDao {

    @Query("SELECT * FROM blocked_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockedItemEntity>>

    @Query("SELECT * FROM blocked_items WHERE enabled = 1")
    fun observeEnabled(): Flow<List<BlockedItemEntity>>

    @Query("SELECT * FROM blocked_items WHERE enabled = 1")
    suspend fun enabledItems(): List<BlockedItemEntity>

    @Insert
    suspend fun insert(item: BlockedItemEntity): Long

    @Update
    suspend fun update(item: BlockedItemEntity)

    @Delete
    suspend fun delete(item: BlockedItemEntity)

    @Query("UPDATE blocked_items SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_items WHERE packageName = :pkg AND enabled = 1)")
    suspend fun isAppBlocked(pkg: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_items WHERE packageName = :pkg)")
    suspend fun appExists(pkg: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_items WHERE domain = :domain)")
    suspend fun domainExists(domain: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_items WHERE domain = :domain AND enabled = 1)")
    suspend fun isDomainBlocked(domain: String): Boolean
}
