package com.example.dosediary.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.dosediary.R
import com.example.dosediary.presentation.dashboard.DashboardScreen
import com.example.dosediary.presentation.medication.EditMedicationScreen
import com.example.dosediary.presentation.medication.EditMedicationViewModel
import com.example.dosediary.presentation.search.SearchScreen
import com.example.dosediary.presentation.settings.SettingsScreen
import com.example.dosediary.presentation.symptom.AddSymptomScreen
import com.example.dosediary.presentation.symptom.AddSymptomViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(DashboardRoute, DashboardRoute::class, R.string.nav_dashboard, Icons.Default.Home),
    TopLevelDestination(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Default.Settings),
)

@Composable
fun DoseDiaryNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { currentDestination?.hasRoute(it.routeClass) == true }

    Scaffold(
        // Each screen owns its own Scaffold + insets; this one only reserves room for the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onLogSymptom = { medicationId -> navController.navigate(AddSymptomRoute(medicationId = medicationId)) },
                    onEditSymptom = { symptomId -> navController.navigate(AddSymptomRoute(symptomId = symptomId)) },
                    onEditMedication = { medicationId -> navController.navigate(EditMedicationRoute(medicationId)) },
                    onOpenSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onLogSymptom = { medicationId -> navController.navigate(AddSymptomRoute(medicationId = medicationId)) },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<EditMedicationRoute> { entry ->
                val route = entry.toRoute<EditMedicationRoute>()
                val viewModel: EditMedicationViewModel = koinViewModel(
                    parameters = { parametersOf(route.medicationId) },
                )
                EditMedicationScreen(
                    onNavigateUp = { navController.navigateUp() },
                    viewModel = viewModel,
                )
            }
            composable<AddSymptomRoute> { entry ->
                val route = entry.toRoute<AddSymptomRoute>()
                val viewModel: AddSymptomViewModel = koinViewModel(
                    parameters = { parametersOf(route.medicationId, route.symptomId) },
                )
                AddSymptomScreen(
                    onNavigateUp = { navController.navigateUp() },
                    viewModel = viewModel,
                )
            }
        }
    }
}
