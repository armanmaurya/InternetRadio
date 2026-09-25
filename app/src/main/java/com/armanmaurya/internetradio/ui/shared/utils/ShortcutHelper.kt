package com.armanmaurya.internetradio.ui.shared.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.core.provider.SvgProxyProvider
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ShortcutHelper {
    const val ACTION_PLAY_STATION = "com.armanmaurya.internetradio.ACTION_PLAY_STATION"
    const val EXTRA_STATION_JSON = "STATION_JSON"

    fun pinStationShortcut(context: Context, station: RadioStation) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var iconCompat = IconCompat.createWithResource(context, R.mipmap.ic_launcher)

            val artworkUrl = station.favicon
            if (!artworkUrl.isNullOrBlank()) {
                var targetUrl = artworkUrl
                if (targetUrl.endsWith(".svg", ignoreCase = true)) {
                    targetUrl = SvgProxyProvider.createProxyUri(context, targetUrl)
                }

                try {
                    val request = ImageRequest.Builder(context)
                        .data(targetUrl)
                        .size(192) // Standard shortcut icon size
                        .build()

                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.image as? BitmapImage)?.bitmap
                            ?: (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                        
                        if (bitmap != null) {
                            val swBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                            } else {
                                bitmap
                            }
                            iconCompat = IconCompat.createWithAdaptiveBitmap(swBitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val intent = Intent(context, MobileActivity::class.java).apply {
                action = ACTION_PLAY_STATION
                putExtra(EXTRA_STATION_JSON, Gson().toJson(station))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val shortcutInfo = ShortcutInfoCompat.Builder(context, "station_${station.stationUuid}")
                .setShortLabel(station.name)
                .setLongLabel(station.name)
                .setIcon(iconCompat)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        }
    }
}
