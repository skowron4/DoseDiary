package com.example.dosediary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationReminderEntity
import com.example.dosediary.data.local.entity.MedicationWithReminders
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    /** Sorted by the name the user sees: nickname if set, otherwise the commercial name. */
    @Transaction
    @Query(
        """
        SELECT * FROM medications
        ORDER BY COALESCE(NULLIF(TRIM(customUserNickname), ''), commercialName) COLLATE NOCASE ASC
        """,
    )
    abstract fun observeAll(): Flow<List<MedicationWithReminders>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    abstract suspend fun getById(id: String): MedicationWithReminders?

    /** Saving a medication twice is a no-op so existing dosage and reminders are never wiped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMedication(entity: MedicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertReminders(reminders: List<MedicationReminderEntity>)

    @Query("DELETE FROM medication_reminders WHERE medicationId = :medicationId")
    protected abstract suspend fun deleteReminders(medicationId: String)

    @Query(
        """
        UPDATE medications
        SET customUserNickname = :nickname, doseAmount = :doseAmount, intervalHours = :intervalHours
        WHERE id = :id
        """,
    )
    protected abstract suspend fun updateDetailColumns(
        id: String,
        nickname: String?,
        doseAmount: String?,
        intervalHours: Int?,
    )

    @Query("DELETE FROM medications WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Transaction
    open suspend fun insert(entity: MedicationEntity, reminders: List<MedicationReminderEntity>) {
        if (insertMedication(entity) != -1L) insertReminders(reminders)
    }

    /** Replaces the editable fields and the whole reminder list of one medication atomically. */
    @Transaction
    open suspend fun updateDetails(
        id: String,
        nickname: String?,
        doseAmount: String?,
        intervalHours: Int?,
        reminders: List<MedicationReminderEntity>,
    ) {
        updateDetailColumns(id, nickname, doseAmount, intervalHours)
        deleteReminders(id)
        insertReminders(reminders)
    }
}
