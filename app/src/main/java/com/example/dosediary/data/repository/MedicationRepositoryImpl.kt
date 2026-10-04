package com.example.dosediary.data.repository

import com.example.dosediary.data.local.dao.MedicationDao
import com.example.dosediary.data.local.toDomain
import com.example.dosediary.data.local.toEntity
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
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
        dao.insert(medication.toEntity(savedAtMillis = clock.nowMillis()))
    }

    override suspend fun deleteMedication(id: String) = dao.deleteById(id)

    override suspend fun updateReminder(id: String, time: ReminderTime?) =
        dao.updateReminder(id, time?.hour, time?.minute)

    override suspend fun updateNickname(id: String, nickname: String?) = dao.updateNickname(id, nickname)
}
