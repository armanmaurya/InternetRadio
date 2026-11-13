package com.armanmaurya.internetradio.ui.tv.screens.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.utils.StateUtils
import com.armanmaurya.internetradio.domain.model.RadioStation
import java.util.Locale

private class AboutRow(
    val icon: ImageVector,
    val value: String,
    val label: String? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * Station details, like the phone player's About tab: location, languages, stream format, tags,
 * website and stream URL. Each row is focusable so the list scrolls with the remote.
 */
@Composable
fun TvStationAboutDialog(
    station: RadioStation,
    streamCodec: String?,
    streamBitrate: Int?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cannotOpenWebsite = stringResource(R.string.error_cannot_open_website)
    val tagsLabel = stringResource(R.string.player_label_tags)
    val websiteLabel = stringResource(R.string.player_label_website)
    val streamUrlLabel = stringResource(R.string.player_tab_about_stream_url)

    val rows = remember(station, streamCodec, streamBitrate) {
        val locale = Locale.getDefault()
        buildList {
            val country = station.countryCode.takeIf { it.isNotBlank() }
                ?.let { Locale("", it).getDisplayCountry(locale) }
                ?: station.country.takeIf { it.isNotBlank() }
            val state = station.iso3166_2?.takeIf { it.isNotBlank() && station.countryCode.isNotBlank() }?.let { code ->
                StateUtils.getStatesForCountry(context, station.countryCode)
                    .find { it.code == code }?.getDisplayName(locale.language) ?: code
            }
            listOfNotNull(state, country).joinToString(", ").takeIf { it.isNotBlank() }
                ?.let { add(AboutRow(Icons.Default.Public, it)) }

            val languages = station.languageCodes.takeIf { it.isNotEmpty() }?.joinToString(", ") { code ->
                Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            } ?: station.language.takeIf { it.isNotBlank() }
            languages?.let { add(AboutRow(Icons.Default.Translate, it)) }

            val codec = streamCodec?.takeIf { it.isNotBlank() } ?: station.codec.takeIf { it.isNotBlank() && it != "unknown" }
            val bitrate = (streamBitrate ?: station.bitrate).takeIf { it > 0 }
            listOfNotNull(codec?.uppercase(locale), bitrate?.let { "$it kbps" }).joinToString(" · ")
                .takeIf { it.isNotBlank() }
                ?.let { add(AboutRow(Icons.Default.GraphicEq, it)) }

            if (station.tags.isNotEmpty()) {
                add(AboutRow(Icons.Default.Sell, station.tags.joinToString(", "), label = tagsLabel))
            }
            if (station.homepage.isNotBlank()) {
                add(AboutRow(Icons.Default.Language, station.homepage, label = websiteLabel) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(station.homepage))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        // Most TVs have no browser.
                        Toast.makeText(context, cannotOpenWebsite, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            if (station.url.isNotBlank()) {
                add(AboutRow(Icons.Default.Link, station.url, label = streamUrlLabel))
            }
        }
    }

    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            firstRowFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(620.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .padding(20.dp)
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
                    ) {
                        AsyncImage(
                            model = station.favicon.ifBlank { null },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            error = painterResource(R.drawable.ic_launcher_foreground),
                            fallback = painterResource(R.drawable.ic_launcher_foreground),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E1E))
                        )
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                items(rows.withIndex().toList()) { (index, row) ->
                    ListItem(
                        selected = false,
                        onClick = { row.onClick?.invoke() },
                        overlineContent = row.label?.let { { Text(it) } },
                        headlineContent = { Text(row.value, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(row.icon, contentDescription = null) },
                        scale = ListItemDefaults.scale(focusedScale = 1.01f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                    )
                }
            }
        }
    }
}
