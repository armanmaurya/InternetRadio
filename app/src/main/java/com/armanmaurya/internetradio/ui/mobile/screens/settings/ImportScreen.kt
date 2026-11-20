package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPickerAction
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPickerScaffold
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.StationPlayback
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.groupShape
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportPart
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportStationItem
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportUiState
import com.armanmaurya.internetradio.ui.shared.viewmodels.ImportViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationDiffField
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationFieldDiff
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationParts
import com.armanmaurya.internetradio.ui.shared.viewmodels.StationUpdateMode

/**
 * Review of the picked backup file(s) before anything is saved: what to bring in for each
 * station, with shortcuts to include or leave out track history and starred tracks everywhere.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onClose: () -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsState()

    // Both the finished event and the idle state can ask to close; only leave once.
    var closed by remember { mutableStateOf(false) }
    fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }

    // Back to idle means the import finished, failed, found nothing or was cancelled; Settings
    // shows how it went. Also covers the app being restarted on this screen.
    LaunchedEffect(state) {
        if (state == ImportUiState.Idle) close()
    }

    val review = state as? ImportUiState.Review
    val isImporting = review?.isImporting == true
    fun cancel() {
        if (!isImporting) {
            viewModel.cancel()
            close()
        }
    }
    BackHandler { cancel() }

    StationPickerScaffold(
        title = stringResource(R.string.import_title),
        onClose = ::cancel,
        closeEnabled = !isImporting,
        stationCount = review?.stations?.size ?: 0,
        action = review?.let {
            StationPickerAction(
                text = pluralStringResource(R.plurals.import_button, it.selectedCount, it.selectedCount),
                enabled = it.selectedCount > 0,
                isWorking = it.isImporting,
                onClick = viewModel::import
            )
        },
        contentPadding = contentPadding
    ) { innerPadding, searchQuery ->
        if (review != null) {
            ReviewList(
                review = review,
                viewModel = viewModel,
                playback = StationPlayback(onPlayStation, playingStationUuid, isPlaybackActive),
                searchQuery = searchQuery,
                contentPadding = innerPadding
            )
        }
    }
}

@Composable
private fun ReviewList(
    review: ImportUiState.Review,
    viewModel: ImportViewModel,
    playback: StationPlayback,
    searchQuery: String,
    contentPadding: PaddingValues
) {
    val enabled = !review.isImporting
    val isSearching = searchQuery.isNotEmpty()
    fun List<ImportStationItem>.matching() =
        if (isSearching) filter { it.name.contains(searchQuery, ignoreCase = true) } else this
    val newStations = review.newStations.matching()
    val existingStations = review.existingStations.matching()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        if (review.failedFiles > 0) {
            item(key = "failed") {
                Text(
                    text = pluralStringResource(R.plurals.settings_imported_failed, review.failedFiles, review.failedFiles),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Shortcuts for every station at once, styled like the Settings switches.
        val options = buildList<@Composable (Shape) -> Unit> {
            if (review.hasHistory) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_history),
                    icon = Icons.Default.History,
                    isEnabled = review.partState(ImportPart.HISTORY) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.HISTORY) },
                    shape = shape,
                    enabled = enabled
                )
            }
            if (review.hasStarred) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_part_starred),
                    icon = Icons.Default.Star,
                    isEnabled = review.partState(ImportPart.STARRED) == ToggleableState.On,
                    onToggle = { viewModel.togglePartForAll(ImportPart.STARRED) },
                    shape = shape,
                    enabled = enabled
                )
            }
            if (review.exceedsHistoryLimit) add { shape ->
                ToggleItem(
                    title = stringResource(R.string.import_keep_all_plays),
                    subtitle = stringResource(R.string.import_keep_all_plays_desc, review.historyLimit, review.playsNeeded),
                    icon = Icons.Default.Storage,
                    isEnabled = review.keepAllPlays,
                    onToggle = viewModel::setKeepAllPlays,
                    shape = shape,
                    enabled = enabled
                )
            }
        }
        // While searching, only the matching stations are shown.
        if (options.isNotEmpty() && !isSearching) {
            item(key = "options") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEachIndexed { index, option -> option(groupShape(index, options.size)) }
                }
            }
        }

        stationSection(
            title = R.string.import_section_new,
            stations = newStations,
            sectionState = review.sectionState(existing = false),
            showSelectAll = !isSearching,
            enabled = enabled,
            onToggleSection = { viewModel.toggleSection(existing = false) },
            viewModel = viewModel,
            playback = playback
        )
        stationSection(
            title = R.string.import_section_existing,
            stations = existingStations,
            sectionState = review.sectionState(existing = true),
            showSelectAll = !isSearching,
            enabled = enabled,
            onToggleSection = { viewModel.toggleSection(existing = true) },
            viewModel = viewModel,
            playback = playback
        )
        if (isSearching && newStations.isEmpty() && existingStations.isEmpty()) {
            item(key = "no_matches") {
                Text(
                    text = stringResource(R.string.import_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                )
            }
        }
    }
}

private fun LazyListScope.stationSection(
    title: Int,
    stations: List<ImportStationItem>,
    sectionState: ToggleableState,
    /** Select all acts on the whole section, so it is hidden while a search hides some of it. */
    showSelectAll: Boolean,
    enabled: Boolean,
    onToggleSection: () -> Unit,
    viewModel: ImportViewModel,
    playback: StationPlayback
) {
    if (stations.isEmpty()) return

    // Header only; the cards are separate items so long lists stay lazy.
    item(key = "header_$title") {
        Section(
            title = "${stringResource(title)} · ${stations.size}",
            trailingContent = if (showSelectAll) {
                { TriStateCheckbox(state = sectionState, onClick = onToggleSection, enabled = enabled) }
            } else null
        ) {}
    }

    items(stations, key = { it.stationUuid }) { item ->
        StationListCard(
            station = item.station,
            // Like everywhere else in the app, tapping a station plays it, so it can be tried
            // before importing; the checkbox and chips do the selecting.
            onClick = { playback.onPlay(item.station) },
            isCurrentlyPlaying = playback.playingStationUuid == item.stationUuid,
            isPlaybackActive = playback.isPlaybackActive,
            trailingContent = {
                TriStateCheckbox(
                    state = item.state,
                    onClick = { viewModel.toggleStation(item.stationUuid) },
                    enabled = enabled,
                    // The card plays the station; name the checkbox so screen readers say which one it picks.
                    modifier = Modifier.semantics { contentDescription = item.name }
                )
            },
            footerContent = if (item.availableParts.size > 1 || item.isInLibrary) {
                {
                    ImportStationFooter(
                        item = item,
                        enabled = enabled,
                        viewModel = viewModel
                    )
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportStationFooter(
    item: ImportStationItem,
    enabled: Boolean,
    viewModel: ImportViewModel
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = if (item.isInLibrary && ImportPart.STATION in item.selected) 4.dp else 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                if (item.isInLibrary) {
                    // Connected [ Update | Replace ] segmented control
                    ConnectedUpdateReplaceControl(
                        item = item,
                        enabled = enabled,
                        onSelectMode = { mode -> viewModel.setStationUpdateMode(item.stationUuid, mode) }
                    )
                } else {
                    // New station: standard Station chip
                    val isStationSelected = ImportPart.STATION in item.selected
                    FilterChip(
                        selected = isStationSelected,
                        onClick = { viewModel.setPart(item.stationUuid, ImportPart.STATION, !isStationSelected) },
                        enabled = enabled && item.canChoose(ImportPart.STATION),
                        label = { Text(stringResource(R.string.import_part_station)) },
                        leadingIcon = if (isStationSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }

                // History chip
                if (item.playCount > 0) {
                    val isHistorySelected = ImportPart.HISTORY in item.selected
                    FilterChip(
                        selected = isHistorySelected,
                        onClick = { viewModel.setPart(item.stationUuid, ImportPart.HISTORY, !isHistorySelected) },
                        enabled = enabled && item.canChoose(ImportPart.HISTORY),
                        label = { Text(pluralStringResource(R.plurals.import_plays, item.playCount, item.playCount)) },
                        leadingIcon = if (isHistorySelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }

                // Starred chip
                if (item.starredCount > 0) {
                    val isStarredSelected = ImportPart.STARRED in item.selected
                    FilterChip(
                        selected = isStarredSelected,
                        onClick = { viewModel.setPart(item.stationUuid, ImportPart.STARRED, !isStarredSelected) },
                        enabled = enabled && item.canChoose(ImportPart.STARRED),
                        label = { Text(pluralStringResource(R.plurals.import_starred, item.starredCount, item.starredCount)) },
                        leadingIcon = if (isStarredSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }

            // Expandable details panel
            AnimatedVisibility(
                visible = item.isInLibrary && ImportPart.STATION in item.selected && item.isDetailsExpanded
            ) {
                StationFieldDiffPanel(
                    diff = item.fieldDiff,
                    updateMode = item.updateMode,
                    enabled = enabled,
                    onToggleField = { field -> viewModel.toggleStationField(item.stationUuid, field) }
                )
            }

            // Down arrow at bottom horizontally centered with animated visibility
            AnimatedVisibility(
                visible = item.isInLibrary && ImportPart.STATION in item.selected
            ) {
                val arrowRotation by animateFloatAsState(
                    targetValue = if (item.isDetailsExpanded) 180f else 0f,
                    label = "detailsArrow"
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = { viewModel.toggleStationDetails(item.stationUuid) },
                        enabled = enabled,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.import_field_details),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(arrowRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedUpdateReplaceControl(
    item: ImportStationItem,
    enabled: Boolean,
    onSelectMode: (StationUpdateMode) -> Unit
) {
    val isStationSelected = ImportPart.STATION in item.selected
    val isUpdate = isStationSelected && item.updateMode == StationUpdateMode.UPDATE
    val isReplace = isStationSelected && item.updateMode == StationUpdateMode.REPLACE

    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = isUpdate,
            onClick = { onSelectMode(StationUpdateMode.UPDATE) },
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            label = { Text(stringResource(R.string.import_mode_update)) },
            leadingIcon = if (isUpdate) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
            enabled = enabled
        )
        FilterChip(
            selected = isReplace,
            onClick = { onSelectMode(StationUpdateMode.REPLACE) },
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            label = { Text(stringResource(R.string.import_mode_replace)) },
            leadingIcon = if (isReplace) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
            enabled = enabled
        )
    }
}

@Composable
private fun StationFieldDiffPanel(
    diff: StationFieldDiff,
    updateMode: StationUpdateMode,
    enabled: Boolean,
    onToggleField: (StationDiffField) -> Unit
) {
    val updatingBadge = if (updateMode == StationUpdateMode.REPLACE) {
        stringResource(R.string.import_diff_replacing)
    } else {
        stringResource(R.string.import_diff_updating)
    }
    val sameBadge = stringResource(R.string.import_diff_same)
    val skippedBadge = stringResource(R.string.import_diff_skipped)
    val nonePlaceholder = stringResource(R.string.import_diff_none)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Stream URL
        val urlChanges = diff.urlDiffers
        val urlDetail = buildDiffDetail(
            oldText = diff.oldUrl,
            newText = diff.newUrl,
            isChanging = urlChanges,
            checked = diff.updateUrl,
            nonePlaceholder = nonePlaceholder
        )
        val urlBadge = when {
            !urlChanges -> sameBadge
            !diff.updateUrl -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_stream_url),
            badgeText = urlBadge,
            detail = urlDetail,
            isChanging = urlChanges,
            checked = diff.updateUrl,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.URL) }
        )

        // 2. Station Name
        val nameChanges = diff.nameDiffers
        val nameDetail = buildDiffDetail(
            oldText = diff.oldName,
            newText = diff.newName,
            isChanging = nameChanges,
            checked = diff.updateName,
            nonePlaceholder = nonePlaceholder
        )
        val nameBadge = when {
            !nameChanges -> sameBadge
            !diff.updateName -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_station_name),
            badgeText = nameBadge,
            detail = nameDetail,
            isChanging = nameChanges,
            checked = diff.updateName,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.NAME) }
        )

        // 3. Tags
        val newIncomingTags = diff.newTags.filter { newTag ->
            newTag.isNotBlank() && diff.oldTags.none { oldTag -> oldTag.equals(newTag, ignoreCase = true) }
        }
        val tagsChanges = if (updateMode == StationUpdateMode.UPDATE) {
            newIncomingTags.isNotEmpty()
        } else {
            diff.tagsDiffer
        }
        val tagsDetail = when {
            updateMode == StationUpdateMode.UPDATE -> {
                when {
                    tagsChanges -> {
                        val tagsText = newIncomingTags.joinToString(prefix = "+ ", separator = ", + ")
                        if (diff.updateTags) {
                            AnnotatedString(tagsText)
                        } else {
                            buildAnnotatedString {
                                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                    append(tagsText)
                                }
                            }
                        }
                    }
                    diff.oldTags.isNotEmpty() -> AnnotatedString(diff.oldTags.joinToString())
                    else -> AnnotatedString(nonePlaceholder)
                }
            }
            else -> {
                val oldTagsStr = diff.oldTags.joinToString().ifBlank { nonePlaceholder }
                val newTagsStr = if (diff.newTags.isNotEmpty()) diff.newTags.joinToString() else stringResource(R.string.import_diff_clears_tags)
                buildDiffDetail(
                    oldText = oldTagsStr,
                    newText = newTagsStr,
                    isChanging = tagsChanges,
                    checked = diff.updateTags,
                    nonePlaceholder = nonePlaceholder
                )
            }
        }
        val tagsBadge = when {
            !tagsChanges -> sameBadge
            !diff.updateTags -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_tags),
            badgeText = tagsBadge,
            detail = tagsDetail,
            isChanging = tagsChanges,
            checked = diff.updateTags,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.TAGS) }
        )

        // 4. Favicon / Icon
        val iconChanges = if (updateMode == StationUpdateMode.UPDATE) {
            diff.faviconDiffers
        } else {
            diff.faviconDiffers || (diff.oldFavicon.isNotBlank() && diff.newFavicon.isBlank())
        }
        val iconDetail = when {
            updateMode == StationUpdateMode.UPDATE -> {
                when {
                    iconChanges -> {
                        val oldIconStr = if (diff.oldFavicon.isNotBlank()) stringResource(R.string.import_diff_keeps_existing_icon) else nonePlaceholder
                        val newIconStr = stringResource(R.string.import_diff_update_icon)
                        buildDiffDetail(
                            oldText = oldIconStr,
                            newText = newIconStr,
                            isChanging = true,
                            checked = diff.updateFavicon,
                            nonePlaceholder = nonePlaceholder
                        )
                    }
                    diff.oldFavicon.isNotBlank() -> AnnotatedString(stringResource(R.string.import_diff_keeps_existing_icon))
                    else -> AnnotatedString(nonePlaceholder)
                }
            }
            else -> {
                val oldIconStr = if (diff.oldFavicon.isNotBlank()) stringResource(R.string.import_diff_keeps_existing_icon) else nonePlaceholder
                val newIconStr = when {
                    diff.faviconDiffers -> stringResource(R.string.import_diff_replace_icon)
                    diff.oldFavicon.isNotBlank() && diff.newFavicon.isBlank() -> stringResource(R.string.import_diff_clears_icon)
                    else -> oldIconStr
                }
                buildDiffDetail(
                    oldText = oldIconStr,
                    newText = newIconStr,
                    isChanging = iconChanges,
                    checked = diff.updateFavicon,
                    nonePlaceholder = nonePlaceholder
                )
            }
        }
        val iconBadge = when {
            !iconChanges -> sameBadge
            !diff.updateFavicon -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_icon),
            badgeText = iconBadge,
            detail = iconDetail,
            isChanging = iconChanges,
            checked = diff.updateFavicon,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.FAVICON) }
        )

        // 5. Homepage
        val homepageChanges = diff.homepageDiffers
        val homepageDetail = buildDiffDetail(
            oldText = diff.oldHomepage,
            newText = diff.newHomepage,
            isChanging = homepageChanges,
            checked = diff.updateHomepage,
            nonePlaceholder = nonePlaceholder
        )
        val homepageBadge = when {
            !homepageChanges -> sameBadge
            !diff.updateHomepage -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_homepage),
            badgeText = homepageBadge,
            detail = homepageDetail,
            isChanging = homepageChanges,
            checked = diff.updateHomepage,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.HOMEPAGE) }
        )

        // 6. Country
        val countryChanges = diff.countryDiffers
        val oldCountryName = if (diff.oldCountryCode.isNotBlank()) {
            java.util.Locale("", diff.oldCountryCode).getDisplayCountry(java.util.Locale.getDefault()).ifBlank { diff.oldCountryCode }
        } else ""
        val newCountryName = if (diff.newCountryCode.isNotBlank()) {
            java.util.Locale("", diff.newCountryCode).getDisplayCountry(java.util.Locale.getDefault()).ifBlank { diff.newCountryCode }
        } else ""
        val countryDetail = buildDiffDetail(
            oldText = oldCountryName,
            newText = newCountryName,
            isChanging = countryChanges,
            checked = diff.updateCountry,
            nonePlaceholder = nonePlaceholder
        )
        val countryBadge = when {
            !countryChanges -> sameBadge
            !diff.updateCountry -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_country),
            badgeText = countryBadge,
            detail = countryDetail,
            isChanging = countryChanges,
            checked = diff.updateCountry,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.COUNTRY) }
        )

        // 7. Language
        fun formatLanguageCodes(codes: List<String>): String = if (codes.isNotEmpty()) {
            codes.joinToString(", ") { code ->
                java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }
        } else ""

        val newIncomingLanguages = diff.newLanguageCodes.filter { newCode ->
            diff.oldLanguageCodes.none { it.equals(newCode, ignoreCase = true) }
        }
        val languageChanges = if (updateMode == StationUpdateMode.UPDATE) {
            newIncomingLanguages.isNotEmpty()
        } else {
            diff.languageDiffers
        }
        val languageDetail = when {
            updateMode == StationUpdateMode.UPDATE -> {
                when {
                    languageChanges -> {
                        val langText = newIncomingLanguages.joinToString(prefix = "+ ", separator = ", + ") { code ->
                            formatLanguageCodes(listOf(code))
                        }
                        if (diff.updateLanguage) {
                            AnnotatedString(langText)
                        } else {
                            buildAnnotatedString {
                                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                                    append(langText)
                                }
                            }
                        }
                    }
                    diff.oldLanguageCodes.isNotEmpty() -> AnnotatedString(formatLanguageCodes(diff.oldLanguageCodes))
                    else -> AnnotatedString(nonePlaceholder)
                }
            }
            else -> {
                val oldLangStr = formatLanguageCodes(diff.oldLanguageCodes).ifBlank { nonePlaceholder }
                val newLangStr = if (diff.newLanguageCodes.isNotEmpty()) {
                    formatLanguageCodes(diff.newLanguageCodes)
                } else {
                    stringResource(R.string.import_diff_clears_languages)
                }
                buildDiffDetail(
                    oldText = oldLangStr,
                    newText = newLangStr,
                    isChanging = languageChanges,
                    checked = diff.updateLanguage,
                    nonePlaceholder = nonePlaceholder
                )
            }
        }
        val languageBadge = when {
            !languageChanges -> sameBadge
            !diff.updateLanguage -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_language),
            badgeText = languageBadge,
            detail = languageDetail,
            isChanging = languageChanges,
            checked = diff.updateLanguage,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.LANGUAGE) }
        )

        // 8. Audio format (Codec & Bitrate)
        fun formatAudio(codec: String, bitrate: Int): String {
            val c = if (codec.isNotBlank() && codec != "unknown") codec.uppercase() else ""
            val b = if (bitrate > 0) "$bitrate kbps" else ""
            return listOfNotNull(c.takeIf { it.isNotBlank() }, b.takeIf { it.isNotBlank() }).joinToString(" · ")
        }
        val oldAudioText = formatAudio(diff.oldCodec, diff.oldBitrate)
        val newAudioText = formatAudio(diff.newCodec, diff.newBitrate)
        val audioChanges = diff.audioDiffers
        val audioDetail = buildDiffDetail(
            oldText = oldAudioText,
            newText = newAudioText,
            isChanging = audioChanges,
            checked = diff.updateAudio,
            nonePlaceholder = nonePlaceholder
        )
        val audioBadge = when {
            !audioChanges -> sameBadge
            !diff.updateAudio -> skippedBadge
            else -> updatingBadge
        }
        FieldDiffRow(
            label = stringResource(R.string.import_field_audio),
            badgeText = audioBadge,
            detail = audioDetail,
            isChanging = audioChanges,
            checked = diff.updateAudio,
            enabled = enabled,
            onToggle = { onToggleField(StationDiffField.AUDIO) }
        )
    }
}

private fun buildDiffDetail(
    oldText: String,
    newText: String,
    isChanging: Boolean,
    checked: Boolean,
    nonePlaceholder: String
): AnnotatedString {
    if (!isChanging) {
        val display = oldText.ifBlank { newText.ifBlank { nonePlaceholder } }
        return AnnotatedString(display)
    }

    val displayOld = oldText.ifBlank { nonePlaceholder }
    val displayNew = newText.ifBlank { nonePlaceholder }

    return buildAnnotatedString {
        if (checked) {
            // Update active: strike through the old value being replaced
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(displayOld)
            }
            append("  →  ")
            append(displayNew)
        } else {
            // Update skipped: user unchecked, so keep old and strike through the incoming new value
            append(displayOld)
            append("  →  ")
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                append(displayNew)
            }
        }
    }
}

@Composable
private fun FieldDiffRow(
    label: String,
    badgeText: String,
    detail: AnnotatedString,
    isChanging: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val clickableModifier = if (isChanging) {
        Modifier.clickable(enabled = enabled, onClick = onToggle)
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(clickableModifier)
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isChanging && checked) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isChanging && checked) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isChanging) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** One chip per part in [parts]; tapping one changes only that part. Shared with the export screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartChips(
    item: StationParts,
    enabled: Boolean,
    parts: List<ImportPart> = item.availableParts,
    stationLabel: String = stringResource(R.string.import_part_station),
    onSetPart: (ImportPart, Boolean) -> Unit
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides 0.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            parts.forEach { part ->
                val selected = part in item.selected
                FilterChip(
                    selected = selected,
                    onClick = { onSetPart(part, !selected) },
                    enabled = enabled && item.canChoose(part),
                    label = {
                        Text(
                            when (part) {
                                ImportPart.STATION -> stationLabel
                                ImportPart.HISTORY -> pluralStringResource(R.plurals.import_plays, item.playCount, item.playCount)
                                ImportPart.STARRED -> pluralStringResource(R.plurals.import_starred, item.starredCount, item.starredCount)
                            }
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
    }
}
