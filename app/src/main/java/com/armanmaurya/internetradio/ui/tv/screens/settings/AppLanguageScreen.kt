package com.armanmaurya.internetradio.ui.tv.screens.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.settings.SYSTEM_LANGUAGE_TAG
import com.armanmaurya.internetradio.ui.mobile.screens.settings.currentAppLanguageTag
import com.armanmaurya.internetradio.ui.mobile.screens.settings.rememberAppLanguages

private data class LanguageOption(val tag: String, val title: String, val subtitle: String?)

/** TV picker for the app's UI language. Same languages and storage as the phone's App language screen. */
@Composable
fun AppLanguageScreen(onLanguageSelected: (String) -> Unit) {
    val languages = rememberAppLanguages()
    val selectedTag = languages.currentAppLanguageTag()
    val device = remember { Resources.getSystem().configuration.locales[0] }
    val systemTitle = stringResource(R.string.settings_system_default)

    val options = remember(languages, systemTitle) {
        listOf(
            LanguageOption(
                tag = SYSTEM_LANGUAGE_TAG,
                title = systemTitle,
                subtitle = device.getDisplayName(device).replaceFirstChar { it.titlecase(device) }
            )
        ) + languages.map { LanguageOption(it.tag, it.nativeName, it.localizedName.takeIf { n -> n != it.nativeName }) }
    }
    val selectedIndex = options.indexOfFirst { it.tag == selectedTag }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            selectedFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, end = 24.dp)) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(options, key = { it.tag }) { option ->
                val isSelected = option.tag == selectedTag
                Surface(
                    onClick = { onLanguageSelected(option.tag) },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = option.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                text = option.subtitle ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/** Native name of the app's current language, or "System Default". */
@Composable
fun rememberCurrentAppLanguageName(): String {
    val languages = rememberAppLanguages()
    val tag = languages.currentAppLanguageTag()
    return languages.firstOrNull { it.tag == tag }?.nativeName
        ?: stringResource(R.string.settings_system_default)
}
