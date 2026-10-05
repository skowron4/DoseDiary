package com.example.dosediary.domain

import com.example.dosediary.domain.analytics.AnalyticsLogger
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.MedicationDetails
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderNotifier
import com.example.dosediary.domain.repository.ReminderPermissions
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
                    frequencyDays = details.frequencyDays,
                    frequencyStartEpochDay = details.frequencyStartEpochDay,
                    intakes = details.intakes,
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

/** Records what the scheduler was asked to do, in order. */
class FakeReminderScheduler : ReminderScheduler {
    /** [schedule] calls: the medication as it was passed. */
    val scheduled = mutableListOf<Medication>()

    data class IntakeCall(val medicationId: String, val intake: Intake, val afterMillis: Long)

    /** [scheduleIntake] calls (what runs after a reminder fired). */
    val scheduledIntakes = mutableListOf<IntakeCall>()

    /** Medications passed to [cancel], as they were at that moment. */
    val cancelled = mutableListOf<Medication>()

    override fun schedule(medication: Medication) {
        scheduled += medication
    }

    override fun scheduleIntake(medication: Medication, intake: Intake, afterMillis: Long) {
        scheduledIntakes += IntakeCall(medication.id, intake, afterMillis)
    }

    override fun cancel(medication: Medication) {
        cancelled += medication
    }
}

class FakeReminderNotifier : ReminderNotifier {
    data class Shown(val medicationId: String, val intake: Intake)

    val shown = mutableListOf<Shown>()

    override fun show(medication: Medication, intake: Intake) {
        shown += Shown(medication.id, intake)
    }
}

class FakeReminderPermissions(
    var canPost: Boolean = true,
    var canRequest: Boolean = false,
    var exactNeedsAccess: Boolean = false,
    var canExact: Boolean = true,
) : ReminderPermissions {
    override fun canPostNotifications() = canPost
    override fun canRequestNotificationPermission() = canRequest
    override fun exactAlarmsNeedUserAccess() = exactNeedsAccess
    override fun canScheduleExactAlarms() = canExact
}

/** Keeps every logged event so tests can assert on what was reported. */
class RecordingAnalyticsLogger : AnalyticsLogger {
    data class Event(val name: String, val params: Map<String, String>)

    val events = mutableListOf<Event>()

    override fun logEvent(eventName: String, params: Map<String, String>) {
        events += Event(eventName, params)
    }
}
