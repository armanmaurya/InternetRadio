package com.armanmaurya.internetradio.ui.mobile.screens.home.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextAlign
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
    onSearch: (String) -> Unit = {},
    selectedCountryCode: String?,
    selectedStateCode: String?,
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

    val bottomPadding by animateDpAsState(
        targetValue = if (isSearchExpanded) 0.dp else 4.dp,
        label = "SearchBarBottomPadding"
    )

    SearchBar(
        inputField = {
            var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text = query)) }

            androidx.compose.runtime.LaunchedEffect(query) {
                if (query != textFieldValue.text) {
                    textFieldValue = textFieldValue.copy(text = query)
                }
            }

            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

            androidx.compose.runtime.LaunchedEffect(isSearchExpanded) {
                if (isSearchExpanded && textFieldValue.text.isNotEmpty()) {
                    textFieldValue = textFieldValue.copy(
                        selection = androidx.compose.ui.text.TextRange(0, textFieldValue.text.length)
                    )
                } else if (!isSearchExpanded) {
                    focusManager.clearFocus()
                }
            }

            TextField(
                value = textFieldValue,
                onValueChange = { 
                    textFieldValue = it
                    onQueryChange(it.text) 
                },
                modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        onExpandedChange(true)
                    }
                },
                placeholder = { Text(stringResource(R.string.general_search)) },
                leadingIcon = {
                    if (isSearchExpanded) {
                        IconButton(onClick = { onExpandedChange(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = isSearchExpanded && textFieldValue.text.isNotEmpty(),
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut()
                        ) {
                            IconButton(onClick = { 
                                textFieldValue = textFieldValue.copy(text = "")
                                onQueryChange("")
                                onSearchCleared() 
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
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
                                    contentDescription = stringResource(R.string.home_cd_select_tags),
                                    tint = if (selectedTags.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onLanguageClick) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.home_cd_select_language),
                                    tint = if (!selectedLanguage.isNullOrBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                if (!selectedLanguage.isNullOrBlank()) {
                                    Text(
                                        text = selectedLanguage.take(3).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 5.dp)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onCountryClick) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = stringResource(R.string.home_cd_select_country),
                                    tint = if (!selectedCountryCode.isNullOrBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!selectedCountryCode.isNullOrBlank()) {
                                    val displayCode = if (!selectedStateCode.isNullOrBlank()) selectedStateCode else selectedCountryCode
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = displayCode,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 7.sp,
                                                lineHeight = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 32.dp)
                                                .padding(horizontal = 4.dp, vertical = 0.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !isSearchExpanded,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut()
                        ) {
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_cd_settings))
                            }
                        }
                    }
                },
                singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { 
                    onSearch(textFieldValue.text)
                    onExpandedChange(false)
                })
            )
        },
        expanded = isSearchExpanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(bottom = bottomPadding)
    ) {
        content()
    }
}
