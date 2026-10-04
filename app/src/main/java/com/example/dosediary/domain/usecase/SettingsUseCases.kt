package com.example.dosediary.domain.usecase

import com.example.dosediary.domain.model.UserSettings
import com.example.dosediary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<UserSettings> = repository.observeSettings()
}

class SetDarkModeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setDarkMode(enabled)
}

class SetBiometricsEnabledUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setBiometricsEnabled(enabled)
}

class SetScreenProtectionEnabledUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setScreenProtectionEnabled(enabled)
}
