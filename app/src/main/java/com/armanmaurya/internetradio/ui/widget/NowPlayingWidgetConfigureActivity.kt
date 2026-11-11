package com.armanmaurya.internetradio.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.utils.extractPaletteFromBitmap
import com.armanmaurya.internetradio.core.utils.resolveArtwork
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class NowPlayingWidgetConfigureActivity : ComponentActivity() {

    private val viewModel: WidgetConfigureViewModel by viewModels()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result to CANCELED so if the user backs out, the widget is not placed
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val isDark = when (appPreferences.themeMode) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            // Trigger data load when dark mode preference is determined
            LaunchedEffect(appWidgetId, isDark) {
                viewModel.loadWidgetState(appWidgetId, isDark)
            }

            InternetRadioTheme(appPreferences = appPreferences) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val lastStation = uiState.lastStation
                    val nothingPlayingStr = stringResource(R.string.widget_preview_title)

                    val previewTitle = uiState.savedTitle?.takeIf { it.isNotBlank() && it != "Nothing playing" }
                        ?: lastStation?.name
                        ?: nothingPlayingStr
                    val previewArtist = uiState.savedArtist?.takeIf { it.isNotBlank() }
                        ?: (if (lastStation != null) "" else stringResource(R.string.widget_preview_artist))
                    val previewArtworkUrl = uiState.savedArtworkUrl?.takeIf { it.isNotBlank() }
                        ?: lastStation?.favicon
                    val previewStationThumbUrl = uiState.savedStationThumbUrl?.takeIf { it.isNotBlank() }
                        ?: if (uiState.savedIsCoverArtFetched) lastStation?.favicon else null

                    WidgetConfigureScreen(
                        appWidgetId = appWidgetId,
                        initialAlpha = uiState.initialAlpha,
                        previewTitle = previewTitle,
                        previewArtist = previewArtist,
                        previewArtworkUrl = previewArtworkUrl,
                        previewStationThumbUrl = previewStationThumbUrl,
                        isCoverArtFetched = uiState.savedIsCoverArtFetched,
                        isPlaying = uiState.savedIsPlaying,
                        extractedBgColor = uiState.savedBgColor,
                        extractedTitleColor = uiState.savedTitleColor,
                        extractedArtistColor = uiState.savedArtistColor,
                        isDark = isDark,
                        onApply = { chosenAlpha ->
                            CoroutineScope(Dispatchers.Main).launch {
                                viewModel.saveConfiguration(appWidgetId, chosenAlpha)
                                val resultValue = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, resultValue)
                                finish()
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigureScreen(
    appWidgetId: Int,
    initialAlpha: Float,
    previewTitle: String = stringResource(R.string.widget_preview_title),
    previewArtist: String = stringResource(R.string.widget_preview_artist),
    previewArtworkUrl: String? = null,
    previewStationThumbUrl: String? = null,
    isCoverArtFetched: Boolean = false,
    isPlaying: Boolean = false,
    extractedBgColor: Color? = null,
    extractedTitleColor: Color? = null,
    extractedArtistColor: Color? = null,
    isDark: Boolean = true,
    onApply: (Float) -> Unit,
    onCancel: () -> Unit
) {
    var alpha by remember(initialAlpha) { mutableStateOf(initialAlpha) }
    val percentageInt = (alpha * 100f).roundToInt()

    var dynamicBgColor by remember(previewArtworkUrl, previewStationThumbUrl) { mutableStateOf<Color?>(null) }
    var dynamicTitleColor by remember(previewArtworkUrl, previewStationThumbUrl) { mutableStateOf<Color?>(null) }
    var dynamicArtistColor by remember(previewArtworkUrl, previewStationThumbUrl) { mutableStateOf<Color?>(null) }

    val context = LocalContext.current
    LaunchedEffect(previewArtworkUrl, previewStationThumbUrl, extractedBgColor) {
        if (extractedBgColor == null) {
            val primaryUrl = previewArtworkUrl?.takeIf { it.isNotBlank() }
            val fallbackUrl = previewStationThumbUrl?.takeIf { it.isNotBlank() }
            val targetUrl = primaryUrl ?: fallbackUrl

            if (!targetUrl.isNullOrBlank()) {
                val bmp = resolveArtwork(context, targetUrl)
                var palette = extractPaletteFromBitmap(bmp)
                if (palette == null && primaryUrl != null && !fallbackUrl.isNullOrBlank() && primaryUrl != fallbackUrl) {
                    palette = extractPaletteFromBitmap(resolveArtwork(context, fallbackUrl, maxDimension = 96))
                }
                if (palette != null) {
                    dynamicBgColor = Color(if (isDark) palette.backgroundColor else palette.dayBackgroundColor)
                    dynamicTitleColor = Color(if (isDark) palette.titleTextColor else palette.dayTitleTextColor)
                    dynamicArtistColor = Color(if (isDark) palette.artistTextColor else palette.dayArtistTextColor)
                }
            }
        }
    }

    val effectiveBgColor = extractedBgColor ?: dynamicBgColor
    val effectiveTitleColor = extractedTitleColor ?: dynamicTitleColor
    val effectiveArtistColor = extractedArtistColor ?: dynamicArtistColor

    val presets = listOf(
        Pair(0.0f, stringResource(R.string.settings_widget_opacity_transparent)),
        Pair(0.25f, "25%"),
        Pair(0.50f, "50%"),
        Pair(0.75f, "75%"),
        Pair(1.0f, stringResource(R.string.settings_widget_opacity_opaque))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_configure_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = { onApply(alpha) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_configure_apply),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Live Preview
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.widget_configure_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Simulated home screen background wallpaper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1A237E),
                                    Color(0xFF311B92),
                                    Color(0xFF006064),
                                    Color(0xFF263238)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Widget Mockup Card
                    val baseBgColor = effectiveBgColor ?: MaterialTheme.colorScheme.surfaceContainer
                    val widgetBgColor = baseBgColor.copy(alpha = alpha)
                    val titleColor = effectiveTitleColor ?: MaterialTheme.colorScheme.onSurface
                    val artistColor = effectiveArtistColor ?: MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(widgetBgColor)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!previewArtworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = previewArtworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Floating station thumbnail badge
                            if (isCoverArtFetched && !previewStationThumbUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 6.dp, bottom = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = previewStationThumbUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }

                        // Right column: TrackInfo + Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = previewTitle,
                                    style = TextStyle(
                                        color = titleColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (previewArtist.isNotBlank()) {
                                    Text(
                                        text = previewArtist,
                                        style = TextStyle(color = artistColor, fontSize = 12.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_widget_prev),
                                    contentDescription = "Previous",
                                    tint = titleColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    painter = painterResource(
                                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                                    ),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = titleColor,
                                    modifier = Modifier.size(40.dp).padding(horizontal = 4.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_widget_next),
                                    contentDescription = "Next",
                                    tint = titleColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section: Opacity Controls
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_widget_transparency_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$percentageInt%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        track = { sliderState ->
                            val sliderColors = SliderDefaults.colors()
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = sliderColors,
                                modifier = Modifier.drawWithContent {
                                    drawContent()
                                    presets.drop(1).dropLast(1).forEach { (presetAlpha, _) ->
                                        val distance = kotlin.math.abs(sliderState.value - presetAlpha)
                                        if (distance >= 0.03f) {
                                            val x = size.width * presetAlpha
                                            val color = when {
                                                presetAlpha < sliderState.value -> sliderColors.activeTickColor
                                                else -> sliderColors.inactiveTickColor
                                            }
                                            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(x, center.y))
                                        }
                                    }
                                }
                            )
                        }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.widget_configure_presets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OpacityPresetRow(presets = presets, selectedAlpha = alpha) { chosen -> alpha = chosen }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpacityPresetRow(
    presets: List<Pair<Float, String>>,
    selectedAlpha: Float,
    onSelect: (Float) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        presets.forEach { (presetAlpha, label) ->
            val isSelected = (selectedAlpha * 100f).roundToInt() == (presetAlpha * 100f).roundToInt()
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(presetAlpha) },
                label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                contentPadding = PaddingValues(horizontal = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
