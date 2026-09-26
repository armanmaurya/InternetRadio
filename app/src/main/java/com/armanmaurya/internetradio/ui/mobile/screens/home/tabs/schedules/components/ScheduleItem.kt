package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.model.StartOfWeek
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.ui.shared.theme.LocalAppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleItem(
    schedule: Schedule,
    stationFavicon: String?,
    startOfWeek: StartOfWeek,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormat = com.armanmaurya.internetradio.core.utils.FormatUtils.getTimeFormat(context)

    val startCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, schedule.timeHour)
        set(Calendar.MINUTE, schedule.timeMinute)
    }
    val startTimeString = timeFormat.format(startCal.time)
    
    val endTotalMinutes = schedule.timeHour * 60 + schedule.timeMinute + schedule.durationMinutes
    val endHour = (endTotalMinutes / 60) % 24
    val endMinute = endTotalMinutes % 60
    
    val endCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, endHour)
        set(Calendar.MINUTE, endMinute)
    }
    val endTimeString = timeFormat.format(endCal.time)
    
    val timeRangeString = if (schedule.durationMinutes > 0) {
        "$startTimeString - $endTimeString"
    } else {
        "$startTimeString - ${stringResource(R.string.schedule_indefinite)}"
    }

    val repeatText = if (schedule.isRecurring) {
        null
    } else {
        stringResource(R.string.schedule_once)
    }

    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black
    val cardBorder = if (isPureBlack) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else {
        null
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showThumbnails = LocalAppPreferences.current.showStationThumbnails

                if (showThumbnails && !stationFavicon.isNullOrBlank()) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(stationFavicon)
                            .size(coil3.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = stringResource(R.string.schedule_station_logo_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        error = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        loading = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = if (schedule.scheduleName.isNotBlank()) {
                        "${schedule.scheduleName} • ${schedule.stationName}"
                    } else {
                        schedule.stationName
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (schedule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (repeatText != null) "$timeRangeString • $repeatText" else timeRangeString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (schedule.isRecurring) {
                val enabledDays = schedule.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
                val dayOrder = startOfWeek.getDaysOrder()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (dayValue in dayOrder) {
                        val isSelected = enabledDays.contains(dayValue)
                        val javaTimeDay = if (dayValue == 1) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.of(dayValue - 1)
                        val dayName = javaTimeDay.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).take(2)
                        
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .sizeIn(maxWidth = 44.dp, maxHeight = 44.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(percent = if (isSelected) 25 else 50))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
        }
    }
}
