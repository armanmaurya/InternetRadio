package com.armanmaurya.internetradio.ui.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.utils.extractPaletteFromBitmap
import com.armanmaurya.internetradio.core.utils.resolveArtwork
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.service.PlaybackService
import com.armanmaurya.internetradio.ui.widget.components.PlayerContent
import com.armanmaurya.internetradio.ui.widget.state.NowPlayingWidgetState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recentRepository(): com.armanmaurya.internetradio.domain.repository.RecentRepository
    fun settingsRepository(): com.armanmaurya.internetradio.domain.repository.SettingsRepository
    fun widgetController(): WidgetController
}

class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val savedAlpha = try {
            entryPoint.settingsRepository().appPreferencesFlow.first().widgetBackgroundAlpha
        } catch (e: Exception) {
            1.0f
        }
        val initialRecent = try {
            entryPoint.recentRepository().getAllRecent().first().firstOrNull()
        } catch (e: Exception) {
            null
        }
        val widgetController = entryPoint.widgetController()

        provideContent {
            val prefs = currentState<Preferences>()
            val latestPayload = widgetController.latestPayload
            val widgetAlpha = prefs[WidgetPreferenceKeys.BG_ALPHA] ?: latestPayload?.bgAlpha ?: savedAlpha
            val isServiceRunning = PlaybackService.isRunning
            val latest = if (isServiceRunning) latestPayload else null

            val savedTitle = prefs[WidgetPreferenceKeys.TITLE] ?: latest?.title
            val nothingPlaying = context.getString(R.string.widget_nothing_playing)

            var lastStation by remember { mutableStateOf(initialRecent) }
            var cleanedStationUuid by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                entryPoint.recentRepository().getAllRecent().collect { list ->
                    lastStation = list.firstOrNull()
                }
            }

            val isPlaying = prefs[WidgetPreferenceKeys.IS_PLAYING] ?: latest?.isPlaying ?: false
            val isCoverArtFetched = if (isPlaying) (prefs[WidgetPreferenceKeys.IS_COVER_ART_FETCHED] ?: latest?.isCoverArtFetched ?: false) else false

            LaunchedEffect(savedTitle, isPlaying, lastStation) {
                if (isPlaying) {
                    if (prefs[WidgetPreferenceKeys.TITLE] == null && isServiceRunning) {
                        PlaybackService.requestWidgetUpdate()
                    }
                } else {
                    val currentUuid = lastStation?.stationUuid ?: ""
                    val expectedTitle = lastStation?.name ?: nothingPlaying
                    val expectedStationName = lastStation?.name ?: ""
                    val isPrefsStale = prefs[WidgetPreferenceKeys.TITLE] != expectedTitle ||
                        prefs[WidgetPreferenceKeys.STATION_NAME] != expectedStationName ||
                        prefs[WidgetPreferenceKeys.IS_PLAYING] == true ||
                        !prefs[WidgetPreferenceKeys.ARTIST].isNullOrBlank() ||
                        prefs[WidgetPreferenceKeys.IS_COVER_ART_FETCHED] == true

                    if (cleanedStationUuid != currentUuid && isPrefsStale) {
                        cleanedStationUuid = currentUuid
                        widgetController.cleanStaleWidgetState(
                            stationName = lastStation?.name,
                            favicon = lastStation?.favicon,
                        )
                    }
                }
            }

            val stationName = if (isPlaying) {
                prefs[WidgetPreferenceKeys.STATION_NAME]?.takeIf { it.isNotBlank() } ?: latest?.stationName ?: lastStation?.name
            } else {
                lastStation?.name
            }

            val title = if (isPlaying) {
                savedTitle?.takeIf { it.isNotBlank() && it != "Nothing playing" && it != nothingPlaying }
                    ?: stationName
                    ?: nothingPlaying
            } else {
                lastStation?.name ?: nothingPlaying
            }

            val artist = if (isPlaying) {
                prefs[WidgetPreferenceKeys.ARTIST] ?: latest?.artist ?: ""
            } else {
                ""
            }

            val artworkUrl = if (isPlaying) {
                (prefs[WidgetPreferenceKeys.ARTWORK_URL] ?: latest?.artworkUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else {
                lastStation?.favicon
            }

            val stationThumbnailUrl = if (isCoverArtFetched) {
                (prefs[WidgetPreferenceKeys.STATION_THUMBNAIL_URL] ?: latest?.stationThumbnailUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else null

            val hasNext = if (isPlaying) (prefs[WidgetPreferenceKeys.HAS_NEXT] ?: latest?.hasNext ?: false) else false
            val hasPrev = if (isPlaying) (prefs[WidgetPreferenceKeys.HAS_PREV] ?: latest?.hasPrev ?: false) else false

            // Pre-computed atomic palette colors (Spotify pattern)
            val precomputedBgColorInt = prefs[WidgetPreferenceKeys.BG_COLOR] ?: latest?.bgColor
            val precomputedTitleColorInt = prefs[WidgetPreferenceKeys.TITLE_COLOR] ?: latest?.titleColor
            val precomputedArtistColorInt = prefs[WidgetPreferenceKeys.ARTIST_COLOR] ?: latest?.artistColor
            val precomputedDayBgColorInt = prefs[WidgetPreferenceKeys.DAY_BG_COLOR] ?: latest?.dayBgColor
            val precomputedDayTitleColorInt = prefs[WidgetPreferenceKeys.DAY_TITLE_COLOR] ?: latest?.dayTitleColor
            val precomputedDayArtistColorInt = prefs[WidgetPreferenceKeys.DAY_ARTIST_COLOR] ?: latest?.dayArtistColor

            var artwork by remember(artworkUrl) { mutableStateOf<ImageProvider?>(null) }
            var stationThumbnail by remember(stationThumbnailUrl) { mutableStateOf<ImageProvider?>(null) }

            // Dynamic palette fallback if prefs didn't have precomputed colors (e.g. legacy/initial load)
            var dynamicBgColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var dynamicTitleColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var dynamicArtistColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }

            LaunchedEffect(artworkUrl) {
                if (artworkUrl != null) {
                    val bmp = resolveArtwork(context, artworkUrl)
                    if (bmp != null) {
                        artwork = ImageProvider(bmp)
                        if (precomputedBgColorInt == null) {
                            val extracted = extractPaletteFromBitmap(bmp)
                            if (extracted != null) {
                                dynamicBgColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayBackgroundColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.backgroundColor)
                                )
                                dynamicTitleColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayTitleTextColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.titleTextColor)
                                )
                                dynamicArtistColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayArtistTextColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.artistTextColor)
                                )
                            }
                        }
                    } else {
                        artwork = null
                    }
                } else {
                    artwork = null
                }
            }

            LaunchedEffect(stationThumbnailUrl) {
                if (stationThumbnailUrl != null) {
                    val bmp = resolveArtwork(context, stationThumbnailUrl, maxDimension = 96)
                    stationThumbnail = if (bmp != null) ImageProvider(bmp) else null
                } else {
                    stationThumbnail = null
                }
            }

            val bgColor = if (precomputedBgColorInt != null && precomputedDayBgColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayBgColorInt).copy(alpha = widgetAlpha),
                    night = androidx.compose.ui.graphics.Color(precomputedBgColorInt).copy(alpha = widgetAlpha)
                )
            } else if (precomputedBgColorInt != null) {
                androidx.glance.unit.ColorProvider(
                    androidx.compose.ui.graphics.Color(precomputedBgColorInt).copy(alpha = widgetAlpha)
                )
            } else {
                val base = dynamicBgColor ?: GlanceTheme.colors.widgetBackground
                val resolved = base.getColor(context)
                androidx.glance.unit.ColorProvider(resolved.copy(alpha = widgetAlpha))
            }

            val titleColor = if (precomputedTitleColorInt != null && precomputedDayTitleColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayTitleColorInt),
                    night = androidx.compose.ui.graphics.Color(precomputedTitleColorInt)
                )
            } else if (precomputedTitleColorInt != null) {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(precomputedTitleColorInt))
            } else {
                dynamicTitleColor ?: GlanceTheme.colors.onSurface
            }

            val artistColor = if (precomputedArtistColorInt != null && precomputedDayArtistColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayArtistColorInt),
                    night = androidx.compose.ui.graphics.Color(precomputedArtistColorInt)
                )
            } else if (precomputedArtistColorInt != null) {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(precomputedArtistColorInt))
            } else {
                dynamicArtistColor ?: GlanceTheme.colors.onSurfaceVariant
            }

            val state = NowPlayingWidgetState(
                title               = title,
                artist              = artist,
                stationName         = stationName,
                artworkUrl          = artworkUrl,
                artwork             = artwork,
                stationThumbnailUrl = stationThumbnailUrl,
                stationThumbnail    = stationThumbnail,
                isCoverArtFetched   = isCoverArtFetched,
                isPlaying           = isPlaying,
                hasNext             = hasNext,
                hasPrev             = hasPrev,
                backgroundColor     = bgColor,
                titleColor          = titleColor,
                artistColor         = artistColor,
            )

            GlanceTheme {
                PlayerContent(
                    state = state,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(bgColor)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}
