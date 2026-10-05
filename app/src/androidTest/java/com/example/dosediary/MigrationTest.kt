package com.example.dosediary

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dosediary.data.local.AppDatabase
import com.example.dosediary.data.local.MIGRATION_3_4
import com.example.dosediary.data.local.MIGRATION_4_5
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs MIGRATION_4_5 on a real SQLite database built from the exported v4 schema, then lets Room
 * validate the result against the exported v5 schema (columns, defaults, foreign keys, indices).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun SupportSQLiteDatabase.seedV4() {
        // Three medications: with reminders + dose, dose only, nothing (+ a blank dose with a reminder).
        execSQL(
            """INSERT INTO medications (id, commercialName, activeSubstance, manufacturer, customUserNickname,
                purpose, route, doseAmount, intervalHours, savedAtMillis) VALUES
                ('advil', 'Advil', 'Ibuprofen', 'Haleon', 'Morning pill', NULL, NULL, '2 pills', 8, 1000),
                ('dose-only', 'Aspirin', NULL, NULL, NULL, NULL, NULL, ' 1 pill ', 12, 2000),
                ('plain', 'Plain', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 3000),
                ('blank-dose', 'Blank', NULL, NULL, NULL, NULL, NULL, '   ', NULL, 4000)""",
        )
        execSQL(
            """INSERT INTO medication_reminders (medicationId, hour, minute) VALUES
                ('advil', 8, 0), ('advil', 20, 30), ('blank-dose', 9, 15)""",
        )
        execSQL(
            """INSERT INTO symptoms (medicationId, severity, notes, loggedAtMillis, tags) VALUES
                ('advil', 7, 'linked', 10, 'HEADACHE,NAUSEA'),
                (NULL, 3, 'general', 20, ''),
                ('plain', 5, 'other link', 30, 'COUGH')""",
        )
    }

    private fun SupportSQLiteDatabase.intakes(medicationId: String): List<List<Any?>> =
        query("SELECT hour, minute, doseAmount, notify FROM medication_intakes WHERE medicationId = '$medicationId' ORDER BY hour, minute")
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(listOf(c.getInt(0), c.getInt(1), c.getString(2), c.getInt(3)))
                    }
                }
            }

    private fun migrated(): SupportSQLiteDatabase {
        helper.createDatabase(DB_NAME, 4).apply {
            seedV4()
            close()
        }
        // validateDroppedTables = true: medication_reminders must be gone, nothing unexpected left.
        return helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)
    }

    @Test
    fun remindersBecomeNotifyingIntakesThatInheritTheOldDose() {
        val db = migrated()

        assertEquals(
            listOf(listOf(8, 0, "2 pills", 1), listOf(20, 30, "2 pills", 1)),
            db.intakes("advil"),
        )
    }

    @Test
    fun blankOldDoseBecomesNoDose() {
        val db = migrated()

        assertEquals(listOf(listOf(9, 15, null, 1)), db.intakes("blank-dose"))
    }

    @Test
    fun doseOnlyMedicationKeepsItAsSilentPlaceholderIntake() {
        val db = migrated()

        assertEquals(listOf(listOf(8, 0, "1 pill", 0)), db.intakes("dose-only"))
    }

    @Test
    fun medicationWithNeitherDoseNorReminderGetsNoIntake() {
        val db = migrated()

        assertTrue(db.intakes("plain").isEmpty())
    }

    @Test
    fun medicationsKeepTheirDataAndStartEveryDay() {
        val db = migrated()

        db.query("SELECT commercialName, customUserNickname, savedAtMillis, frequencyDays, frequencyStartEpochDay FROM medications WHERE id = 'advil'")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Advil", c.getString(0))
                assertEquals("Morning pill", c.getString(1))
                assertEquals(1000L, c.getLong(2))
                assertEquals(1, c.getInt(3))
                assertEquals(0L, c.getLong(4))
            }
        db.query("SELECT COUNT(*) FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
        }
    }

    @Test
    fun symptomsKeepTheirMedicationLinkTagsAndNotes() {
        val db = migrated()

        db.query("SELECT medicationId, severity, notes, tags FROM symptoms ORDER BY loggedAtMillis").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("advil", c.getString(0))
            assertEquals("linked", c.getString(2))
            assertEquals("HEADACHE,NAUSEA", c.getString(3))
            assertTrue(c.moveToNext())
            assertTrue("a general symptom stays general", c.isNull(0))
            assertTrue(c.moveToNext())
            assertEquals("plain", c.getString(0))
            assertEquals("COUGH", c.getString(3))
            assertFalse(c.moveToNext())
        }
    }

    @Test
    fun foreignKeysStillWorkAfterTheRebuild() {
        val db = migrated()
        db.execSQL("PRAGMA foreign_keys = ON")

        db.execSQL("DELETE FROM medications WHERE id = 'advil'")

        // Intakes are removed with their medication ...
        assertTrue(db.intakes("advil").isEmpty())
        // ... and the symptom history stays, just no longer linked.
        db.query("SELECT medicationId, notes FROM symptoms WHERE notes = 'linked'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
    }

    @Test
    fun roomOpensTheMigratedDatabaseAndReadsItThroughTheDao() = runBlocking {
        helper.createDatabase(DB_NAME, 4).apply {
            seedV4()
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            val advil = room.medicationDao().getById("advil")!!

            assertEquals(1, advil.medication.frequencyDays)
            assertEquals(listOf(8 to 0, 20 to 30), advil.intakes.map { it.hour to it.minute }.sortedBy { it.first })
            assertTrue(advil.intakes.all { it.notify })
            assertNull(room.medicationDao().getById("nope"))
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
