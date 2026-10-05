package com.example.dosediary.data.local

import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationIntakeEntity
import com.example.dosediary.data.local.entity.MedicationWithIntakes
import com.example.dosediary.data.local.entity.SymptomEntity
import com.example.dosediary.data.local.entity.SymptomWithMedication
import com.example.dosediary.domain.model.Intake
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.model.SymptomTag

fun MedicationWithIntakes.toDomain(): Medication = Medication(
    id = medication.id,
    commercialName = medication.commercialName,
    activeSubstance = medication.activeSubstance,
    manufacturer = medication.manufacturer,
    customUserNickname = medication.customUserNickname,
    purpose = medication.purpose,
    route = medication.route,
    frequencyDays = medication.frequencyDays,
    frequencyStartEpochDay = medication.frequencyStartEpochDay,
    intakes = intakes.map { it.toDomain() }.sortedBy { it.time },
)

fun Medication.toEntity(savedAtMillis: Long): MedicationEntity = MedicationEntity(
    id = id,
    commercialName = commercialName,
    activeSubstance = activeSubstance,
    manufacturer = manufacturer,
    customUserNickname = customUserNickname,
    purpose = purpose,
    route = route,
    frequencyDays = frequencyDays,
    frequencyStartEpochDay = frequencyStartEpochDay,
    savedAtMillis = savedAtMillis,
)

fun MedicationIntakeEntity.toDomain(): Intake = Intake(
    time = ReminderTime(hour, minute),
    doseAmount = doseAmount,
    notify = notify,
)

/** One row per distinct time; if a time appears twice the first entry wins. */
fun List<Intake>.toIntakeEntities(medicationId: String): List<MedicationIntakeEntity> =
    distinctBy { it.time }.map {
        MedicationIntakeEntity(
            medicationId = medicationId,
            hour = it.time.hour,
            minute = it.time.minute,
            doseAmount = it.doseAmount,
            notify = it.notify,
        )
    }

fun SymptomWithMedication.toDomain(): Symptom = Symptom(
    id = id,
    medicationId = medicationId,
    medicationName = medicationName,
    severity = severity,
    tags = SymptomTag.decode(tags),
    notes = notes,
    loggedAtMillis = loggedAtMillis,
)

fun Symptom.toEntity(): SymptomEntity = SymptomEntity(
    id = id,
    medicationId = medicationId,
    severity = severity,
    notes = notes,
    loggedAtMillis = loggedAtMillis,
    tags = SymptomTag.encode(tags),
)
