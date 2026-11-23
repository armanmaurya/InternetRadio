package com.armanmaurya.internetradio.ui.tv.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.Lyrics
import com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics.ALL_AVAILABLE_PROVIDERS
import kotlinx.coroutines.launch

/**
 * TV version of the lyrics providers selection list, rendered in the right sidebar.
 * Includes custom Track/Artist search inputs, Reset/Search buttons, Auto mode, and individual provider selection.
 */
@Composable
fun TvLyricsProvidersList(
    allCandidates: List<Lyrics>,
    activeLyrics: Lyrics?,
    selectedProviderId: String?,
    currentTrackTitle: String,
    currentArtistName: String?,
    streamTrackTitle: String?,
    streamArtistName: String?,
    onSearch: (String, String?) -> Unit,
    onResetToTrack: () -> Unit,
    onSelectProvider: (String?) -> Unit,
    firstItemModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var trackTitleInput by remember(currentTrackTitle, streamTrackTitle) { mutableStateOf(currentTrackTitle) }
    var artistInput by remember(currentArtistName, streamArtistName) { mutableStateOf(currentArtistName.orEmpty()) }

    val isAutoSelected = selectedProviderId == null || selectedProviderId.equals("auto", ignoreCase = true)

    val autoChosenLyrics = remember(allCandidates, activeLyrics) {
        allCandidates
            .filter { it.hasWordSync || !it.syncedLyrics.isNullOrEmpty() || !it.plainLyrics.isNullOrBlank() }
            .minWithOrNull(
                compareBy(
                    { candidate ->
                        when {
                            candidate.hasWordSync -> 0
                            !candidate.syncedLyrics.isNullOrEmpty() -> 1
                            else -> 2
                        }
                    },
                    { candidate ->
                        val idx = ALL_AVAILABLE_PROVIDERS.indexOfFirst {
                            it.id.equals(candidate.providerInfo.id, ignoreCase = true)
                        }
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }
                )
            ) ?: allCandidates.firstOrNull() ?: activeLyrics
    }

    val autoProviderName = autoChosenLyrics?.providerInfo?.displayName
    val autoSyncType = when {
        autoChosenLyrics?.hasWordSync == true -> stringResource(R.string.lyrics_word_synced)
        autoChosenLyrics?.syncedLyrics?.isNotEmpty() == true -> stringResource(R.string.lyrics_line_synced)
        autoChosenLyrics != null -> stringResource(R.string.lyrics_plain)
        else -> null
    }
    val autoSubtitle = if (autoProviderName != null && autoSyncType != null) {
        "$autoProviderName • $autoSyncType"
    } else {
        stringResource(R.string.lyrics_provider_auto_subtitle)
    }

    val scrollToTopOnFocus = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { listState.animateScrollToItem(0) }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.border.copy(alpha = 0.5f),
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Track Title Input Field
        item(key = "input_track_title") {
            OutlinedTextField(
                value = trackTitleInput,
                onValueChange = { trackTitleInput = it },
                label = { androidx.compose.material3.Text(stringResource(R.string.lyrics_track_title)) },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(scrollToTopOnFocus)
            )
        }

        // Artist Name Input Field
        item(key = "input_artist") {
            OutlinedTextField(
                value = artistInput,
                onValueChange = { artistInput = it },
                label = { androidx.compose.material3.Text(stringResource(R.string.lyrics_artist_name)) },
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (trackTitleInput.isNotBlank()) {
                            onSearch(trackTitleInput.trim(), artistInput.trim().takeIf { it.isNotBlank() })
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // Reset & Search Action Buttons
        item(key = "search_actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        trackTitleInput = streamTrackTitle.orEmpty()
                        artistInput = streamArtistName.orEmpty()
                        onResetToTrack()
                    },
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.lyrics_reset_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = {
                        if (trackTitleInput.isNotBlank()) {
                            onSearch(trackTitleInput.trim(), artistInput.trim().takeIf { it.isNotBlank() })
                        }
                    },
                    enabled = trackTitleInput.isNotBlank(),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.lyrics_search_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        item(key = "divider") {
            Spacer(Modifier.height(8.dp))
        }

        // Auto (Recommended) Card
        item(key = "provider_auto") {
            ListItem(
                selected = false,
                onClick = { onSelectProvider(null) },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ListItemDefaults.colors(
                    containerColor = if (isAutoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = if (isAutoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                headlineContent = {
                    Text(
                        text = stringResource(R.string.lyrics_provider_auto),
                        color = if (isAutoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = {
                    Text(
                        text = autoSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutoSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .then(firstItemModifier)
            )
        }

        item(key = "providers_divider") {
            Spacer(Modifier.height(4.dp))
        }

        // Available Providers
        items(ALL_AVAILABLE_PROVIDERS, key = { "provider_${it.id}" }) { provider ->
            val matching = allCandidates.firstOrNull { it.providerInfo.id.equals(provider.id, ignoreCase = true) }
            val isSelected = if (isAutoSelected) {
                provider.id.equals(autoChosenLyrics?.providerInfo?.id, ignoreCase = true)
            } else {
                selectedProviderId.equals(provider.id, ignoreCase = true)
            }
            val isAvailable = matching != null

            val badgeText = when {
                matching == null -> null
                matching.hasWordSync -> stringResource(R.string.lyrics_word_synced)
                !matching.syncedLyrics.isNullOrEmpty() -> stringResource(R.string.lyrics_line_synced)
                else -> stringResource(R.string.lyrics_plain)
            }

            ListItem(
                selected = false,
                enabled = isAvailable,
                onClick = {
                    if (isAvailable) {
                        if (!isAutoSelected && selectedProviderId.equals(provider.id, ignoreCase = true)) {
                            onSelectProvider(null)
                        } else {
                            onSelectProvider(provider.id)
                        }
                    }
                },
                scale = ListItemDefaults.scale(focusedScale = 1.02f),
                shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                headlineContent = {
                    Text(
                        text = provider.displayName,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                },
                trailingContent = {
                    if (matching != null && badgeText != null) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.lyrics_provider_not_found),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .then(if (!isAvailable) Modifier.alpha(0.5f) else Modifier)
            )
        }
    }
}
