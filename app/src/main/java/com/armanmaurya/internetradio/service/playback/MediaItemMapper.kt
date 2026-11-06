package com.armanmaurya.internetradio.service.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.google.gson.Gson
import com.google.gson.JsonParseException

/** Holds the full station as JSON. Unlike the tag, extras reach the app's controller. */
private const val EXTRA_STATION_JSON = "stationJson"

private val gson = Gson()

fun RadioStation.toJson(): String = gson.toJson(this)

fun MediaItem.toRadioStation(): RadioStation? {
    (localConfiguration?.tag as? RadioStation)?.let { return it }
    val json = mediaMetadata.extras?.getString(EXTRA_STATION_JSON) ?: return null
    return try {
        gson.fromJson(json, RadioStation::class.java)
    } catch (_: JsonParseException) {
        null
    }
}

/** App logo URI — used as fallback artwork for stations with no/broken favicon. */
private val APP_LOGO_URI: Uri =
    Uri.parse("android.resource://com.armanmaurya.internetradio/mipmap/ic_launcher")

/**
 * Converts a [RadioStation] domain model to a Media3 [MediaItem] with artwork proxying.
 */
fun RadioStation.toMediaItem(
    context: Context,
    parentId: String? = null,
    showThumbnails: Boolean = true
): MediaItem {
    val artworkUriStr = when {
        !showThumbnails -> null
        favicon.endsWith(".svg", ignoreCase = true) -> SvgProxyProvider.createProxyUri(context, favicon)
        favicon.isNotBlank() -> favicon
        else -> null
    }
    val artworkUri = if (!showThumbnails) Uri.EMPTY else (artworkUriStr?.let { Uri.parse(it) } ?: APP_LOGO_URI)
    val id = if (parentId != null) "$parentId|$stationUuid" else stationUuid

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(urlResolved.ifBlank { url })
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setAlbumTitle(name)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    putString("stationName", name)
                    putString("stationFavicon", favicon)
                    putString(EXTRA_STATION_JSON, this@toMediaItem.toJson())
                })
                .build()
        )
        .setTag(this)
        .build()
}

/** Marks items started by a schedule alarm, which only play library stations. */
const val EXTRA_SCHEDULED_PLAYBACK = "scheduled_playback"

/**
 * Creates a MediaItem from discrete station parameters (e.g. for ACTION_PLAY_STATION).
 */
fun createStationMediaItem(
    context: Context,
    stationUuid: String,
    stationUrl: String,
    stationName: String,
    stationFavicon: String,
    stationJson: String? = null,
    showThumbnails: Boolean = true
): MediaItem {
    val artworkUriStr = when {
        !showThumbnails -> null
        stationFavicon.endsWith(".svg", ignoreCase = true) -> SvgProxyProvider.createProxyUri(context, stationFavicon)
        stationFavicon.isNotBlank() -> stationFavicon
        else -> null
    }
    val artworkUri = if (!showThumbnails) Uri.EMPTY else (artworkUriStr?.let { Uri.parse(it) } ?: APP_LOGO_URI)

    return MediaItem.Builder()
        .setMediaId(stationUuid)
        .setUri(stationUrl)
        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(stationName)
                .setAlbumTitle(stationName)
                .setArtworkUri(artworkUri)
                .setExtras(Bundle().apply {
                    putString("stationName", stationName)
                    putString("stationFavicon", stationFavicon)
                    putBoolean(EXTRA_SCHEDULED_PLAYBACK, true)
                    putString(EXTRA_STATION_JSON, stationJson)
                })
                .build()
        )
        .setTag(stationFavicon)
        .build()
}
