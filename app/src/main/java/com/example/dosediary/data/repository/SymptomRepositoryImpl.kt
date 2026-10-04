package com.example.dosediary.data.repository

import com.example.dosediary.data.local.dao.SymptomDao
import com.example.dosediary.data.local.toDomain
import com.example.dosediary.data.local.toEntity
import com.example.dosediary.domain.model.Symptom
import com.example.dosediary.domain.repository.SymptomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SymptomRepositoryImpl(private val dao: SymptomDao) : SymptomRepository {

    override fun observeSymptoms(): Flow<List<Symptom>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getSymptom(id: Long): Symptom? = dao.getById(id)?.toDomain()

    override suspend fun saveSymptom(symptom: Symptom) {
        dao.upsert(symptom.toEntity())
    }

    override suspend fun deleteSymptom(id: Long) = dao.deleteById(id)
}
