package com.example.dosediary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dosediary.data.local.entity.MedicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    /** Sorted by the name the user sees: nickname if set, otherwise the commercial name. */
    @Query(
        """
        SELECT * FROM medications
        ORDER BY COALESCE(NULLIF(TRIM(customUserNickname), ''), commercialName) COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: String): MedicationEntity?

    /** Saving a medication twice is a no-op so an existing reminder is never wiped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MedicationEntity): Long

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE medications SET reminderHour = :hour, reminderMinute = :minute WHERE id = :id")
    suspend fun updateReminder(id: String, hour: Int?, minute: Int?)

    @Query("UPDATE medications SET customUserNickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: String, nickname: String?)
}
