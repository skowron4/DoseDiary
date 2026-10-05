package com.example.dosediary.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DRAFT (Phase 5): not yet listed in `AppDatabase.entities`, so Room ignores it and the schema is
 * unchanged. To activate:
 *  1. add it to `@Database(entities = [...])`, bump `version` to 4 and add `AutoMigration(from = 3, to = 4)`
 *     (adding a table is a supported auto-migration; commit the generated `4.json` schema),
 *  2. add a `MedicationCourseDao` (observe ordered by `startMillis`, insert, update end),
 *  3. implement `MedicationCourseRepository` + mappers and bind it in `appModule`.
 *
 * Mirrors `SymptomEntity`: deleting a medication keeps its history (`SET_NULL`), and the name is
 * stored as a snapshot because it can no longer be joined once the medication row is gone.
 */
@Entity(
    tableName = "medication_courses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("medicationId"), Index("startMillis")],
)
data class MedicationCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String?,
    val medicationName: String,
    val startMillis: Long,
    /** `null` while the course is ongoing. */
    val endMillis: Long?,
)
