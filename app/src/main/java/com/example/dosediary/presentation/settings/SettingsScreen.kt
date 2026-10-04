package com.example.dosediary.presentation.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dosediary.R
import com.example.dosediary.presentation.common.LoadingState
import com.example.dosediary.presentation.common.TopLevelContentInsets
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = TopLevelContentInsets,
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        when (val current = state) {
            SettingsUiState.Loading -> LoadingState(Modifier.padding(padding))
            is SettingsUiState.Loaded -> Column(Modifier.fillMaxSize().padding(padding)) {
                val settings = current.settings
                val darkEnabled = settings.darkModeEnabled ?: isSystemInDarkTheme()

                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_dark_mode)) },
                    supportingContent = { Text(stringResource(R.string.setting_dark_mode_desc)) },
                    trailingContent = {
                        Switch(checked = darkEnabled, onCheckedChange = viewModel::onDarkModeChange)
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_biometrics)) },
                    supportingContent = { Text(stringResource(R.string.setting_biometrics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = settings.biometricsEnabled,
                            onCheckedChange = viewModel::onBiometricsChange,
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_screen_protection)) },
                    supportingContent = { Text(stringResource(R.string.setting_screen_protection_desc)) },
                    trailingContent = {
                        Switch(
                            checked = settings.screenProtectionEnabled,
                            onCheckedChange = viewModel::onScreenProtectionChange,
                        )
                    },
                )
            }
        }
    }
}
