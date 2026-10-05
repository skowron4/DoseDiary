package com.example.dosediary.domain

import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.MedicationDetails
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderScheduler
import com.example.dosediary.domain.repository.SymptomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDrugSearchRepository(
    var result: AppResult<List<Medication>> = AppResult.Success(emptyList()),
) : DrugSearchRepository {
    val queries = mutableListOf<String>()

    override suspend fun searchByBrandName(query: String): AppResult<List<Medication>> {
        queries += query
        return result
    }
}

class FakeMedicationRepository(initial: List<Medication> = emptyList()) : MedicationRepository {
    private val items = MutableStateFlow(initial)

    override fun observeMedications(): Flow<List<Medication>> = items
    override suspend fun getMedication(id: String): Medication? = items.value.firstOrNull { it.id == id }
    override suspend fun saveMedication(medication: Medication) {
        if (items.value.none { it.id == medication.id }) items.value += medication
    }

    override suspend fun deleteMedication(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun updateDetails(id: String, details: MedicationDetails) {
        items.value = items.value.map {
            if (it.id == id) {
                it.copy(
                    customUserNickname = details.nickname,
                    doseAmount = details.doseAmount,
                    intervalHours = details.intervalHours,
                    reminderTimes = details.reminderTimes,
                )
            } else {
                it
            }
        }
    }
}
class FakeSymptomRepository : SymptomRepository {
    private val items = MutableStateFlow<List<Symptom>>(emptyList())
    private var nextId = 1L

    override fun observeSymptoms(): Flow<List<Symptom>> = items.map { it.sortedByDescending { s -> s.loggedAtMillis } }
    override suspend fun getSymptom(id: Long): Symptom? = items.value.firstOrNull { it.id == id }
    override suspend fun saveSymptom(symptom: Symptom) {
        val withId = if (symptom.id == 0L) symptom.copy(id = nextId++) else symptom
        items.value = items.value.filterNot { it.id == withId.id } + withId
    }

    override suspend fun deleteSymptom(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }
}

class FakeReminderScheduler : ReminderScheduler {
    data class Scheduled(val medicationId: String, val name: String, val times: List<ReminderTime>)

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<String>()

    override fun schedule(medicationId: String, medicationName: String, times: List<ReminderTime>) {
        scheduled += Scheduled(medicationId, medicationName, times)
    }

    override fun cancel(medicationId: String) {
        cancelled += medicationId
    }
}
