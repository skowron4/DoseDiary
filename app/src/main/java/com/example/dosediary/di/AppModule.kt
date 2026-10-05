package com.example.dosediary.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.example.dosediary.data.preferences.DataStoreSearchHistoryRepository
import com.example.dosediary.data.preferences.DataStoreSettingsRepository
import com.example.dosediary.data.repository.DrugSearchRepositoryImpl
import com.example.dosediary.data.repository.MedicationRepositoryImpl
import com.example.dosediary.data.repository.SymptomRepositoryImpl
import com.example.dosediary.data.worker.WorkManagerReminderScheduler
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderScheduler
import com.example.dosediary.domain.repository.SearchHistoryRepository
import com.example.dosediary.domain.repository.SettingsRepository
import com.example.dosediary.domain.repository.SymptomRepository
import com.example.dosediary.domain.usecase.AddSearchHistoryUseCase
import com.example.dosediary.domain.usecase.CancelReminderUseCase
import com.example.dosediary.domain.usecase.ClearSearchHistoryUseCase
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.GetSymptomUseCase
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ObserveSettingsUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
import com.example.dosediary.domain.usecase.RemoveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.SaveMedicationUseCase
import com.example.dosediary.domain.usecase.ScheduleReminderUseCase
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import com.example.dosediary.domain.usecase.SetBiometricsEnabledUseCase
import com.example.dosediary.domain.usecase.SetDarkModeUseCase
import com.example.dosediary.domain.usecase.SetScreenProtectionEnabledUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationNicknameUseCase
import com.example.dosediary.domain.util.Clock
import com.example.dosediary.presentation.app.AppViewModel
import com.example.dosediary.presentation.dashboard.DashboardViewModel
import com.example.dosediary.presentation.search.SearchViewModel
import com.example.dosediary.presentation.settings.SettingsViewModel
import com.example.dosediary.presentation.symptom.AddSymptomViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // --- Platform services -------------------------------------------------------------------
    single<Clock> { Clock { System.currentTimeMillis() } }
    single { WorkManager.getInstance(androidContext()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { androidContext().preferencesDataStoreFile("settings") }
    }

    // --- Repositories ------------------------------------------------------------------------
    single<DrugSearchRepository> { DrugSearchRepositoryImpl(api = get(), ioDispatcher = Dispatchers.IO) }
    single<MedicationRepository> { MedicationRepositoryImpl(dao = get(), clock = get()) }
    single<SymptomRepository> { SymptomRepositoryImpl(dao = get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(dataStore = get()) }
    single<SearchHistoryRepository> { DataStoreSearchHistoryRepository(dataStore = get()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(workManager = get()) }

    // --- Use cases ---------------------------------------------------------------------------
    factory { SearchMedicationUseCase(get()) }
    factory { ObserveSavedMedicationsUseCase(get()) }
    factory { SaveMedicationUseCase(get()) }
    factory { DeleteMedicationUseCase(get(), get()) }
    factory { ScheduleReminderUseCase(get(), get()) }
    factory { CancelReminderUseCase(get(), get()) }
    factory { UpdateMedicationNicknameUseCase(get(), get()) }
    factory { ObserveSymptomsUseCase(get()) }
    factory { GetSymptomUseCase(get()) }
    factory { LogSymptomUseCase(get(), get()) }
    factory { DeleteSymptomUseCase(get()) }
    factory { ObserveSearchHistoryUseCase(get()) }
    factory { AddSearchHistoryUseCase(get()) }
    factory { RemoveSearchHistoryUseCase(get()) }
    factory { ClearSearchHistoryUseCase(get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { SetDarkModeUseCase(get()) }
    factory { SetBiometricsEnabledUseCase(get()) }
    factory { SetScreenProtectionEnabledUseCase(get()) }

    // --- ViewModels --------------------------------------------------------------------------
    viewModel { AppViewModel(observeSettings = get(), clock = get()) }
    viewModel {
        DashboardViewModel(
            observeMedications = get(),
            observeSymptoms = get(),
            deleteMedication = get(),
            deleteSymptom = get(),
            scheduleReminder = get(),
            cancelReminder = get(),
            updateNickname = get(),
        )
    }
    viewModel {
        SearchViewModel(
            searchMedication = get(),
            observeSavedMedications = get(),
            saveMedication = get(),
            observeHistory = get(),
            addToHistory = get(),
            removeFromHistory = get(),
            clearHistory = get(),
        )
    }
    viewModel { params ->
        AddSymptomViewModel(
            initialMedicationId = params.getOrNull<String>(),
            symptomId = params.getOrNull<Long>(),
            observeMedications = get(),
            getSymptom = get(),
            logSymptom = get(),
        )
    }
    viewModel {
        SettingsViewModel(
            observeSettings = get(),
            setDarkMode = get(),
            setBiometricsEnabled = get(),
            setScreenProtectionEnabled = get(),
        )
    }
}
