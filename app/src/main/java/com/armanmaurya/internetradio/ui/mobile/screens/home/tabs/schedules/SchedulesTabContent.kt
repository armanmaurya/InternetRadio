@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.ScheduleType
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.components.ScheduleItem

@Composable
fun SchedulesTabContent(
    onEditSchedule: (Int?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    viewModel: SchedulesViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState()
    val libraryStations by viewModel.libraryStations.collectAsState()
    val appPreferences by viewModel.appPreferences.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (schedules == null || libraryStations == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (schedules!!.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.schedule_no_schedules_yet),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val playbackSchedules = schedules!!.filter { it.type == ScheduleType.PLAYBACK }
            val recordSchedules = schedules!!.filter { it.type == ScheduleType.RECORD }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playbackSchedules.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.material3.MaterialTheme.shapes.small)
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_playback),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(playbackSchedules, key = { it.id }) { schedule ->
                        val stationFavicon = libraryStations!!.find { it.stationUuid == schedule.stationUuid }?.favicon
                        ScheduleItem(
                            schedule = schedule,
                            stationFavicon = stationFavicon,
                            startOfWeek = appPreferences.startOfWeek,
                            onToggle = { isEnabled -> viewModel.toggleSchedule(schedule, isEnabled) },
                            onClick = { onEditSchedule(schedule.id) }
                        )
                    }
                }

                if (playbackSchedules.isNotEmpty() && recordSchedules.isNotEmpty()) {
                    // Separator removed per user request
                }

                if (recordSchedules.isNotEmpty()) {
                    item {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .clip(androidx.compose.material3.MaterialTheme.shapes.small)
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_record),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(recordSchedules, key = { it.id }) { schedule ->
                        val stationFavicon = libraryStations!!.find { it.stationUuid == schedule.stationUuid }?.favicon
                        ScheduleItem(
                            schedule = schedule,
                            stationFavicon = stationFavicon,
                            startOfWeek = appPreferences.startOfWeek,
                            onToggle = { isEnabled -> viewModel.toggleSchedule(schedule, isEnabled) },
                            onClick = { onEditSchedule(schedule.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onEditSchedule(null) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.schedule_add_schedule_cd)
            )
        }
    }
}
