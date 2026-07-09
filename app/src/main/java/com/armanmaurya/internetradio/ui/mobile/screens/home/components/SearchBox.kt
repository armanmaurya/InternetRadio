package com.armanmaurya.internetradio.ui.mobile.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSearchCleared: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedCountryCode: String?,
    selectedLanguage: String?,
    selectedTags: Set<String>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val isSearchActive = query.isNotBlank()

    val horizontalPadding by animateDpAsState(
        targetValue = if (isSearchExpanded) 0.dp else 16.dp,
        label = "SearchBarPadding"
    )

    SearchBar(
        inputField = {
            val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == androidx.compose.ui.graphics.Color.Black
            val borderAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isSearchExpanded) 0f else 0.3f,
                label = "SearchBarBorderAlpha"
            )
            SearchBarDefaults.InputField(
                modifier = if (isPureBlack) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
                        androidx.compose.foundation.shape.RoundedCornerShape(100)
                    )
                } else Modifier,
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onExpandedChange(false) },
                expanded = isSearchExpanded,
                onExpandedChange = onExpandedChange,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    if (isSearchExpanded) {
                        IconButton(onClick = { onExpandedChange(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            onSearchCleared()
                            onExpandedChange(false)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onTagClick) {
                                BadgedBox(
                                    badge = {
                                        if (selectedTags.isNotEmpty()) {
                                            Badge {
                                                Text(selectedTags.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalOffer,
                                        contentDescription = "Select Tags",
                                        tint = if (selectedTags.isNotEmpty())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = onLanguageClick) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Select Language",
                                        tint = if (!selectedLanguage.isNullOrBlank())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!selectedLanguage.isNullOrBlank()) {
                                        Text(
                                            text = selectedLanguage.take(2).uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onCountryClick) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = "Select Country",
                                        tint = if (!selectedCountryCode.isNullOrBlank())
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!selectedCountryCode.isNullOrBlank()) {
                                        Text(
                                            text = selectedCountryCode,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !isSearchExpanded,
                                enter = expandHorizontally() + fadeIn(),
                                exit = shrinkHorizontally() + fadeOut()
                            ) {
                                IconButton(onClick = onSettingsClick) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        }
                    }
                }
            )
        },
        expanded = isSearchExpanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = 4.dp)
    ) {
        content()
    }
}
