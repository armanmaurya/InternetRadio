package com.armanmaurya.internetradio.ui.mobile.screens.tags


import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.shared.viewmodels.TagSelectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectScreen(
    initialTags: Set<String>,
    onTagsSelected: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: TagSelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val lazyRowState = rememberLazyListState()
    var showUnsavedWarning by remember { mutableStateOf(false) }

    val handleBack = {
        if (uiState.selectedTags != initialTags) {
            showUnsavedWarning = true
        } else {
            onBackClick()
        }
    }

    BackHandler {
        if (showUnsavedWarning) {
            showUnsavedWarning = false
        } else if (uiState.isSearchActive) {
            viewModel.toggleSearch()
        } else {
            handleBack()
        }
    }

    if (showUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarning = false },
            title = { Text(stringResource(R.string.select_tags_discard_changes)) },
            text = { Text(stringResource(R.string.select_tags_discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedWarning = false
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.general_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUnsavedWarning = false }
                ) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }

    LaunchedEffect(uiState.selectedTags.size) {
        if (uiState.selectedTags.isNotEmpty()) {
            lazyRowState.animateScrollToItem(uiState.selectedTags.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setInitialTags(initialTags)
    }

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val filteredTags = remember(uiState.tags, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.tags
        } else {
            uiState.tags.filter { it.name.contains(uiState.searchQuery, ignoreCase = true) }
        }
    }

    val selectedTagsList = remember(uiState.selectedTags) {
        uiState.selectedTags.toList()
    }

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = uiState.isSearchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TitleSearchTransition"
                    ) { isSearch ->
                        if (isSearch) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.select_tags_search)) },
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
                            Text(stringResource(R.string.select_tags_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearchActive) {
                            viewModel.toggleSearch()
                        } else {
                            handleBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (uiState.selectedTags.isNotEmpty() && !uiState.isSearchActive) {
                        IconButton(onClick = viewModel::clearSelectedTags) {
                            Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.select_tags_cd_clear_all))
                        }
                    }
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearchActive) stringResource(R.string.cd_close_search) else stringResource(R.string.cd_search)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onTagsSelected(uiState.selectedTags) }
            ) {
                Icon(Icons.Default.Done, contentDescription = stringResource(R.string.select_tags_cd_apply))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = uiState.selectedTags.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    state = lazyRowState,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedTagsList, key = { it }) { tag ->
                        Box(modifier = Modifier.animateItem()) {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.toggleTagSelection(tag) },
                                label = { Text(tag) },
                                shape = RoundedCornerShape(32.dp),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.select_tags_cd_remove_tag),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: stringResource(R.string.error_unknown))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredTags, key = { it.name }) { tag ->
                            val isSelected = uiState.selectedTags.contains(tag.name)
                            Box(modifier = Modifier.animateItem()) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = tag.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    supportingContent = { Text(stringResource(R.string.general_station_count_msg, tag.stationCount)) },
                                    trailingContent = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable { viewModel.toggleTagSelection(tag.name) }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
