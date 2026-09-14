package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FamilyDao
import com.example.data.model.CalendarNote
import com.example.data.model.CallRecord
import com.example.data.model.FamilyEvent
import com.example.data.model.FamilyMemory
import com.example.data.model.HomeVisitRecord
import com.example.data.model.SafeArrivalLog

@Database(
    entities = [
        FamilyEvent::class,
        CalendarNote::class,
        HomeVisitRecord::class,
        CallRecord::class,
        FamilyMemory::class,
        SafeArrivalLog::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE family_events ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE family_events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE calendar_notes ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE calendar_notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE home_visits ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE home_visits ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE call_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE family_memories ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE family_memories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE safe_arrival_logs ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE safe_arrival_logs ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN audioUri TEXT")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE call_records ADD COLUMN audioDurationSeconds INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "family_space_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
