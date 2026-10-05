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
    /** Days between intake days; 1 means every day. */
    @ColumnInfo(defaultValue = "1") val frequencyDays: Int,
    /** Epoch day the interval is counted from; only meaningful when [frequencyDays] > 1. */
    @ColumnInfo(defaultValue = "0") val frequencyStartEpochDay: Long,
    val savedAtMillis: Long,
)

/** One planned intake (time, dose, notification) of a medication. Removed together with its medication. */
@Entity(
    tableName = "medication_intakes",
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
data class MedicationIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: String,
    val hour: Int,
    val minute: Int,
    val doseAmount: String?,
    @ColumnInfo(defaultValue = "1") val notify: Boolean,
)

/** A medication together with its intakes. */
data class MedicationWithIntakes(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val intakes: List<MedicationIntakeEntity>,
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
