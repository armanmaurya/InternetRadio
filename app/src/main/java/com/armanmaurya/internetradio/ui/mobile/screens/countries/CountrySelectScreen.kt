package com.armanmaurya.internetradio.ui.mobile.screens.countries


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
import com.armanmaurya.internetradio.data.model.Country
import com.armanmaurya.internetradio.ui.shared.viewmodels.CountrySelectViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectScreen(
    onCountrySelected: (Country) -> Unit,
    onBackClick: () -> Unit,
    selectedCountryCode: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: CountrySelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val filteredCountries = remember(uiState.countries, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.countries
        } else {
            uiState.countries.filter { 
                it.name.contains(uiState.searchQuery, ignoreCase = true) ||
                it.isoCode.contains(uiState.searchQuery, ignoreCase = true)
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

    // Scroll to selected country
    LaunchedEffect(uiState.isLoading, selectedCountryCode) {
        if (!hasAutoScrolled && !uiState.isLoading && selectedCountryCode != null && uiState.countries.isNotEmpty()) {
            val index = filteredCountries.indexOfFirst { it.isoCode == selectedCountryCode }
            if (index >= 0) {
                // Small delay to ensure layout is ready
                delay(100)
                listState.animateScrollToItem(index + 1)
                hasAutoScrolled = true
            }
        }
    }

    val totalStations = remember(uiState.countries) {
        uiState.countries.sumOf { it.stationCount }
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
                                placeholder = { Text(stringResource(R.string.select_country_search)) },
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
                            Text(stringResource(R.string.select_country_title))
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
                    item {
                        CountryItem(
                            country = Country(name = stringResource(R.string.select_country_all), isoCode = "", stationCount = totalStations),
                            isSelected = selectedCountryCode.isNullOrBlank(),
                            onClick = { onCountrySelected(Country(name = context.getString(R.string.select_country_all), isoCode = "", stationCount = totalStations)) }
                        )
                    }
                    itemsIndexed(filteredCountries, key = { _, country -> country.isoCode }) { _, country ->
                        val isSelected = country.isoCode == selectedCountryCode
                        CountryItem(
                            country = country,
                            isSelected = isSelected,
                            onClick = { onCountrySelected(country) },
                            modifier = if (hasAutoScrolled) Modifier.animateItem() else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryItem(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { 
            Text(
                text = country.name,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = if (isSelected) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge
            ) 
        },
        supportingContent = { Text(stringResource(R.string.general_station_count_msg, country.stationCount)) },
        trailingContent = { 
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            } else if (country.isoCode.isNotBlank()) {
                Text(country.isoCode)
            }
        },
        modifier = modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
    )
}
