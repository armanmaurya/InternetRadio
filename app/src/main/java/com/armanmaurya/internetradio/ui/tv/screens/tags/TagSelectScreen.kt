package com.armanmaurya.internetradio.ui.tv.screens.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.tv.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import com.armanmaurya.internetradio.ui.shared.viewmodels.TagSelectViewModel

@Composable
fun TagSelectScreen(
    initialTags: Set<String>,
    onTagsSelected: (Set<String>) -> Unit,
    onBackClick: () -> Unit,
    viewModel: TagSelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.setInitialTags(initialTags)
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, end = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_tags_title),
                style = MaterialTheme.typography.headlineMedium
            )

            
            Button(
                onClick = { onTagsSelected(uiState.selectedTags) }
            ) {
                Text(stringResource(R.string.select_tags_cd_apply))
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: stringResource(R.string.error_unknown))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.tags, key = { it.name }) { tag ->
                    val isSelected = uiState.selectedTags.contains(tag.name)
                    
                    Surface(
                        onClick = { viewModel.toggleTagSelection(tag.name) },
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${tag.stationCount} stns",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
