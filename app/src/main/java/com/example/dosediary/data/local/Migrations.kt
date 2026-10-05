package com.example.dosediary.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4: dosage and multiple reminders per medication.
 *
 * - `medications` gains `doseAmount` and `intervalHours`, and loses the single
 *   `reminderHour`/`reminderMinute` pair.
 * - Reminder times move to the new `medication_reminders` table (one row per time). Every existing
 *   single reminder is copied over, so already-scheduled reminders keep their time.
 *
 * SQLite cannot drop columns portably (minSdk 26), so `medications` is rebuilt. Room runs migrations
 * with foreign keys switched off, so dropping the old table does not trigger the `ON DELETE SET NULL`
 * rule of `symptoms.medicationId`; the symptom history is untouched.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medication_reminders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `medicationId` TEXT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_reminders_medicationId_hour_minute` " +
                "ON `medication_reminders` (`medicationId`, `hour`, `minute`)",
        )
        db.execSQL(
            """
            INSERT INTO `medication_reminders` (`medicationId`, `hour`, `minute`)
            SELECT `id`, `reminderHour`, `reminderMinute` FROM `medications`
            WHERE `reminderHour` IS NOT NULL AND `reminderMinute` IS NOT NULL
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medications_new` (
                `id` TEXT NOT NULL,
                `commercialName` TEXT NOT NULL,
                `activeSubstance` TEXT,
                `manufacturer` TEXT,
                `customUserNickname` TEXT,
                `purpose` TEXT,
                `route` TEXT,
                `doseAmount` TEXT,
                `intervalHours` INTEGER,
                `savedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `medications_new` (
                `id`, `commercialName`, `activeSubstance`, `manufacturer`, `customUserNickname`,
                `purpose`, `route`, `savedAtMillis`
            )
            SELECT
                `id`, `commercialName`, `activeSubstance`, `manufacturer`, `customUserNickname`,
                `purpose`, `route`, `savedAtMillis`
            FROM `medications`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `medications`")
        db.execSQL("ALTER TABLE `medications_new` RENAME TO `medications`")
    }
}
