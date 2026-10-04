package com.example.dosediary.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosediary.domain.model.UserSettings
import com.example.dosediary.domain.usecase.ObserveSettingsUseCase
import com.example.dosediary.domain.usecase.SetBiometricsEnabledUseCase
import com.example.dosediary.domain.usecase.SetDarkModeUseCase
import com.example.dosediary.domain.usecase.SetScreenProtectionEnabledUseCase
import com.example.dosediary.presentation.common.runCatchingCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Loaded(val settings: UserSettings) : SettingsUiState
}

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val setDarkMode: SetDarkModeUseCase,
    private val setBiometricsEnabled: SetBiometricsEnabledUseCase,
    private val setScreenProtectionEnabled: SetScreenProtectionEnabledUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = observeSettings()
        .map<UserSettings, SettingsUiState> { SettingsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun onDarkModeChange(enabled: Boolean) {
        viewModelScope.launch { runCatchingCancellable { setDarkMode(enabled) } }
    }

    fun onBiometricsChange(enabled: Boolean) {
        viewModelScope.launch { runCatchingCancellable { setBiometricsEnabled(enabled) } }
    }

    fun onScreenProtectionChange(enabled: Boolean) {
        viewModelScope.launch { runCatchingCancellable { setScreenProtectionEnabled(enabled) } }
    }
}
