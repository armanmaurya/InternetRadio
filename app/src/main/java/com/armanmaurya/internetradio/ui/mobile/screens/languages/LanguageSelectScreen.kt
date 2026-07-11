package com.armanmaurya.internetradio.ui.mobile.screens.languages


import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.Language
import com.armanmaurya.internetradio.ui.shared.viewmodels.LanguageSelectViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectScreen(
    onLanguageSelected: (Language) -> Unit,
    onBackClick: () -> Unit,
    selectedLanguage: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LanguageSelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val filteredLanguages = remember(uiState.languages, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.languages
        } else {
            uiState.languages.filter { 
                it.name.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var hasAutoScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    // Scroll to selected language
    LaunchedEffect(uiState.isLoading, selectedLanguage) {
        if (!hasAutoScrolled && !uiState.isLoading && selectedLanguage != null && uiState.languages.isNotEmpty()) {
            val index = filteredLanguages.indexOfFirst { it.name == selectedLanguage }
            if (index >= 0) {
                delay(100)
                listState.animateScrollToItem(index + 1)
                hasAutoScrolled = true
            }
        }
    }

    val totalStations = remember(uiState.languages) {
        uiState.languages.sumOf { it.stationCount }
    }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = uiState.isSearchActive,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "TitleSearchTransition"
                    ) { isSearch ->
                        if (isSearch) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.select_language_search)) },
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
                            Text(stringResource(R.string.select_language_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearchActive) {
                            viewModel.toggleSearch()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearchActive) stringResource(R.string.cd_close_search) else stringResource(R.string.cd_search)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error ?: stringResource(R.string.error_unknown))
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    item(key = "all_languages") {
                        LanguageItem(
                            language = Language(name = stringResource(R.string.select_language_all), isoCode = "", stationCount = totalStations),
                            isSelected = selectedLanguage.isNullOrBlank(),
                            onClick = { onLanguageSelected(Language(name = context.getString(R.string.select_language_all), isoCode = "", stationCount = totalStations)) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    itemsIndexed(filteredLanguages, key = { _, language -> language.name }) { _, language ->
                        val isSelected = language.name == selectedLanguage
                        LanguageItem(
                            language = language,
                            isSelected = isSelected,
                            onClick = { onLanguageSelected(language) },
                            modifier = if (hasAutoScrolled) Modifier.animateItem() else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: Language,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { 
            Text(
                text = language.name,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = { Text(stringResource(R.string.general_station_count_msg, language.stationCount)) },
        trailingContent = { 
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else if (!language.isoCode.isNullOrBlank()) {
                Text(language.isoCode.uppercase())
            }
        },
        modifier = modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
    )
}
