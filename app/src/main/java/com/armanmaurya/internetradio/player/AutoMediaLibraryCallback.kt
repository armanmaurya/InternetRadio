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
import com.armanmaurya.internetradio.data.repository.LibraryRepository
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
    const val LIBRARY = "AUTO_LIBRARY"
}


@Singleton
class AutoMediaLibraryCallback @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: StationRepository,
    private val settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
) : MediaLibrarySession.Callback {

    companion object {
        /** Custom command sent when the user taps the heart button in Android Auto. */
        val COMMAND_TOGGLE_LIBRARY = SessionCommand("TOGGLE_LIBRARY", Bundle.EMPTY)
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
            runBlocking { libraryRepository.isStationInLibraryDirect(it.stationUuid) }
        } ?: false

        // Grant the custom library command in addition to all default commands
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(COMMAND_TOGGLE_LIBRARY)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setCustomLayout(buildLibraryButton(isFav))
            .build()
    }

    // ─── Root ────────────────────────────────────────────────────────────────

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val isSuggested = params?.isSuggested == true
        val rootId = if (isSuggested) AutoBrowseTree.LIBRARY else AutoBrowseTree.ROOT
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
        val realId = mediaId.substringAfter("|")
        val station = findStationByUuid(realId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(station.toMediaItem(context), null))
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
            AutoBrowseTree.LIBRARY -> libraryChildren()
            else -> emptyList()
        }
        return Futures.immediateFuture(
            LibraryResult.ofItemList(items, params)
        )
    }

    // ─── Custom command: Library toggle ─────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != COMMAND_TOGGLE_LIBRARY.customAction) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        val uuid = session.player.currentMediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))

        searchScope.launch {
            val station = findStationByUuid(uuid) ?: return@launch
            val wasFav = libraryRepository.isStationInLibraryDirect(station.stationUuid)
            if (wasFav) libraryRepository.removeStationFromLibrary(station.stationUuid)
            else libraryRepository.addStationToLibrary(station)
            // Push updated icon to every connected controller
            session.setCustomLayout(buildLibraryButton(!wasFav))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Called by [PlaybackService] whenever the playing station changes.
     * Looks up the new station's library status and refreshes the heart icon.
     */
    fun updateLibraryButton(mediaId: String?) {
        val session = activeSession ?: return
        searchScope.launch {
            val isFav = mediaId?.let { libraryRepository.isStationInLibraryDirect(it) } ?: false
            session.setCustomLayout(buildLibraryButton(isFav))
        }
    }

    /** Builds a one-item custom layout list with the correct heart icon. */
    @Suppress("DEPRECATION") // CommandButton.Builder() no-arg is safe pre-1.4 fallback
    private fun buildLibraryButton(isFavorite: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remove from Library" else "Add to Library")
            .setIconResId(
                if (isFavorite) R.drawable.ic_auto_favorite
                else R.drawable.ic_auto_favorite_border
            )
            .setSessionCommand(COMMAND_TOGGLE_LIBRARY)
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
        return Futures.immediateFuture(LibraryResult.ofItemList(results.map { it.toMediaItem(context, query) }, params))
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
            val station = findStationByUuid(item.mediaId.substringAfter("|")) ?: return@map item
            station.toMediaItem(context)
        }
        return Futures.immediateFuture(resolved)
    }

    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (mediaItems.size == 1) {
            val requestedId = mediaItems.first().mediaId
            val parentId = if (requestedId.contains("|")) requestedId.substringBefore("|") else null
            
            if (parentId != null) {
                val future = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                searchScope.launch {
                    try {
                        val stations = when (parentId) {
                            AutoBrowseTree.LIBRARY -> libraryRepository.getAllStations().first()
                            AutoBrowseTree.RECENT -> recentRepository.getAllRecent().first()
                            AutoBrowseTree.BROWSE -> getBrowseStationsList()
                            else -> searchResultsCache[parentId] ?: emptyList()
                        }
                        
                        val realId = requestedId.substringAfter("|")
                        var index = stations.indexOfFirst { it.stationUuid == realId }
                        if (index == -1) index = 0
                        
                        // Sync with PlayerController so infinite scrolling works
                        val prefs = settingsRepository.appPreferencesFlow.first()
                        val source = when (parentId) {
                            AutoBrowseTree.LIBRARY -> PlaybackSource.Library
                            AutoBrowseTree.RECENT -> PlaybackSource.Recent
                            AutoBrowseTree.BROWSE -> PlaybackSource.Browse(
                                name = "", 
                                countryCode = prefs.selectedCountryCode, 
                                language = prefs.selectedLanguage,
                                tagList = prefs.selectedTags.joinToString(","), 
                                order = prefs.order, 
                                reverse = prefs.reverse
                            )
                            else -> PlaybackSource.None
                        }
                        
                        playerController.syncAndroidAutoContext(stations, index, source)
                        
                        // Return un-piped media items to the player
                        val resolvedItems = stations.map { it.toMediaItem(context) }
                        future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, index, startPositionMs))
                    } catch (e: Exception) {
                        val resolved = findStationByUuid(requestedId.substringAfter("|"))?.toMediaItem(context) ?: mediaItems.first()
                        future.set(MediaSession.MediaItemsWithStartPosition(listOf(resolved), 0, startPositionMs))
                    }
                }
                return future
            }
        }
        
        // Default behavior for unhandled cases
        val future = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        searchScope.launch {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) item
                else findStationByUuid(item.mediaId.substringAfter("|"))?.toMediaItem(context) ?: item
            }
            future.set(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
        }
        return future
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun rootChildren(): List<MediaItem> = listOf(
        buildTabItem(
            id = AutoBrowseTree.LIBRARY,
            title = "Library",
            subtitle = "Your library stations",
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

    private suspend fun getBrowseStationsList(): List<RadioStation> {
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

        // Fallback to global top stations if the user's filters returned 0 results
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

    private fun browseChildren(): List<MediaItem> = runBlocking {
        getBrowseStationsList().map { it.toMediaItem(context, AutoBrowseTree.BROWSE) }
    }

    private fun recentChildren(): List<MediaItem> = runBlocking {
        recentRepository.getAllRecent().first().map { it.toMediaItem(context, AutoBrowseTree.RECENT) }
    }

    private fun libraryChildren(): List<MediaItem> = runBlocking {
        libraryRepository.getAllStations().first().map { it.toMediaItem(context, AutoBrowseTree.LIBRARY) }
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
     * Checked in order: recents → library → top-stations cache.
     */
    private fun findStationByUuid(uuid: String): RadioStation? = runBlocking {
        recentRepository.getStationById(uuid)
            ?: libraryRepository.getAllStations().first().find { it.stationUuid == uuid }
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

fun RadioStation.toMediaItem(context: android.content.Context, parentId: String? = null): MediaItem {
    // Use the station favicon if present. If it's an SVG, proxy it through our SvgProxyProvider 
    // so Android Auto can receive it as a PNG stream. Otherwise fall back to the app logo.
    val artworkUriStr = if (favicon.endsWith(".svg", ignoreCase = true)) {
        SvgProxyProvider.createProxyUri(context, favicon)
    } else {
        favicon.takeIf { it.isNotBlank() }
    }
    val artworkUri = artworkUriStr?.let { Uri.parse(it) } ?: APP_LOGO_URI

    val id = if (parentId != null) "$parentId|$stationUuid" else stationUuid

    return MediaItem.Builder()
        .setMediaId(id)
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
