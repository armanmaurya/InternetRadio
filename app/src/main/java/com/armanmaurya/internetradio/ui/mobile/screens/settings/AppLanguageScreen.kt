package com.armanmaurya.internetradio.ui.mobile.screens.settings

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** Value stored/applied for "follow the phone's language". */
const val SYSTEM_LANGUAGE_TAG = "System"

/** A UI language the app ships translations for (from res/xml/locales_config.xml). */
data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val localizedName: String
) {
    val badge: String get() = Locale.forLanguageTag(tag).language.uppercase(Locale.ROOT)
}

private val groupTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val groupMiddleShape = RoundedCornerShape(4.dp)
private val groupBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
private val groupSingleShape = RoundedCornerShape(24.dp)

private fun groupShape(index: Int, count: Int): Shape = when {
    count == 1 -> groupSingleShape
    index == 0 -> groupTopShape
    index == count - 1 -> groupBottomShape
    else -> groupMiddleShape
}

private fun String.capitalized(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** Languages from locales_config, sorted by their native name. Names follow the current app language. */
@Composable
fun rememberAppLanguages(): List<AppLanguage> {
    val context = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0]
    return remember(appLocale) {
        buildList {
            try {
                val parser = context.resources.getXml(R.xml.locales_config)
                var event = parser.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                        val tag = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                        if (!tag.isNullOrBlank()) {
                            val locale = Locale.forLanguageTag(tag)
                            add(
                                AppLanguage(
                                    tag = tag,
                                    nativeName = locale.getDisplayName(locale).capitalized(locale),
                                    localizedName = locale.getDisplayName(appLocale).capitalized(appLocale)
                                )
                            )
                        }
                    }
                    event = parser.next()
                }
                parser.close()
            } catch (_: Exception) {
            }
        }.sortedBy { it.nativeName.lowercase() }
    }
}

/** Finds the supported language for [locale]: exact tag first (e.g. pt-BR), then language only (e.g. de). */
private fun List<AppLanguage>.matching(locale: Locale?): AppLanguage? {
    if (locale == null) return null
    val tag = locale.toLanguageTag()
    return firstOrNull { it.tag.equals(tag, ignoreCase = true) }
        ?: firstOrNull { Locale.forLanguageTag(it.tag).language == locale.language }
}

/** The tag of the language the app is currently set to, or [SYSTEM_LANGUAGE_TAG]. */
fun List<AppLanguage>.currentAppLanguageTag(): String {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    if (appLocales.isEmpty) return SYSTEM_LANGUAGE_TAG
    return matching(appLocales[0])?.tag ?: SYSTEM_LANGUAGE_TAG
}

private fun deviceLocale(): Locale = Resources.getSystem().configuration.locales[0]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLanguageScreen(
    onLanguageSelected: (String) -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val languages = rememberAppLanguages()
    val selectedTag = languages.currentAppLanguageTag()
    val device = remember { deviceLocale() }
    // One group: "System default" first, then every language.
    val rowCount = languages.size + 1

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        // The bar adds 4dp and the 48dp touch target another 4dp around the 40dp
                        // circle; 8dp more puts the circle 16dp from the edge, in line with the list.
                        // The end gap gives a little extra room before the title.
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                // Keep the bar the same colour while content scrolls under it.
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item(key = SYSTEM_LANGUAGE_TAG) {
                LanguageRow(
                    title = stringResource(R.string.settings_system_default),
                    subtitle = device.getDisplayName(device).capitalized(device),
                    leading = { BadgeIcon(Icons.Default.Translate) },
                    isSelected = selectedTag == SYSTEM_LANGUAGE_TAG,
                    shape = groupShape(0, rowCount),
                    onClick = { onLanguageSelected(SYSTEM_LANGUAGE_TAG) }
                )
            }
            itemsIndexed(languages, key = { _, lang -> lang.tag }) { index, lang ->
                RowSpacer()
                LanguageRow(
                    title = lang.nativeName,
                    subtitle = lang.localizedName.takeIf { it != lang.nativeName },
                    leading = { BadgeText(lang.badge) },
                    isSelected = selectedTag == lang.tag,
                    shape = groupShape(index + 1, rowCount),
                    onClick = { onLanguageSelected(lang.tag) }
                )
            }
        }
    }
}

@Composable
private fun RowSpacer() = Spacer(modifier = Modifier.height(2.dp))

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    isSelected: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.secondaryContainer else colors.surfaceContainerHigh,
        label = "languageRowContainer"
    )
    ListItem(
        modifier = Modifier
            .clip(shape)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = leading,
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer
                )
            }
        }
    )
}

@Composable
private fun BadgeText(text: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}
