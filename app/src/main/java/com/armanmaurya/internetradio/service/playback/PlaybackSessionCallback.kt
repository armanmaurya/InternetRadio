package com.armanmaurya.internetradio.service.playback

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.auto.AutoBrowseTree
import com.armanmaurya.internetradio.ui.auto.AutoBrowseTreeProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSessionCallback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val autoBrowseTreeProvider: AutoBrowseTreeProvider,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
) : MediaLibrarySession.Callback {

    companion object {
        /** Custom command sent when the user taps the heart button in Android Auto. */
        val COMMAND_TOGGLE_LIBRARY = SessionCommand("TOGGLE_LIBRARY", Bundle.EMPTY)
        /** Custom command to apply volume boost above 100%. */
        val COMMAND_SET_VOLUME_BOOST = SessionCommand("SET_VOLUME_BOOST", Bundle.EMPTY)
    }

    var onVolumeBoostChanged: ((Float) -> Unit)? = null
    var activeSession: MediaLibrarySession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        autoBrowseTreeProvider.observeSettingsChanges { activeSession }
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val currentStation = session.player.currentMediaItem?.localConfiguration?.tag as? RadioStation
        val isFav = currentStation?.let {
            runBlocking { libraryRepository.isStationInLibraryDirect(it.stationUuid) }
        } ?: false

        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(COMMAND_TOGGLE_LIBRARY)
            .add(COMMAND_SET_VOLUME_BOOST)
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setCustomLayout(buildLibraryButton(isFav))
            .build()
    }

    // ─── Library Root & Browsing (Delegated to AutoBrowseTreeProvider) ─────────

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = autoBrowseTreeProvider.onGetLibraryRoot(params)

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = autoBrowseTreeProvider.onGetItem(mediaId)

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = autoBrowseTreeProvider.onGetChildren(parentId, params)

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = autoBrowseTreeProvider.onSearch(session, browser, query, params)

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = autoBrowseTreeProvider.onGetSearchResult(query, params)

    // ─── Custom Commands ──────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == COMMAND_SET_VOLUME_BOOST.customAction) {
            val boost = args.getFloat("KEY_BOOST", 0f)
            onVolumeBoostChanged?.invoke(boost)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        if (customCommand.customAction != COMMAND_TOGGLE_LIBRARY.customAction) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        val mediaItem = session.player.currentMediaItem
        val uuid = mediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))

        scope.launch {
            val station = (mediaItem.localConfiguration?.tag as? RadioStation)
                ?: autoBrowseTreeProvider.findStationByUuid(uuid.substringAfter("|"))
                ?: return@launch
            val wasFav = libraryRepository.isStationInLibraryDirect(station.stationUuid)
            if (wasFav) libraryRepository.removeStationFromLibrary(station.stationUuid)
            else libraryRepository.addStationToLibrary(station)
            session.setCustomLayout(buildLibraryButton(!wasFav))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    fun updateLibraryButton(mediaId: String?) {
        val session = activeSession ?: return
        scope.launch {
            val isFav = mediaId?.let { libraryRepository.isStationInLibraryDirect(it) } ?: false
            session.setCustomLayout(buildLibraryButton(isFav))
        }
    }

    @Suppress("DEPRECATION")
    private fun buildLibraryButton(isFavorite: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(if (isFavorite) getLocalizedString(R.string.home_remove_from_library) else getLocalizedString(R.string.home_add_to_library))
            .setIconResId(
                if (isFavorite) R.drawable.ic_auto_bookmark
                else R.drawable.ic_auto_bookmark_border
            )
            .setSessionCommand(COMMAND_TOGGLE_LIBRARY)
            .build()
    )

    // ─── Playback Resumption ──────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            try {
                val prefs = settingsRepository.appPreferencesFlow.first()
                if (!prefs.autoPlayOnStart) {
                    future.setException(UnsupportedOperationException("Auto Play on Start is disabled"))
                    return@launch
                }

                val station = recentRepository.getAllRecent().first().firstOrNull()
                if (station != null) {
                    val libraryStations = autoBrowseTreeProvider.getLibraryStationsList()
                    val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == station.stationUuid }

                    if (libraryIndex != -1) {
                        playerController.syncAndroidAutoContext(libraryStations, libraryIndex, PlaybackSource.Library)
                        val resolvedItems = libraryStations.map { it.toMediaItem(context) }
                        future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, libraryIndex, 0L))
                    } else {
                        val recentStations = recentRepository.getAllRecent().first()
                        val recentIndex = recentStations.indexOfFirst { it.stationUuid == station.stationUuid }.coerceAtLeast(0)

                        playerController.syncAndroidAutoContext(recentStations, recentIndex, PlaybackSource.Recent)
                        val resolvedItems = recentStations.map { it.toMediaItem(context) }
                        future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, recentIndex, 0L))
                    }
                } else {
                    future.setException(UnsupportedOperationException("No recent station found"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    // ─── Media Items Resolution ───────────────────────────────────────────────

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val resolved = mediaItems.map { item ->
            if (item.localConfiguration?.uri != null) return@map item
            val station = autoBrowseTreeProvider.findStationByUuid(item.mediaId.substringAfter("|")) ?: return@map item
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
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                scope.launch {
                    try {
                        val stations = when (parentId) {
                            AutoBrowseTree.LIBRARY -> autoBrowseTreeProvider.getLibraryStationsList()
                            AutoBrowseTree.RECENT -> recentRepository.getAllRecent().first()
                            AutoBrowseTree.BROWSE -> autoBrowseTreeProvider.getBrowseStationsList()
                            else -> autoBrowseTreeProvider.getCachedSearchResults(parentId)
                        }

                        val realId = requestedId.substringAfter("|")
                        var index = stations.indexOfFirst { it.stationUuid == realId }
                        if (index == -1) index = 0

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
                        val resolvedItems = stations.map { it.toMediaItem(context) }
                        future.set(MediaSession.MediaItemsWithStartPosition(resolvedItems, index, startPositionMs))
                    } catch (e: Exception) {
                        val resolved = autoBrowseTreeProvider.findStationByUuid(requestedId.substringAfter("|"))?.toMediaItem(context) ?: mediaItems.first()
                        future.set(MediaSession.MediaItemsWithStartPosition(listOf(resolved), 0, startPositionMs))
                    }
                }
                return future
            }
        }

        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) item
                else autoBrowseTreeProvider.findStationByUuid(item.mediaId.substringAfter("|"))?.toMediaItem(context) ?: item
            }
            future.set(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
        }
        return future
    }

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
