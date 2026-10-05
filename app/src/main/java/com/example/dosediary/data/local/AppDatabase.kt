package com.example.dosediary.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.example.dosediary.data.local.dao.MedicationDao
import com.example.dosediary.data.local.dao.SymptomDao
import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationIntakeEntity
import com.example.dosediary.data.local.entity.SymptomEntity

@Database(
    entities = [MedicationEntity::class, MedicationIntakeEntity::class, SymptomEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AppDatabase.Migration1To2::class),
        // v2 -> v3: new `tags` column on symptoms (defaults to '' for existing rows).
        AutoMigration(from = 2, to = 3),
        // v3 -> v4 changes data (existing reminders move to their own table), so it is the manual
        // MIGRATION_3_4 registered on the database builder (see DatabaseModule).
        // v4 -> v5 also rebuilds tables and moves data (reminders + dose become intakes): MIGRATION_4_5.
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
