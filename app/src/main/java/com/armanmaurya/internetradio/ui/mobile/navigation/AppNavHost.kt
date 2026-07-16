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
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.SettingsScreen
import com.armanmaurya.internetradio.ui.mobile.screens.about.AboutScreen
import com.armanmaurya.internetradio.ui.mobile.screens.edit.EditStationScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun AppNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCheckUpdates: () -> Unit = {}
) {
    val discoverViewModel: HomeViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = AppDestination.Discover.route,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { (it * 0.1f).toInt() },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -(it * 0.1f).toInt() },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -(it * 0.1f).toInt() },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { (it * 0.1f).toInt() },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
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
                onEditStation = { stationUuid -> navController.navigate(AppDestination.EditStation.createRoute(stationUuid)) },
                contentPadding = contentPadding
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onAboutClick = { navController.navigate(AppDestination.About.route) },
                onCheckUpdatesClick = onCheckUpdates,
                contentPadding = contentPadding
            )
        }
        composable(AppDestination.About.route) {
            AboutScreen(
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
                onCountrySelected = { country ->
                    discoverViewModel.updateCountry(country.isoCode)
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
            LanguageSelectScreen(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { language ->
                    val languageName = if (language.isoCode.isNullOrEmpty()) "All Languages" else language.name
                    discoverViewModel.updateLanguage(languageName)
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
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
