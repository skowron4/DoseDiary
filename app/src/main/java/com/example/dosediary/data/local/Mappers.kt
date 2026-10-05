package com.example.dosediary.data.local

import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.MedicationReminderEntity
import com.example.dosediary.data.local.entity.MedicationWithReminders
import com.example.dosediary.data.local.entity.SymptomEntity
import com.example.dosediary.data.local.entity.SymptomWithMedication
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom

fun MedicationWithReminders.toDomain(): Medication = Medication(
    id = medication.id,
    commercialName = medication.commercialName,
    activeSubstance = medication.activeSubstance,
    manufacturer = medication.manufacturer,
    customUserNickname = medication.customUserNickname,
    purpose = medication.purpose,
    route = medication.route,
    doseAmount = medication.doseAmount,
    intervalHours = medication.intervalHours,
    reminderTimes = reminders.map { ReminderTime(it.hour, it.minute) }.sorted(),
)

fun Medication.toEntity(savedAtMillis: Long): MedicationEntity = MedicationEntity(
    id = id,
    commercialName = commercialName,
    activeSubstance = activeSubstance,
    manufacturer = manufacturer,
    customUserNickname = customUserNickname,
    purpose = purpose,
    route = route,
    doseAmount = doseAmount,
    intervalHours = intervalHours,
    savedAtMillis = savedAtMillis,
)

fun List<ReminderTime>.toReminderEntities(medicationId: String): List<MedicationReminderEntity> =
    distinct().map { MedicationReminderEntity(medicationId = medicationId, hour = it.hour, minute = it.minute) }

fun SymptomWithMedication.toDomain(): Symptom = Symptom(
    id = id,
    medicationId = medicationId,
    medicationName = medicationName,
    severity = severity,
    notes = notes,
    loggedAtMillis = loggedAtMillis,
)

fun Symptom.toEntity(): SymptomEntity = SymptomEntity(
    id = id,
    medicationId = medicationId,
    severity = severity,
    notes = notes,
    loggedAtMillis = loggedAtMillis,
)
