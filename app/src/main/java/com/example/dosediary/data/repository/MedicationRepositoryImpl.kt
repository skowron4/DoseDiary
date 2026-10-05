package com.example.dosediary.data.repository

import com.example.dosediary.data.local.dao.MedicationDao
import com.example.dosediary.data.local.toDomain
import com.example.dosediary.data.local.toEntity
import com.example.dosediary.data.local.toReminderEntities
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.MedicationDetails
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicationRepositoryImpl(
    private val dao: MedicationDao,
    private val clock: Clock,
) : MedicationRepository {

    override fun observeMedications(): Flow<List<Medication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getMedication(id: String): Medication? = dao.getById(id)?.toDomain()

    override suspend fun saveMedication(medication: Medication) {
        dao.insert(
            entity = medication.toEntity(savedAtMillis = clock.nowMillis()),
            reminders = medication.reminderTimes.toReminderEntities(medication.id),
        )
    }

    override suspend fun deleteMedication(id: String) = dao.deleteById(id)

    override suspend fun updateDetails(id: String, details: MedicationDetails) = dao.updateDetails(
        id = id,
        nickname = details.nickname,
        doseAmount = details.doseAmount,
        intervalHours = details.intervalHours,
        reminders = details.reminderTimes.toReminderEntities(id),
    )
}