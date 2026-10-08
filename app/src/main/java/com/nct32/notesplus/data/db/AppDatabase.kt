package com.nct32.notesplus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's Room database. Holds notes, reminders, and explicitly created folders.
 *
 * The database file lives in the app's **internal** storage (the app's private directory), so
 * data survives app kills and phone restarts and requires **no** storage permissions.
 */
@Database(
    entities = [NoteEntity::class, ReminderEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DB_NAME = "notes_plus.db"

        private var instance: AppDatabase? = null

        /** The shared database instance. */
        val db: AppDatabase
            get() = instance ?: error(
                "AppDatabase not initialized. Call AppDatabase.init(context) first " +
                    "(done in MainActivity.onCreate)."
            )

        /** Builds (or returns the existing) database from the application context. Idempotent. */
        fun init(context: Context) {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build()
            }
        }
    }
}
