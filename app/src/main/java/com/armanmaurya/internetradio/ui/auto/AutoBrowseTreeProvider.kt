package com.armanmaurya.internetradio.ui.auto

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.LibrarySortOption
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.repository.StationRepository
import com.armanmaurya.internetradio.service.playback.toMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media IDs for the Android Auto browse tree nodes.
 */
object AutoBrowseTree {
    const val ROOT = "AUTO_ROOT"
    const val BROWSE = "AUTO_BROWSE"
    const val RECENT = "AUTO_RECENT"
    const val LIBRARY = "AUTO_LIBRARY"
}

/**
 * Provides the browsable catalog hierarchy, styling hints, and search functionality
 * for Android Auto and external media browsers.
 */
@Singleton
class AutoBrowseTreeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: StationRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var searchResultsCache: Map<String, List<RadioStation>> = emptyMap()

    fun observeSettingsChanges(activeSessionProvider: () -> MediaLibrarySession?) {
        searchScope.launch {
            settingsRepository.appPreferencesFlow.drop(1).collect {
                activeSessionProvider()?.let { session ->
                    session.connectedControllers.forEach { controller ->
                        session.notifyChildrenChanged(controller, AutoBrowseTree.BROWSE, 30, null)
                        session.notifyChildrenChanged(controller, AutoBrowseTree.LIBRARY, 100, null)
                    }
                }
            }
        }
    }

    fun onGetLibraryRoot(params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
        val isSuggested = params?.isSuggested == true
        val rootId = if (isSuggested) AutoBrowseTree.LIBRARY else AutoBrowseTree.ROOT
        val rootTitle = if (isSuggested) getLocalizedString(R.string.auto_for_you) else getLocalizedString(R.string.app_name)

        val rootItem = MediaItem.Builder()
            .setMediaId(rootId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(rootTitle)
                    .setExtras(Bundle().apply {
                        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                        putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 3)
                    })
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    @OptIn(UnstableApi::class)
    fun onGetItem(mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        val realId = mediaId.substringAfter("|")
        val station = findStationByUuid(realId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(station.toMediaItem(context), null))
    }

    fun onGetChildren(parentId: String, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val items: List<MediaItem> = when (parentId) {
            AutoBrowseTree.ROOT -> rootChildren()
            AutoBrowseTree.BROWSE -> browseChildren()
            AutoBrowseTree.RECENT -> recentChildren()
            AutoBrowseTree.LIBRARY -> libraryChildren()
            else -> emptyList()
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
    }

    fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        searchScope.launch {
            val results = stationRepository.filterStations(
                name = query,
                order = "votes",
                reverse = true,
                limit = 30,
                hideBroken = true,
            ).getOrElse { emptyList() }

            searchResultsCache = searchResultsCache + (query to results)
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    fun onGetSearchResult(query: String, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val results = searchResultsCache[query] ?: emptyList()
        return Futures.immediateFuture(LibraryResult.ofItemList(results.map { it.toMediaItem(context, query) }, params))
    }

    fun getCachedSearchResults(query: String): List<RadioStation> = searchResultsCache[query] ?: emptyList()

    suspend fun getLibraryStationsList(): List<RadioStation> {
        val prefs = settingsRepository.appPreferencesFlow.first()
        val stations = when (prefs.librarySortOption) {
            LibrarySortOption.NAME_A_Z -> libraryRepository.getStationsByName().first()
            LibrarySortOption.NAME_Z_A -> libraryRepository.getStationsByNameDescending().first()
            LibrarySortOption.RECENTLY_PLAYED -> libraryRepository.getStationsByRecentlyPlayed().first()
            LibrarySortOption.LEAST_RECENTLY_PLAYED -> libraryRepository.getStationsByLeastRecentlyPlayed().first()
            LibrarySortOption.CUSTOM -> libraryRepository.getStationsByCustomOrder().first()
            LibrarySortOption.RECENTLY_ADDED -> libraryRepository.getAllStations().first()
            LibrarySortOption.OLDEST_ADDED -> libraryRepository.getStationsByOldestAdded().first()
        }

        return if (prefs.useFilterOnFavorites) {
            val hasCountryFilter = !prefs.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !prefs.selectedLanguage.isNullOrBlank()
            val hasTagFilter = prefs.selectedTags.isNotEmpty()

            if (!hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stations
            } else {
                stations.filter { station ->
                    val countryMatch = !hasCountryFilter || station.countryCode == prefs.selectedCountryCode
                    val languageMatch = !hasLanguageFilter || station.language == prefs.selectedLanguage
                    val tagsMatch = !hasTagFilter || prefs.selectedTags.any { it in station.tags }
                    countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stations
        }
    }

    suspend fun getBrowseStationsList(): List<RadioStation> {
        val prefs = settingsRepository.appPreferencesFlow.first()
        val filteredStations = stationRepository.filterStations(
            countryCode = prefs.selectedCountryCode,
            language = prefs.selectedLanguage,
            tagList = prefs.selectedTags.joinToString(","),
            order = prefs.order,
            reverse = prefs.reverse,
            limit = 30,
            hideBroken = true
        ).getOrElse { emptyList() }

        return if (filteredStations.isEmpty()) {
            stationRepository.filterStations(
                order = "votes",
                reverse = true,
                limit = 30,
                hideBroken = true
            ).getOrElse { emptyList() }
        } else {
            filteredStations
        }
    }

    fun findStationByUuid(uuid: String): RadioStation? = runBlocking {
        recentRepository.getStationById(uuid)
            ?: libraryRepository.getAllStations().first().find { it.stationUuid == uuid }
            ?: stationRepository.filterStations(order = "votes", reverse = true, limit = 30)
                .getOrNull()?.find { it.stationUuid == uuid }
    }

    private fun rootChildren(): List<MediaItem> = listOf(
        buildTabItem(AutoBrowseTree.LIBRARY, getLocalizedString(R.string.home_tab_library)),
        buildTabItem(AutoBrowseTree.BROWSE, getLocalizedString(R.string.home_tab_browse)),
        buildTabItem(AutoBrowseTree.RECENT, getLocalizedString(R.string.home_tab_recent)),
    )

    private fun browseChildren(): List<MediaItem> = runBlocking {
        getBrowseStationsList().map { it.toMediaItem(context, AutoBrowseTree.BROWSE) }
    }

    private fun recentChildren(): List<MediaItem> = runBlocking {
        recentRepository.getAllRecent().first().map { it.toMediaItem(context, AutoBrowseTree.RECENT) }
    }

    private fun libraryChildren(): List<MediaItem> = runBlocking {
        getLibraryStationsList().map { it.toMediaItem(context, AutoBrowseTree.LIBRARY) }
    }

    private fun buildTabItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(title)
                    .setSubtitle("")
                    .setExtras(Bundle().apply {
                        putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
                    })
                    .build()
            )
            .build()

    private fun getLocalizedString(@StringRes resId: Int): String {
        val language = runBlocking { settingsRepository.getSavedAppLanguage() } ?: "System"
        val locale = if (language == "System" || language.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(language)
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)
        return localizedContext.getString(resId)
    }
}
