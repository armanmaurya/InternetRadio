package com.armanmaurya.internetradio.service.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.armanmaurya.internetradio.domain.model.RadioStation

/** App logo URI — used as fallback artwork for stations with no/broken favicon. */
private val APP_LOGO_URI: Uri =
    Uri.parse("android.resource://com.armanmaurya.internetradio/mipmap/ic_launcher")

/**
 * Converts a [RadioStation] domain model to a Media3 [MediaItem] with artwork proxying.
 */
fun RadioStation.toMediaItem(context: Context, parentId: String? = null): MediaItem {
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
                })
                .build()
        )
        .setTag(this)
        .build()
}
