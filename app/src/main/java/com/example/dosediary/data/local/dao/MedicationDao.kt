package com.example.dosediary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationIntakeEntity
import com.example.dosediary.data.local.entity.MedicationWithIntakes
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
    abstract fun observeAll(): Flow<List<MedicationWithIntakes>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    abstract suspend fun getById(id: String): MedicationWithIntakes?

    /** Saving a medication twice is a no-op so existing intakes and frequency are never wiped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMedication(entity: MedicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIntakes(intakes: List<MedicationIntakeEntity>)

    @Query("DELETE FROM medication_intakes WHERE medicationId = :medicationId")
    protected abstract suspend fun deleteIntakes(medicationId: String)

    @Query(
        """
        UPDATE medications
        SET customUserNickname = :nickname, frequencyDays = :frequencyDays,
            frequencyStartEpochDay = :frequencyStartEpochDay
        WHERE id = :id
        """,
    )
    protected abstract suspend fun updateDetailColumns(
        id: String,
        nickname: String?,
        frequencyDays: Int,
        frequencyStartEpochDay: Long,
    )

    @Query("DELETE FROM medications WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Transaction
    open suspend fun insert(entity: MedicationEntity, intakes: List<MedicationIntakeEntity>) {
        if (insertMedication(entity) != -1L) insertIntakes(intakes)
    }

    /** Replaces the editable fields and the whole intake list of one medication atomically. */
    @Transaction
    open suspend fun updateDetails(
        id: String,
        nickname: String?,
        frequencyDays: Int,
        frequencyStartEpochDay: Long,
        intakes: List<MedicationIntakeEntity>,
    ) {
        updateDetailColumns(id, nickname, frequencyDays, frequencyStartEpochDay)
        deleteIntakes(id)
        insertIntakes(intakes)
    }
}
