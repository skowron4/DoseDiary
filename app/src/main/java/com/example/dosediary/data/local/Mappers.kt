package com.example.dosediary.data.local

import com.example.dosediary.data.local.entity.MedicationEntity
import com.example.dosediary.data.local.entity.SymptomEntity
import com.example.dosediary.data.local.entity.SymptomWithMedication
import com.example.dosediary.domain.model.Medication
import com.example.dosediary.domain.model.ReminderTime
import com.example.dosediary.domain.model.Symptom

fun MedicationEntity.toDomain(): Medication = Medication(
    id = id,
    commercialName = commercialName,
    activeSubstance = activeSubstance,
    manufacturer = manufacturer,
    customUserNickname = customUserNickname,
    purpose = purpose,
    route = route,
    reminderTime = if (reminderHour != null && reminderMinute != null) {
        ReminderTime(reminderHour, reminderMinute)
    } else {
        null
    },
)

fun Medication.toEntity(savedAtMillis: Long): MedicationEntity = MedicationEntity(
    id = id,
    commercialName = commercialName,
    activeSubstance = activeSubstance,
    manufacturer = manufacturer,
    customUserNickname = customUserNickname,
    purpose = purpose,
    route = route,
    reminderHour = reminderTime?.hour,
    reminderMinute = reminderTime?.minute,
    savedAtMillis = savedAtMillis,
)

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
