package com.example.dosediary.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.example.dosediary.data.local.dao.MedicationDao
import com.example.dosediary.data.local.dao.SymptomDao
import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationReminderEntity
import com.example.dosediary.data.local.entity.SymptomEntity

@Database(
    entities = [MedicationEntity::class, MedicationReminderEntity::class, SymptomEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.Migration1To2::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun symptomDao(): SymptomDao

    /**
     * v1 -> v2: `brandName` -> `commercialName`, `genericName` -> `activeSubstance`, and a new nullable
     * `customUserNickname` column (added automatically). Existing rows and reminders are preserved.
     */
    @RenameColumn.Entries(
        RenameColumn(tableName = "medications", fromColumnName = "brandName", toColumnName = "commercialName"),
        RenameColumn(tableName = "medications", fromColumnName = "genericName", toColumnName = "activeSubstance"),
    )
    class Migration1To2 : AutoMigrationSpec

    companion object {
        const val NAME = "dosediary.db"
    }
}
