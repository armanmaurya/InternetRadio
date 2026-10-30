package com.armanmaurya.internetradio.data.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.PersistableBundle
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.armanmaurya.internetradio.domain.controller.ShortcutController
import com.armanmaurya.internetradio.domain.controller.ShortcutController.Companion.ACTION_PLAY_STATION
import com.armanmaurya.internetradio.domain.controller.ShortcutController.Companion.EXTRA_STATION_UUID
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository
) : ShortcutController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var isInitialized = false

    @OptIn(FlowPreview::class)
    override fun initialize() {
        if (isInitialized) return
        isInitialized = true

        scope.launch {
            libraryRepository.getAllStations()
                .debounce(SYNC_DEBOUNCE_MS)
                .collect { stations ->
                    mutex.withLock { syncWithLibrary(stations) }
                }
        }
    }

    override suspend fun hasShortcut(stationUuid: String): Boolean = withContext(Dispatchers.IO) {
        pinnedShortcuts().any { it.isEnabled && it.stationUuid == stationUuid }
    }

    override fun pinStationShortcut(station: RadioStation): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        scope.launch {
            mutex.withLock {
                // A disabled id can't be pinned again, so the existing shortcut is brought back instead
                val existing = pinnedShortcuts().firstOrNull { it.stationUuid == station.stationUuid }
                if (existing != null) {
                    if (!existing.isEnabled) ShortcutManagerCompat.enableShortcuts(context, listOf(existing))
                    return@withLock
                }

                val icon = loadIcon(station.favicon)
                val shortcut = buildShortcut(
                    id = "$SHORTCUT_ID_PREFIX${station.stationUuid}",
                    station = station,
                    icon = icon ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher),
                    // Left unset when the logo failed to load, so the next sync retries it
                    storedFavicon = station.favicon.takeIf { icon != null }
                )
                ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            }
        }
        return true
    }

    override suspend fun moveStationShortcut(oldUuid: String, newUuid: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val station = libraryRepository.getLibraryStationById(newUuid) ?: return@withLock
            val shortcut = pinnedShortcuts().firstOrNull { it.stationUuid == oldUuid } ?: return@withLock
            // A sync may have disabled it while the library briefly lacked the old id
            if (!shortcut.isEnabled) ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            refreshShortcuts(listOf(shortcut to station))
        }
    }

    private suspend fun syncWithLibrary(stations: List<RadioStation>) {
        val library = stations.associateBy { it.stationUuid }
        val pinned = pinnedShortcuts()

        val removed = pinned.filter { it.isEnabled && it.stationUuid !in library }
        if (removed.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(
                context,
                removed.map { it.id },
                context.getString(R.string.shortcut_disabled_station_removed)
            )
        }

        val returned = pinned.filter { !it.isEnabled && it.stationUuid in library }
        if (returned.isNotEmpty()) {
            ShortcutManagerCompat.enableShortcuts(context, returned)
        }

        val outdated = pinned.mapNotNull { shortcut ->
            val station = library[shortcut.stationUuid] ?: return@mapNotNull null
            val isOutdated = shortcut.storedName != station.name || shortcut.storedFavicon != station.favicon
            if (isOutdated) shortcut to station else null
        }
        if (outdated.isNotEmpty()) {
            refreshShortcuts(outdated)
        }
    }

    /** Rewrites label, intent and (when the logo changed) icon, keeping each shortcut's id. */
    private suspend fun refreshShortcuts(shortcuts: List<Pair<ShortcutInfoCompat, RadioStation>>) {
        val updates = shortcuts.map { (shortcut, station) ->
            val faviconChanged = shortcut.storedFavicon != station.favicon
            // A null icon leaves the current one in place, so a logo that fails to load keeps the old icon
            val icon = if (faviconChanged) loadIcon(station.favicon) else null
            buildShortcut(
                id = shortcut.id,
                station = station,
                icon = icon,
                storedFavicon = if (!faviconChanged || icon != null) station.favicon else shortcut.storedFavicon
            )
        }
        if (!ShortcutManagerCompat.updateShortcuts(context, updates)) {
            Log.w(TAG, "Shortcut update was rate limited; it is retried on the next library change")
        }
    }

    private fun buildShortcut(
        id: String,
        station: RadioStation,
        icon: IconCompat?,
        storedFavicon: String?
    ): ShortcutInfoCompat {
        val intent = Intent(context, MobileActivity::class.java).apply {
            action = ACTION_PLAY_STATION
            putExtra(EXTRA_STATION_UUID, station.stationUuid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val extras = PersistableBundle().apply {
            putString(EXTRA_STORED_NAME, station.name)
            putString(EXTRA_STORED_FAVICON, storedFavicon)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(station.name)
            .setLongLabel(station.name)
            .setIntent(intent)
            .setExtras(extras)
            .apply { icon?.let { setIcon(it) } }
            .build()
    }

    /** The station logo as an icon, the app icon when there is no logo, or null when it fails to load. */
    private suspend fun loadIcon(favicon: String): IconCompat? {
        if (favicon.isBlank()) return IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val targetUrl = if (favicon.endsWith(".svg", ignoreCase = true)) {
            SvgProxyProvider.createProxyUri(context, favicon)
        } else {
            favicon
        }

        return try {
            val request = ImageRequest.Builder(context)
                .data(targetUrl)
                .size(ICON_SIZE_PX)
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
            val bitmap = (result.image as? BitmapImage)?.bitmap
                ?: (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                ?: return null
            val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }
            IconCompat.createWithAdaptiveBitmap(softwareBitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load shortcut icon", e)
            null
        }
    }

    private fun pinnedShortcuts(): List<ShortcutInfoCompat> =
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { it.id.startsWith(SHORTCUT_ID_PREFIX) }

    private val ShortcutInfoCompat.stationUuid: String?
        get() = intent.getStringExtra(EXTRA_STATION_UUID)

    private val ShortcutInfoCompat.storedName: String?
        get() = extras?.getString(EXTRA_STORED_NAME)

    private val ShortcutInfoCompat.storedFavicon: String?
        get() = extras?.getString(EXTRA_STORED_FAVICON)

    companion object {
        private const val TAG = "ShortcutController"
        private const val SHORTCUT_ID_PREFIX = "station_"
        private const val EXTRA_STORED_NAME = "stored_name"
        private const val EXTRA_STORED_FAVICON = "stored_favicon"
        private const val ICON_SIZE_PX = 192
        private const val SYNC_DEBOUNCE_MS = 500L
    }
}
