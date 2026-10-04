package com.example.dosediary.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val commercialName: String,
    val activeSubstance: String?,
    val manufacturer: String?,
    val customUserNickname: String?,
    val purpose: String?,
    val route: String?,
    val reminderHour: Int?,
    val reminderMinute: Int?,
    val savedAtMillis: Long,
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
)

/** Projection of a symptom joined with the name of its (optional) medication. */
data class SymptomWithMedication(
    val id: Long,
    val medicationId: String?,
    @ColumnInfo(name = "medicationName") val medicationName: String?,
    val severity: Int,
    val notes: String,
    val loggedAtMillis: Long,
)
