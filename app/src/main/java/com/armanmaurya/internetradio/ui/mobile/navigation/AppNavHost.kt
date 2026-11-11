package com.armanmaurya.internetradio.ui.mobile.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeScreen
import com.armanmaurya.internetradio.ui.mobile.screens.countries.CountrySelectScreen
import com.armanmaurya.internetradio.ui.mobile.screens.languages.LanguageSelectScreen
import com.armanmaurya.internetradio.ui.mobile.screens.tags.TagSelectScreen
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.AppLanguageScreen
import com.armanmaurya.internetradio.ui.mobile.screens.settings.SettingsScreen
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel
import androidx.compose.runtime.remember
import com.armanmaurya.internetradio.ui.mobile.screens.edit.EditStationScreen
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.armanmaurya.internetradio.R

@Composable
fun AppNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCheckUpdates: () -> Unit = {}
) {
    val discoverViewModel: HomeViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    // Material's shared-axis slide distance is a fixed 30dp regardless of screen width.
    val sharedAxisSlidePx = with(androidx.compose.ui.platform.LocalDensity.current) { 30.dp.roundToPx() }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Discover.route,
        modifier = modifier,
        // Material shared axis X: short slide + fade-through, mirrored for back navigation.
        enterTransition = { MaterialSharedAxis.enter(sharedAxisSlidePx, forward = true) },
        exitTransition = { MaterialSharedAxis.exit(sharedAxisSlidePx, forward = true) },
        popEnterTransition = { MaterialSharedAxis.enter(sharedAxisSlidePx, forward = false) },
        popExitTransition = { MaterialSharedAxis.exit(sharedAxisSlidePx, forward = false) }
    ) {
        composable(AppDestination.Discover.route) {
            HomeScreen(
                widthSizeClass = widthSizeClass,
                viewModel = discoverViewModel,
                onSettingsClick = { navController.navigate(AppDestination.Settings.route) },
                onCountryClick = { 
                    val currentCode = discoverViewModel.uiState.value.selectedCountryCode
                    navController.navigate(AppDestination.CountrySelect.createRoute(currentCode))
                },
                onLanguageClick = {
                    val currentLanguage = discoverViewModel.uiState.value.selectedLanguage
                    navController.navigate(AppDestination.LanguageSelect.createRoute(currentLanguage))
                },
                onTagClick = {
                    val currentTags = discoverViewModel.uiState.value.selectedTags
                    navController.navigate(AppDestination.TagSelect.createRoute(currentTags))
                },
                onEditSchedule = { scheduleId -> navController.navigate(AppDestination.EditSchedule.createRoute(scheduleId)) },
                onEditStation = { stationUuid -> navController.navigate(AppDestination.EditStation.createRoute(stationUuid)) },
                contentPadding = contentPadding,
                playerViewModel = playerViewModel
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onCheckUpdatesClick = onCheckUpdates,
                onLanguageClick = { navController.navigate(AppDestination.AppLanguage.route) },
                contentPadding = contentPadding
            )
        }
        composable(AppDestination.AppLanguage.route) {
            // Use the Settings screen's ViewModel: it outlives this page, so saving the language
            // isn't cancelled when we pop back (applying a locale also recreates the activity).
            val settingsEntry = remember(it) { navController.getBackStackEntry(AppDestination.Settings.route) }
            val settingsViewModel: SettingsViewModel = hiltViewModel(settingsEntry)
            AppLanguageScreen(
                onLanguageSelected = { tag ->
                    navController.popBackStack()
                    settingsViewModel.setAppLanguage(tag)
                },
                onBackClick = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }
        composable(
            route = AppDestination.CountrySelect.route,
            arguments = listOf(
                navArgument("selectedCode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedCode = backStackEntry.arguments?.getString("selectedCode")
            CountrySelectScreen(
                selectedCountryCode = selectedCode,
                onCountrySelected = { country, stateCode ->
                    discoverViewModel.updateCountry(country.isoCode, stateCode)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }
        composable(
            route = AppDestination.LanguageSelect.route,
            arguments = listOf(
                navArgument("selectedLanguage") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedLanguage = backStackEntry.arguments?.getString("selectedLanguage")
            val context = androidx.compose.ui.platform.LocalContext.current
            LanguageSelectScreen(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { language ->
                    val languageCode = if (language.isoCode.isNullOrEmpty()) "" else language.isoCode
                    discoverViewModel.updateLanguage(languageCode)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }
        composable(
            route = AppDestination.TagSelect.route,
            arguments = listOf(
                navArgument("selectedTags") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedTagsStr = backStackEntry.arguments?.getString("selectedTags")
            val initialTags = selectedTagsStr?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            TagSelectScreen(
                initialTags = initialTags,
                onTagsSelected = { tags ->
                    discoverViewModel.updateTags(tags)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() },
                contentPadding = contentPadding
            )
        }
        composable(
            route = AppDestination.EditStation.route,
            arguments = listOf(navArgument("stationUuid") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val stationUuid = backStackEntry.arguments?.getString("stationUuid")
            EditStationScreen(
                stationUuid = stationUuid,
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.navigateUp() },
                onPlayStation = { station ->
                    playerViewModel.play(listOf(station), 0, PlaybackSource.None)
                },
                contentPadding = contentPadding
            )
        }
        composable(
            route = AppDestination.EditSchedule.route,
            arguments = listOf(
                navArgument("scheduleId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val scheduleIdStr = backStackEntry.arguments?.getString("scheduleId")
            val scheduleId = scheduleIdStr?.toIntOrNull()
            com.armanmaurya.internetradio.ui.mobile.screens.schedule.EditScheduleScreen(
                scheduleId = scheduleId,
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.navigateUp() },
                contentPadding = contentPadding
            )
        }
    }
}
