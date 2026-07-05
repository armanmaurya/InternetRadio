package com.armanmaurya.internetradio.ui.tv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel

import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecentViewModel
import com.armanmaurya.internetradio.ui.tv.screens.library.LibraryScreen
import com.armanmaurya.internetradio.ui.tv.screens.browse.BrowseScreen
import com.armanmaurya.internetradio.ui.tv.screens.countries.CountrySelectScreen
import com.armanmaurya.internetradio.ui.tv.screens.languages.LanguageSelectScreen
import com.armanmaurya.internetradio.ui.tv.screens.player.PlayerScreen
import com.armanmaurya.internetradio.ui.tv.screens.recent.RecentScreen
import com.armanmaurya.internetradio.ui.tv.screens.about.AboutScreen
import com.armanmaurya.internetradio.ui.tv.screens.settings.SettingsScreen
import com.armanmaurya.internetradio.ui.tv.screens.tags.TagSelectScreen
import com.armanmaurya.internetradio.ui.tv.screens.edit.AddEditStationScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType

@Composable
fun AppNavHost(
    navController: NavHostController,
    browseViewModel: BrowseViewModel,
    recentViewModel: RecentViewModel,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,
    startDestination: String = AppDestination.Browse.route,
    modifier: Modifier = Modifier
) {
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStationUuid = playbackState.currentStation?.stationUuid
    val isPlaybackActive = playbackState.isPlaying

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppDestination.Browse.route) {
            BrowseScreen(
                viewModel = browseViewModel,
                playingStationUuid = playingStationUuid,
                isPlaybackActive = isPlaybackActive,
                onStationClick = { stations, index, _ -> playerViewModel.play(stations, index, PlaybackSource.None) }
            )
        }
        composable(AppDestination.Recent.route) {
            RecentScreen(
                viewModel = recentViewModel,
                playingStationUuid = playingStationUuid,
                isPlaybackActive = isPlaybackActive,
                onStationClick = { stations, index, _ -> playerViewModel.play(stations, index, PlaybackSource.Recent) }
            )
        }
        composable(AppDestination.Library.route) {
            LibraryScreen(
                viewModel = libraryViewModel,
                playingStationUuid = playingStationUuid,
                isPlaybackActive = isPlaybackActive,
                onStationClick = { stations, index, _ -> playerViewModel.play(stations, index, PlaybackSource.Library) },
                onAddStation = { navController.navigate(AppDestination.AddEditStation.createRoute(null)) },
                onEditStation = { uuid -> navController.navigate(AppDestination.AddEditStation.createRoute(uuid)) }
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                onAboutClick = { navController.navigate(AppDestination.About.route) }
            )
        }
        composable(AppDestination.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Player.route) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                libraryViewModel = libraryViewModel,
                onEditStation = { uuid -> navController.navigate(AppDestination.AddEditStation.createRoute(uuid)) }
            )
        }
        composable(AppDestination.Tags.route) {
            TagSelectScreen(
                initialTags = homeUiState.selectedTags,
                onTagsSelected = {
                    homeViewModel.updateTags(it)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Language.route) {
            LanguageSelectScreen(
                selectedLanguage = homeUiState.selectedLanguage,
                onLanguageSelected = {
                    homeViewModel.updateLanguage(it.name)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppDestination.Country.route) {
            CountrySelectScreen(
                selectedCountryCode = homeUiState.selectedCountryCode,
                onCountrySelected = {
                    homeViewModel.updateCountry(it.isoCode)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(
            route = AppDestination.AddEditStation.route,
            arguments = listOf(navArgument("stationUuid") { 
                type = NavType.StringType 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val stationUuid = backStackEntry.arguments?.getString("stationUuid")
            AddEditStationScreen(
                stationUuid = stationUuid,
                viewModel = libraryViewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
