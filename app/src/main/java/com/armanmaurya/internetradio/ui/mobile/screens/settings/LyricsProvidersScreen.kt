package com.armanmaurya.internetradio.ui.mobile.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class LyricsProviderUiModel(
    val id: String,
    val displayName: String,
    val description: String? = null
)

val ALL_LYRICS_PROVIDERS = listOf(
    LyricsProviderUiModel("betterlyrics", "Better Lyrics", "Time-synced TTML & word-level lyrics"),
    LyricsProviderUiModel("unison", "Unison", "Crowdsourced community-timed lyrics"),
    LyricsProviderUiModel("lyricsplus", "LyricsPlus", "Multi-source synchronized lyrics relay"),
    LyricsProviderUiModel("kugou", "KuGou", "Large library with time-synced LRC files"),
    LyricsProviderUiModel("lrclib", "LRCLIB", "Open-source synced and plain lyrics database")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProvidersScreen(
    providerOrder: List<String>,
    enabledProviders: Set<String>,
    onOrderChanged: (List<String>) -> Unit,
    onProviderToggled: (String, Boolean) -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    var currentOrder by remember(providerOrder) {
        val completeOrder = providerOrder.toMutableList()
        ALL_LYRICS_PROVIDERS.forEach { p ->
            if (p.id !in completeOrder) completeOrder.add(p.id)
        }
        mutableStateOf(completeOrder)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        currentOrder = currentOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onOrderChanged(currentOrder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.lyrics_providers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.lyrics_providers_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp)
            ) {
                items(currentOrder, key = { it }) { providerId ->
                    val provider = ALL_LYRICS_PROVIDERS.firstOrNull { it.id == providerId }
                        ?: LyricsProviderUiModel(providerId, providerId)
                    val isEnabled = providerId in enabledProviders

                    ReorderableItem(reorderableState, key = providerId) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    modifier = Modifier.draggableHandle(),
                                    onClick = {}
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragIndicator,
                                        contentDescription = stringResource(R.string.cd_reorder_lyrics_provider),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!provider.description.isNullOrBlank()) {
                                        Text(
                                            text = provider.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    modifier = Modifier.semantics {
                                        contentDescription = "Toggle ${provider.displayName}"
                                    },
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        onProviderToggled(providerId, checked)
                                    },
                                    thumbContent = if (isEnabled) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
