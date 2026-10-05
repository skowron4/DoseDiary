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

/**
 * v4 -> v5: intakes (time + dose + notification) and a medication frequency.
 *
 * - `medication_reminders` (times only) becomes `medication_intakes`, which also carries the dose and
 *   a `notify` flag. Every existing reminder is copied as a notifying intake that inherits the
 *   medication's old single `doseAmount`, so reminders keep firing at the same times.
 * - A medication that had a dose but no reminder keeps its dose as one non-notifying intake at 08:00
 *   (the time is a placeholder the user can change; it never alerts).
 * - `medications` loses `doseAmount`/`intervalHours` (the interval in hours has no equivalent in the
 *   new per-intake model) and gains `frequencyDays` (default 1 = every day) and
 *   `frequencyStartEpochDay` (default 0).
 *
 * The `symptoms` table is not rebuilt: the optional medication link already exists as
 * `symptoms.medicationId`.
 *
 * SQLite cannot drop columns portably (minSdk 26), so `medications` is rebuilt, and dropping a table
 * that other tables reference is only safe with foreign keys off. Rather than rely on that setting, the
 * data that could be affected (the new intakes, and the symptom -> medication links that
 * `ON DELETE SET NULL` would clear) is parked in temp tables while `medications` is replaced, and put
 * back afterwards. The result is the same whether or not foreign keys are enforced during migration.
 * `MigrationTest` runs this on a real database and checks the data and the schema.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Park everything that depends on `medications` (and the data derived from the old columns).
        db.execSQL(
            "CREATE TEMP TABLE `_symptom_links` AS " +
                "SELECT `id`, `medicationId` FROM `symptoms` WHERE `medicationId` IS NOT NULL",
        )
        db.execSQL(
            """
            CREATE TEMP TABLE `_intakes` AS
            SELECT r.`medicationId` AS `medicationId`, r.`hour` AS `hour`, r.`minute` AS `minute`,
                   NULLIF(TRIM(m.`doseAmount`), '') AS `doseAmount`, 1 AS `notify`
            FROM `medication_reminders` r
            JOIN `medications` m ON m.`id` = r.`medicationId`
            UNION ALL
            SELECT m.`id`, 8, 0, TRIM(m.`doseAmount`), 0
            FROM `medications` m
            WHERE TRIM(COALESCE(m.`doseAmount`, '')) <> ''
              AND NOT EXISTS (SELECT 1 FROM `medication_reminders` r WHERE r.`medicationId` = m.`id`)
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `medication_reminders`")

        // 2. Rebuild `medications` without the old columns and with the frequency columns.
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
                `frequencyDays` INTEGER NOT NULL DEFAULT 1,
                `frequencyStartEpochDay` INTEGER NOT NULL DEFAULT 0,
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

        // 3. The new intakes table can now reference the new `medications` table.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `medication_intakes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `medicationId` TEXT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `doseAmount` TEXT,
                `notify` INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_intakes_medicationId_hour_minute` " +
                "ON `medication_intakes` (`medicationId`, `hour`, `minute`)",
        )
        db.execSQL(
            """
            INSERT INTO `medication_intakes` (`medicationId`, `hour`, `minute`, `doseAmount`, `notify`)
            SELECT `medicationId`, `hour`, `minute`, `doseAmount`, `notify` FROM temp.`_intakes`
            """.trimIndent(),
        )

        // 4. Put the symptom links back (a no-op when foreign keys were off) and clean up.
        db.execSQL(
            """
            UPDATE `symptoms`
            SET `medicationId` = (SELECT l.`medicationId` FROM temp.`_symptom_links` l WHERE l.`id` = `symptoms`.`id`)
            WHERE `id` IN (SELECT `id` FROM temp.`_symptom_links`)
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE temp.`_intakes`")
        db.execSQL("DROP TABLE temp.`_symptom_links`")
    }
}