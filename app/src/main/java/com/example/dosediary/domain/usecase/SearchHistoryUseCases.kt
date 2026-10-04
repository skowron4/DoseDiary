package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    operator fun invoke(): Flow<List<String>> = repository.observeHistory()
}

class AddSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(query: String) = repository.addQuery(query)
}

class RemoveSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(query: String) = repository.removeQuery(query)
}

class ClearSearchHistoryUseCase(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke() = repository.clear()
}
