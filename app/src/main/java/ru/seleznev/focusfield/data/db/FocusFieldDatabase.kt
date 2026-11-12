package ru.seleznev.focusfield.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.seleznev.focusfield.data.db.dao.BlockedItemDao
import ru.seleznev.focusfield.data.db.dao.FocusSessionDao
import ru.seleznev.focusfield.data.db.dao.TaskDao
import ru.seleznev.focusfield.data.db.entity.BlockedItemEntity
import ru.seleznev.focusfield.data.db.entity.FocusSampleEntity
import ru.seleznev.focusfield.data.db.entity.FocusSessionEntity
import ru.seleznev.focusfield.data.db.entity.TaskEntity

/**
 * Локальная база данных приложения (SQLite через Room).
 * Схема экспортируется в app/schemas (см. ksp { arg("room.schemaLocation", ...) }).
 */
@Database(
    entities = [
        TaskEntity::class,
        FocusSessionEntity::class,
        FocusSampleEntity::class,
        BlockedItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FocusFieldDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun blockedItemDao(): BlockedItemDao

    companion object {
        fun build(context: Context): FocusFieldDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FocusFieldDatabase::class.java,
                "focusfield.db"
            ).build()
    }
}
