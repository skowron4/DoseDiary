package com.example.dosediary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val commercialName: String,
    val activeSubstance: String?,
    val manufacturer: String?,
    val customUserNickname: String?,
    val purpose: String?,
    val route: String?,
    val doseAmount: String?,
    val intervalHours: Int?,
    val savedAtMillis: Long,
)

/** One daily reminder time of a medication. Removed together with its medication. */
@Entity(
    tableName = "medication_reminders",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicationId", "hour", "minute"], unique = true)],
)
data class MedicationReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String,
    val hour: Int,
    val minute: Int,
)

/** A medication together with its reminder times. */
data class MedicationWithReminders(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val reminders: List<MedicationReminderEntity>,
)

@Entity(
    tableName = "symptoms",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            // Keep the symptom history when a medication is removed.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("medicationId"), Index("loggedAtMillis")],
)
data class SymptomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String?,
    val severity: Int,
    val notes: String,
    val loggedAtMillis: Long,
    /** Comma-separated [com.example.dosediary.domain.model.SymptomTag] keys; empty when none. */
    @ColumnInfo(defaultValue = "") val tags: String = "",
)

/** Projection of a symptom joined with the name of its (optional) medication. */
data class SymptomWithMedication(
    val id: Long,
    val medicationId: String?,
    @ColumnInfo(name = "medicationName") val medicationName: String?,
    val severity: Int,
    val notes: String,
    val loggedAtMillis: Long,
    val tags: String,
)
