package com.armanmaurya.internetradio.ui.tv.screens.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.utils.StateUtils
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.launch
import java.util.Locale

private class AboutRow(
    val icon: ImageVector,
    val value: String,
    val label: String? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * TV version of the player "About" station details, rendered in the right sidebar alongside tracks:
 * location, languages, stream format, tags, website, and stream URL.
 */
@Composable
fun TvStationAboutList(
    station: RadioStation,
    streamCodec: String?,
    streamBitrate: Int?,
    firstItemModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cannotOpenWebsite = stringResource(R.string.error_cannot_open_website)
    val tagsLabel = stringResource(R.string.player_label_tags)
    val websiteLabel = stringResource(R.string.player_label_website)
    val streamUrlLabel = stringResource(R.string.player_tab_about_stream_url)

    val rows = remember(station, streamCodec, streamBitrate) {
        val locale = Locale.getDefault()
        buildList {
            val country = station.countryCode.takeIf { it.isNotBlank() }
                ?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(locale) }
                ?: station.country.takeIf { it.isNotBlank() }
            val state = station.iso3166_2?.takeIf { it.isNotBlank() && station.countryCode.isNotBlank() }?.let { code ->
                StateUtils.getStatesForCountry(context, station.countryCode)
                    .find { it.code == code }?.getDisplayName(locale.language) ?: code
            }
            listOfNotNull(state, country).joinToString(", ").takeIf { it.isNotBlank() }
                ?.let { add(AboutRow(Icons.Default.Public, it)) }

            val languages = station.languageCodes.takeIf { it.isNotEmpty() }?.joinToString(", ") { code ->
                Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
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

    val scrollToTopOnFocus = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { listState.animateScrollToItem(0) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "station_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
            ) {
                AsyncImage(
                    model = station.favicon.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                )
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (rows.isEmpty()) {
            item(key = "empty_about") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .then(firstItemModifier)
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_no_tracks_played),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(rows.withIndex().toList(), key = { (index, _) -> "about_row_$index" }) { (index, row) ->
                ListItem(
                    selected = false,
                    onClick = { row.onClick?.invoke() },
                    overlineContent = row.label?.let { { Text(it) } },
                    headlineContent = { Text(row.value, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    scale = ListItemDefaults.scale(focusedScale = 1.02f),
                    shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .then(if (index == 0) firstItemModifier.then(scrollToTopOnFocus) else Modifier)
                )
            }
        }
    }
}
