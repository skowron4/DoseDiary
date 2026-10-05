package com.example.dosediary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dosediary.data.local.entity.SymptomEntity
import com.example.dosediary.data.local.entity.SymptomWithMedication
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomDao {

    @Query(
        """
        SELECT s.id AS id,
               s.medicationId AS medicationId,
               COALESCE(NULLIF(TRIM(m.customUserNickname), ''), m.commercialName) AS medicationName,
               s.severity AS severity,
               s.notes AS notes,
               s.loggedAtMillis AS loggedAtMillis,
               s.tags AS tags
        FROM symptoms s
        LEFT JOIN medications m ON m.id = s.medicationId
        ORDER BY s.loggedAtMillis DESC
        """,
    )
    fun observeAll(): Flow<List<SymptomWithMedication>>

    @Query(
        """
        SELECT s.id AS id,
               s.medicationId AS medicationId,
               COALESCE(NULLIF(TRIM(m.customUserNickname), ''), m.commercialName) AS medicationName,
               s.severity AS severity,
               s.notes AS notes,
               s.loggedAtMillis AS loggedAtMillis,
               s.tags AS tags
        FROM symptoms s
        LEFT JOIN medications m ON m.id = s.medicationId
        WHERE s.id = :id
        """,
    )
    suspend fun getById(id: Long): SymptomWithMedication?

    /** Inserts a new row, or replaces the row with the same id (update). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SymptomEntity): Long

    @Query("DELETE FROM symptoms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
