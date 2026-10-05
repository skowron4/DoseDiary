package com.example.dosediary.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.example.dosediary.BuildConfig
import com.example.dosediary.data.analytics.DebugAnalyticsLogger
import com.example.dosediary.data.locale.AppCompatLanguageManager
import com.example.dosediary.data.preferences.DataStoreSearchHistoryRepository
import com.example.dosediary.data.preferences.DataStoreSettingsRepository
import com.example.dosediary.data.repository.DrugInteractionRepositoryImpl
import com.example.dosediary.data.repository.DrugSearchRepositoryImpl
import com.example.dosediary.data.repository.MedicationRepositoryImpl
import com.example.dosediary.data.repository.SymptomRepositoryImpl
import com.example.dosediary.data.worker.AndroidReminderNotifier
import com.example.dosediary.data.worker.AndroidReminderPermissions
import com.example.dosediary.data.worker.HybridReminderScheduler
import com.example.dosediary.domain.analytics.AnalyticsLogger
import com.example.dosediary.domain.analytics.NoOpAnalyticsLogger
import com.example.dosediary.domain.interaction.CheckMedicationInteractionsUseCase
import com.example.dosediary.domain.interaction.DrugInteractionRepository
import com.example.dosediary.domain.repository.DrugSearchRepository
import com.example.dosediary.domain.repository.LanguageManager
import com.example.dosediary.domain.repository.MedicationRepository
import com.example.dosediary.domain.repository.ReminderNotifier
import com.example.dosediary.domain.repository.ReminderPermissions
import com.example.dosediary.domain.repository.ReminderScheduler
import com.example.dosediary.domain.repository.SearchHistoryRepository
import com.example.dosediary.domain.repository.SettingsRepository
import com.example.dosediary.domain.repository.SymptomRepository
import com.example.dosediary.domain.usecase.AddSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ClearSearchHistoryUseCase
import com.example.dosediary.domain.usecase.DeleteMedicationUseCase
import com.example.dosediary.domain.usecase.DeleteSymptomUseCase
import com.example.dosediary.domain.usecase.GetMedicationUseCase
import com.example.dosediary.domain.usecase.GetSymptomUseCase
import com.example.dosediary.domain.usecase.HandleIntakeDueUseCase
import com.example.dosediary.domain.usecase.LogSymptomUseCase
import com.example.dosediary.domain.usecase.ObserveSavedMedicationsUseCase
import com.example.dosediary.domain.usecase.ObserveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.ObserveSettingsUseCase
import com.example.dosediary.domain.usecase.ObserveSymptomsUseCase
import com.example.dosediary.domain.usecase.RemoveSearchHistoryUseCase
import com.example.dosediary.domain.usecase.SaveMedicationUseCase
import com.example.dosediary.domain.usecase.SearchMedicationUseCase
import com.example.dosediary.domain.usecase.SetBiometricsEnabledUseCase
import com.example.dosediary.domain.usecase.SetDarkModeUseCase
import com.example.dosediary.domain.usecase.SetScreenProtectionEnabledUseCase
import com.example.dosediary.domain.usecase.SyncRemindersUseCase
import com.example.dosediary.domain.usecase.UpdateMedicationDetailsUseCase
import com.example.dosediary.domain.util.Clock
import com.example.dosediary.presentation.app.AppViewModel
import com.example.dosediary.presentation.common.AndroidUiTextFormatter
import com.example.dosediary.presentation.common.UiTextFormatter
import com.example.dosediary.presentation.dashboard.DashboardViewModel
import com.example.dosediary.presentation.medication.EditMedicationViewModel
import com.example.dosediary.presentation.search.SearchViewModel
import com.example.dosediary.presentation.settings.SettingsViewModel
import com.example.dosediary.presentation.symptom.AddSymptomViewModel
import com.example.dosediary.presentation.symptom.SymptomDiaryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // --- Platform services -------------------------------------------------------------------
    single<Clock> { Clock { System.currentTimeMillis() } }
    // Events only reach Logcat in debug builds; release builds drop them.
    single<AnalyticsLogger> { if (BuildConfig.DEBUG) DebugAnalyticsLogger() else NoOpAnalyticsLogger }
    single { WorkManager.getInstance(androidContext()) }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { androidContext().preferencesDataStoreFile("settings") }
    }

    // --- Repositories ------------------------------------------------------------------------
    single<DrugSearchRepository> { DrugSearchRepositoryImpl(api = get(), ioDispatcher = Dispatchers.IO) }
    single<DrugInteractionRepository> { DrugInteractionRepositoryImpl(api = get(), ioDispatcher = Dispatchers.IO) }
    single<MedicationRepository> { MedicationRepositoryImpl(dao = get(), clock = get()) }
    single<SymptomRepository> { SymptomRepositoryImpl(dao = get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(dataStore = get()) }
    single<LanguageManager> { AppCompatLanguageManager() }
    single<SearchHistoryRepository> { DataStoreSearchHistoryRepository(dataStore = get()) }
    single<UiTextFormatter> { AndroidUiTextFormatter(androidContext()) }
    single<ReminderPermissions> { AndroidReminderPermissions(androidContext()) }
    single<ReminderNotifier> { AndroidReminderNotifier(androidContext()) }
    single<ReminderScheduler> {
        HybridReminderScheduler(
            context = androidContext(),
            workManager = get(),
            permissions = get(),
            clock = get(),
        )
    }

    // --- Use cases ---------------------------------------------------------------------------
    factory { SearchMedicationUseCase(get()) }
    factory { ObserveSavedMedicationsUseCase(get()) }
    factory { SaveMedicationUseCase(repository = get(), analytics = get()) }
    factory { DeleteMedicationUseCase(get(), get()) }
    factory { GetMedicationUseCase(get()) }
    factory { CheckMedicationInteractionsUseCase(interactions = get(), medications = get(), analytics = get()) }
    factory { UpdateMedicationDetailsUseCase(get(), get(), get()) }
    factory { SyncRemindersUseCase(get(), get()) }
    factory { HandleIntakeDueUseCase(repository = get(), scheduler = get(), notifier = get(), clock = get()) }
    factory { ObserveSymptomsUseCase(get()) }
    factory { GetSymptomUseCase(get()) }
    factory { LogSymptomUseCase(repository = get(), clock = get(), analytics = get()) }
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
            deleteMedication = get(),
            text = get(),
        )
    }
    viewModel { SymptomDiaryViewModel(observeSymptoms = get(), deleteSymptom = get(), text = get()) }
    viewModel { params ->
        EditMedicationViewModel(
            medicationId = params.get<String>(),
            getMedication = get(),
            updateDetails = get(),
            checkInteractions = get(),
            permissions = get(),
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
            checkInteractions = get(),
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
