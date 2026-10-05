package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.analytics.AnalyticsEvents
import com.example.dosediary.domain.analytics.AnalyticsLogger
import com.example.dosediary.domain.analytics.NoOpAnalyticsLogger
import com.example.dosediary.domain.model.AppResult
import com.example.dosediary.domain.model.DomainError
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.MedicationDetails
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.ValidationReason
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderNotifier
import com.example.dosediary.domain.repository.ReminderScheduler
import com.example.dosediary.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** Searches OpenFDA for medications by brand name. */
class SearchMedicationUseCase(private val repository: DrugSearchRepository) {

    suspend operator fun invoke(query: String): AppResult<List<Medication>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            return AppResult.Failure(DomainError.Validation(ValidationReason.QUERY_TOO_SHORT))
        }
        return repository.searchByBrandName(trimmed)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

class ObserveSavedMedicationsUseCase(private val repository: MedicationRepository) {
    operator fun invoke(): Flow<List<Medication>> = repository.observeMedications()
}

/** Saves a medication to the diary. Saving one that is already there is a no-op and logs nothing. */
class SaveMedicationUseCase(
    private val repository: MedicationRepository,
    private val analytics: AnalyticsLogger = NoOpAnalyticsLogger,
) {
    suspend operator fun invoke(medication: Medication) {
        val isNew = repository.getMedication(medication.id) == null
        repository.saveMedication(medication)
        if (isNew) {
            analytics.logEvent(
                AnalyticsEvents.MEDICATION_ADDED,
                mapOf(AnalyticsEvents.PARAM_INTAKE_COUNT to medication.intakes.size.toString()),
            )
        }
    }
}

/** Deletes a medication and makes sure its reminders no longer fire. */
class DeleteMedicationUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(id: String) {
        repository.getMedication(id)?.let(scheduler::cancel)
        repository.deleteMedication(id)
    }
}

class GetMedicationUseCase(private val repository: MedicationRepository) {
    suspend operator fun invoke(id: String): Medication? = repository.getMedication(id)
}

/**
 * Validates and stores the nickname, frequency and intakes (time, dose, notification) of a saved
 * medication, then re-schedules its reminders so the notifications match what was saved.
 *
 * Blank text is stored as `null`; intakes are sorted by time. Two intakes at the same time are
 * rejected, because the time identifies an intake's reminder.
 *
 * For "every N days" the counting starts on the day the interval is first set (or changed); saving
 * with an unchanged interval keeps the existing start so the rhythm does not shift.
 */
class UpdateMedicationDetailsUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        medicationId: String,
        nickname: String,
        frequencyDays: Int,
        intakes: List<Intake>,
    ): AppResult<Unit> {
        val cleanNickname = nickname.trim().takeIf { it.isNotEmpty() }
        val cleanIntakes = intakes
            .map { it.copy(doseAmount = it.doseAmount?.trim()?.takeIf(String::isNotEmpty)) }
            .sortedBy { it.time }

        val invalid = when {
            cleanNickname != null && cleanNickname.length > Medication.MAX_NICKNAME_LENGTH ->
                ValidationReason.NICKNAME_TOO_LONG
            frequencyDays !in Medication.MIN_FREQUENCY_DAYS..Medication.MAX_FREQUENCY_DAYS ->
                ValidationReason.FREQUENCY_OUT_OF_RANGE
            cleanIntakes.size > Medication.MAX_INTAKES -> ValidationReason.TOO_MANY_INTAKES
            cleanIntakes.any { (it.doseAmount?.length ?: 0) > Medication.MAX_DOSE_LENGTH } ->
                ValidationReason.DOSE_TOO_LONG
            cleanIntakes.map { it.time }.distinct().size != cleanIntakes.size ->
                ValidationReason.DUPLICATE_INTAKE_TIME
            else -> null
        }
        if (invalid != null) return AppResult.Failure(DomainError.Validation(invalid))

        val existing = repository.getMedication(medicationId)
            ?: return AppResult.Failure(DomainError.Validation(ValidationReason.MEDICATION_NOT_FOUND))

        val startDay = if (frequencyDays == existing.frequencyDays && existing.frequencyStartEpochDay != 0L) {
            existing.frequencyStartEpochDay
        } else {
            Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        }

        val details = MedicationDetails(
            nickname = cleanNickname,
            frequencyDays = frequencyDays,
            frequencyStartEpochDay = startDay,
            intakes = cleanIntakes,
        )

        // Cancel with the stored intakes first, so reminders of removed or retimed intakes disappear.
        scheduler.cancel(existing)
        repository.updateDetails(medicationId, details)
        scheduler.schedule(
            existing.copy(
                customUserNickname = cleanNickname,
                frequencyDays = frequencyDays,
                frequencyStartEpochDay = startDay,
                intakes = cleanIntakes,
            ),
        )
        return AppResult.Success(Unit)
    }
}

/**
 * Re-creates every reminder from the stored medications. Used after boot, app update, a time-zone
 * change and when exact-alarm access changes, since those all drop or invalidate pending alarms.
 */
class SyncRemindersUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke() {
        repository.observeMedications().first().forEach { medication ->
            scheduler.cancel(medication)
            scheduler.schedule(medication)
        }
    }
}

/**
 * Runs when the reminder of an intake is due: shows the notification and schedules the next one.
 *
 * The medication is read fresh, so an intake that was deleted, retimed or switched off after the
 * reminder was scheduled stays silent and ends its chain. The next reminder is scheduled before the
 * notification is shown so a notification failure can never break the chain.
 */
class HandleIntakeDueUseCase(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    suspend operator fun invoke(medicationId: String, time: ReminderTime) {
        val medication = repository.getMedication(medicationId) ?: return
        val intake = medication.intakes.firstOrNull { it.time == time } ?: return
        if (!intake.notify) return
        // The guard keeps an alarm that fires a moment early from being scheduled for "now" again.
        scheduler.scheduleIntake(medication, intake, clock.nowMillis() + EARLY_FIRE_GUARD_MILLIS)
        notifier.show(medication, intake)
    }

    companion object {
        const val EARLY_FIRE_GUARD_MILLIS = 30_000L
    }
}
