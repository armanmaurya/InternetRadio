package com.armanmaurya.internetradio.player

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.FavoriteRepository
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

// Media IDs for the browse tree nodes
object AutoBrowseTree {
    const val ROOT = "AUTO_ROOT"
    const val BROWSE = "AUTO_BROWSE"
    const val RECENT = "AUTO_RECENT"
    const val FAVORITES = "AUTO_FAVORITES"
}


@Singleton
class AutoMediaLibraryCallback @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: StationRepository,
    private val settingsRepository: SettingsRepository,
) : MediaLibrarySession.Callback {

    companion object {
        /** Custom command sent when the user taps the heart button in Android Auto. */
        val COMMAND_TOGGLE_FAVORITE = SessionCommand("TOGGLE_FAVORITE", Bundle.EMPTY)
    }

    /** Background scope used for async search network calls and DB operations. */
    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Simple in-memory cache: query string → search results. */
    @Volatile private var searchResultsCache: Map<String, List<RadioStation>> = emptyMap()

    /**
     * Reference to the active [MediaLibrarySession], set by [PlaybackService] after the
     * session is built. Used to push custom layout updates when the station changes.
     */
    var activeSession: MediaLibrarySession? = null

    init {
        // Observe settings changes to instantly refresh the Browse tab if it's currently open
        searchScope.launch {
            // Drop the initial emission since we only care about *changes* after startup
            settingsRepository.appPreferencesFlow.drop(1).collect {
                activeSession?.let { session ->
                    session.connectedControllers.forEach { controller ->
                        session.notifyChildrenChanged(controller, AutoBrowseTree.BROWSE, 30, null)
                    }
                }
            }
        }
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Determine the initial heart state from whatever station is already loaded
        val currentStation = session.player.currentMediaItem?.localConfiguration?.tag as? RadioStation
        val isFav = currentStation?.let {
            runBlocking { favoriteRepository.isFavoriteDirect(it.stationUuid) }
        } ?: false

        // Grant the custom favourite command in addition to all default commands
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(COMMAND_TOGGLE_FAVORITE)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setCustomLayout(buildFavoriteButton(isFav))
            .build()
    }

    // ─── Root ────────────────────────────────────────────────────────────────

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val isSuggested = params?.isSuggested == true
        val rootId = if (isSuggested) AutoBrowseTree.FAVORITES else AutoBrowseTree.ROOT
        val rootTitle = if (isSuggested) "For You" else "Internet Radio"

        val rootItem = MediaItem.Builder()
            .setMediaId(rootId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(rootTitle)
                    .setExtras(Bundle().apply {
                        // Required: enables content style hints for this tree
                        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                        // Signal search support → car UI shows the search icon
                        putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                        // Tabs (browsable children of root) shown as category list
                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 3)
                    })
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    // ─── Single item lookup ───────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val station = findStationByUuid(mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(station.toMediaItem(), null))
    }

    // ─── Children ────────────────────────────────────────────────────────────

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val items: List<MediaItem> = when (parentId) {
            AutoBrowseTree.ROOT -> rootChildren()
            AutoBrowseTree.BROWSE -> browseChildren()
            AutoBrowseTree.RECENT -> recentChildren()
            AutoBrowseTree.FAVORITES -> favoritesChildren()
            else -> emptyList()
        }
        return Futures.immediateFuture(
            LibraryResult.ofItemList(items, params)
        )
    }

    // ─── Custom command: Favourite toggle ─────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != COMMAND_TOGGLE_FAVORITE.customAction) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        val uuid = session.player.currentMediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))

        searchScope.launch {
            val station = findStationByUuid(uuid) ?: return@launch
            val wasFav = favoriteRepository.isFavoriteDirect(station.stationUuid)
            if (wasFav) favoriteRepository.removeFavorite(station.stationUuid)
            else favoriteRepository.addFavorite(station)
            // Push updated icon to every connected controller
            session.setCustomLayout(buildFavoriteButton(!wasFav))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Called by [PlaybackService] whenever the playing station changes.
     * Looks up the new station's favourite status and refreshes the heart icon.
     */
    fun updateFavoriteButton(mediaId: String?) {
        val session = activeSession ?: return
        searchScope.launch {
            val isFav = mediaId?.let { favoriteRepository.isFavoriteDirect(it) } ?: false
            session.setCustomLayout(buildFavoriteButton(isFav))
        }
    }

    /** Builds a one-item custom layout list with the correct heart icon. */
    @Suppress("DEPRECATION") // CommandButton.Builder() no-arg is safe pre-1.4 fallback
    private fun buildFavoriteButton(isFavorite: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remove from Favourites" else "Add to Favourites")
            .setIconResId(
                if (isFavorite) R.drawable.ic_auto_favorite
                else R.drawable.ic_auto_favorite_border
            )
            .setSessionCommand(COMMAND_TOGGLE_FAVORITE)
            .build()
    )

    // ─── Search ───────────────────────────────────────────────────────────────

    /**
     * Called by Android Auto when the user types a search query.
     * We launch an async network request so we don't block the main thread,
     * then notify the car host when results are ready via [MediaLibrarySession.notifySearchResultChanged].
     */
    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        searchScope.launch {
            val results = stationRepository.filterStations(
                name = query,
                order = "votes",
                reverse = true,
                limit = 30,
                hideBroken = true,
            ).getOrElse { emptyList() }

            // Cache results so onGetSearchResult can return them synchronously
            searchResultsCache = searchResultsCache + (query to results)

            // Tell the car host how many results are available — it will then
            // call onGetSearchResult to fetch the actual items
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        // Immediately acknowledge the search request (results arrive asynchronously)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    /** Returns the cached results for a previously executed search query. */
    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val results = searchResultsCache[query] ?: emptyList()
        return Futures.immediateFuture(LibraryResult.ofItemList(results.map { it.toMediaItem() }, params))
    }

    // ─── Playback request from car ────────────────────────────────────────────

    /**
     * The car host sends a MediaItem that may only have a mediaId (no URI).
     * We resolve the full item with stream URI from our local cache so ExoPlayer
     * can actually play it.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val resolved = mediaItems.map { item ->
            // If the item already has a URI, use it as-is
            if (item.localConfiguration?.uri != null) return@map item

            // Otherwise look up the station by UUID and reconstruct the MediaItem
            val station = findStationByUuid(item.mediaId) ?: return@map item
            station.toMediaItem()
        }
        return Futures.immediateFuture(resolved)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun rootChildren(): List<MediaItem> = listOf(
        buildTabItem(
            id = AutoBrowseTree.FAVORITES,
            title = "Favorites",
            subtitle = "Your favourite stations",
        ),
        buildTabItem(
            id = AutoBrowseTree.BROWSE,
            title = "Browse",
            subtitle = "Top stations",
        ),
        buildTabItem(
            id = AutoBrowseTree.RECENT,
            title = "Recent",
            subtitle = "Recently played",
        ),
    )

    private fun browseChildren(): List<MediaItem> = runBlocking {
        val prefs = settingsRepository.appPreferencesFlow.first()
        
        val filteredStations = stationRepository.filterStations(
            countryCode = prefs.selectedCountryCode,
            language = prefs.selectedLanguage,
            tagList = prefs.selectedTags.joinToString(","),
            order = prefs.order,
            reverse = prefs.reverse,
            limit = 30,
            hideBroken = true
        ).getOrElse { emptyList() }.map { it.toMediaItem() }

        // 2. Fallback to global top stations if the user's filters returned 0 results
        if (filteredStations.isEmpty()) {
            stationRepository.filterStations(
                order = "votes",
                reverse = true,
                limit = 30,
                hideBroken = true
            ).getOrElse { emptyList() }.map { it.toMediaItem() }
        } else {
            filteredStations
        }
    }

    private fun recentChildren(): List<MediaItem> = runBlocking {
        recentRepository.getAllRecent().first().map { it.toMediaItem() }
    }

    private fun favoritesChildren(): List<MediaItem> = runBlocking {
        favoriteRepository.getAllFavorites().first().map { it.toMediaItem() }
    }

    private fun buildTabItem(id: String, title: String, subtitle: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setExtras(Bundle().apply {
                        // Stations inside this tab display as a GRID (artwork cards)
                        // Value 2 = CONTENT_STYLE_GRID_ITEM_HINT_VALUE
                        putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
                    })
                    .build()
            )
            .build()

    /**
     * Searches all available sources for a station matching [uuid].
     * Checked in order: recents → favorites → top-stations cache.
     */
    private fun findStationByUuid(uuid: String): RadioStation? = runBlocking {
        recentRepository.getStationById(uuid)
            ?: favoriteRepository.getAllFavorites().first().find { it.stationUuid == uuid }
            ?: stationRepository.filterStations(order = "votes", reverse = true, limit = 30)
                .getOrNull()?.find { it.stationUuid == uuid }
    }
}

// ─── Extension helpers ────────────────────────────────────────────────────────

/**
 * Converts a [RadioStation] domain object into a playable [MediaItem] for both
 * phone MediaSession notifications and Android Auto's now-playing card.
 *
 * The [MediaMetadata.artworkUri] is populated from the station's favicon so
 * Android Auto shows the station logo as the album-art thumbnail in the
 * now-playing card — no extra UI code needed.
 */
/** App logo URI — used as fallback artwork for stations with no/broken favicon. */
private val APP_LOGO_URI: Uri =
    Uri.parse("android.resource://com.armanmaurya.internetradio/mipmap/ic_launcher")

fun RadioStation.toMediaItem(): MediaItem {
    // Use the station favicon if present, otherwise fall back to the app logo so
    // Android Auto grid cards always show meaningful artwork instead of a
    // generic music-note placeholder.
    val artworkUri = favicon.takeIf { it.isNotBlank() }
        ?.let { Uri.parse(it) }
        ?: APP_LOGO_URI

    return MediaItem.Builder()
        .setMediaId(stationUuid)
        .setUri(urlResolved)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    // Carry the station name so onMediaMetadataChanged can
                    // distinguish ICY track updates from station-name echoes
                    putString("stationName", name)
                })
                .build()
        )
        .setTag(this)
        .build()
}
