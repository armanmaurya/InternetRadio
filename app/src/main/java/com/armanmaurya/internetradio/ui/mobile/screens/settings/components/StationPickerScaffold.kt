package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import com.armanmaurya.internetradio.ui.shared.viewmodels.STATION_SEARCH_THRESHOLD
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation

private val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val middleShape = RoundedCornerShape(4.dp)
private val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
private val singleShape = RoundedCornerShape(24.dp)

/** Shape of row [index] of [count] in a group of settings rows, as on the Settings screen. */
internal fun groupShape(index: Int, count: Int): Shape = when {
    count == 1 -> singleShape
    index == 0 -> topShape
    index == count - 1 -> bottomShape
    else -> middleShape
}

/** What station cards need to play a station and show the one playing. */
internal class StationPlayback(
    val onPlay: (RadioStation) -> Unit,
    val playingStationUuid: String?,
    val isPlaybackActive: Boolean
)

/** The main action at the bottom of a [StationPickerScaffold], e.g. "Import 12 stations". */
internal class StationPickerAction(
    val text: String,
    val enabled: Boolean,
    val isWorking: Boolean,
    val onClick: () -> Unit
)

/**
 * Full screen for choosing stations (import and export): a close button, a search field once
 * the list is long, and the main action at the bottom. [content] gets the padding to apply and
 * the trimmed search text (empty when not searching); it shows a spinner while [action] is null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StationPickerScaffold(
    title: String,
    onClose: () -> Unit,
    closeEnabled: Boolean,
    stationCount: Int,
    action: StationPickerAction?,
    contentPadding: PaddingValues,
    content: @Composable (innerPadding: PaddingValues, searchQuery: String) -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    fun closeSearch() {
        isSearchActive = false
        searchQuery = ""
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocus.requestFocus()
    }
    // Back closes the search first; the screen's own handler (registered before) handles the rest.
    BackHandler(enabled = isSearchActive) { closeSearch() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "PickerTitleSearch"
                    ) { searching ->
                        if (searching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus),
                                placeholder = { Text(stringResource(R.string.general_search)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        } else {
                            Text(title)
                        }
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onClose,
                        enabled = closeEnabled,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.general_cancel))
                    }
                },
                actions = {
                    if (stationCount > STATION_SEARCH_THRESHOLD) {
                        IconButton(onClick = { if (isSearchActive) closeSearch() else isSearchActive = true }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(
                                    if (isSearchActive) R.string.cd_close_search else R.string.cd_search
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            if (action != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = action.onClick,
                        enabled = action.enabled && !action.isWorking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (action.isWorking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(action.text)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (action == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            content(innerPadding, searchQuery.trim())
        }
    }
}
