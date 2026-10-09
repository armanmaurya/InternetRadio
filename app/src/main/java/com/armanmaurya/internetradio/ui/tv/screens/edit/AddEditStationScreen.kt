package com.armanmaurya.internetradio.ui.tv.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SwapVert
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.core.media.prober.ProbeResult
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddEditStationScreen(
    stationUuid: String?,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onPlayStation: (RadioStation) -> Unit = {}
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val station = if (stationUuid != null) stations?.find { it.stationUuid == stationUuid } else null
    val isEditing = stationUuid != null
    val coroutineScope = rememberCoroutineScope()

    BackHandler { onNavigateBack() }

    var name by remember(station) { mutableStateOf(station?.name ?: "") }
    var url by remember(station) { mutableStateOf(station?.url ?: "") }
    var favicon by remember(station) { mutableStateOf(station?.favicon ?: "") }
    var tags by remember(station) { mutableStateOf(station?.tags?.joinToString(", ")?.lowercase() ?: "") }
    var countryCode by remember(station) { mutableStateOf(station?.countryCode ?: "") }
    var languageCodes by remember(station) { mutableStateOf(station?.languageCodes?.joinToString(", ") ?: "") }
    var homepage by remember(station) { mutableStateOf(station?.homepage ?: "") }

    var isProbing by remember { mutableStateOf(false) }
    var probedCodec by remember(station) { mutableStateOf(station?.codec ?: "") }
    var probedBitrate by remember(station) { mutableStateOf(station?.bitrate ?: 0) }

    var showOverwriteDialog by remember { mutableStateOf(false) }
    var pendingProbeResult by remember { mutableStateOf<ProbeResult?>(null) }

    val handleProbeResult: (ProbeResult) -> Unit = { result ->
        probedCodec = result.codec
        probedBitrate = result.bitrate
        
        val hasNewMetadata = !result.name.isNullOrBlank() || !result.homepage.isNullOrBlank() || !result.genre.isNullOrBlank()
        val fieldsAlreadyFilled = name.isNotBlank() || homepage.isNotBlank() || tags.isNotBlank()
        
        if (hasNewMetadata) {
            if (fieldsAlreadyFilled) {
                val nameDiffers = !result.name.isNullOrBlank() && result.name != name
                val homepageDiffers = !result.homepage.isNullOrBlank() && result.homepage != homepage
                val parsedGenre = result.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.joinToString(", ")?.lowercase()
                val tagsDiffer = parsedGenre != null && parsedGenre != tags.lowercase()
                
                if (nameDiffers || homepageDiffers || tagsDiffer) {
                    pendingProbeResult = result
                    showOverwriteDialog = true
                }
            } else {
                if (!result.name.isNullOrBlank()) name = result.name!!
                if (!result.homepage.isNullOrBlank()) homepage = result.homepage!!
                if (!result.genre.isNullOrBlank()) tags = result.genre!!.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ").lowercase()
            }
        }
    }

    LaunchedEffect(url) {
        if (url.startsWith("http")) {
            if (station != null && url == station.url && (station.codec.isNotBlank() || station.bitrate > 0)) {
                isProbing = false
            } else {
                isProbing = true
                kotlinx.coroutines.delay(500)
                val result = viewModel.probeStream(url)
                if (result != null) {
                    handleProbeResult(result)
                }
                isProbing = false
            }
        } else {
            isProbing = false
        }
    }

    // Auto-check for similar stations when opening a custom station for editing
    LaunchedEffect(station?.stationUuid) {
        if (isEditing && station?.isCustom == true && url.isNotBlank()) {
            viewModel.checkDuplicateUrl(url)
        }
    }

    val firstFieldFocusRequester = remember { FocusRequester() }

    val canSave = name.isNotBlank() && url.isNotBlank() && !isProbing

    if (showOverwriteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                showOverwriteDialog = false 
                pendingProbeResult = null
            },
            title = { androidx.compose.material3.Text(stringResource(R.string.edit_station_overwrite_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.edit_station_overwrite_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val result = pendingProbeResult
                    if (result != null) {
                        if (!result.name.isNullOrBlank()) name = result.name
                        if (!result.homepage.isNullOrBlank()) homepage = result.homepage
                        if (!result.genre.isNullOrBlank()) tags = result.genre.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ").lowercase()
                    }
                    showOverwriteDialog = false
                    pendingProbeResult = null
                }) { androidx.compose.material3.Text(stringResource(R.string.settings_conflict_overwrite)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { 
                    showOverwriteDialog = false 
                    pendingProbeResult = null
                }) { androidx.compose.material3.Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background from favicon
        if (favicon.isNotBlank()) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(favicon)
                    .size(coil3.size.Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left panel ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .padding(start = 64.dp, top = 48.dp, bottom = 48.dp, end = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                Surface(
                    onClick = onNavigateBack,
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Back", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Artwork preview
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)
                    if (favicon.isNotBlank()) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(favicon)
                                .size(coil3.size.Size.ORIGINAL)
                                .build(),
                            contentDescription = "Preview",
                            filterQuality = FilterQuality.High,
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = name.ifBlank { if (isEditing) "Edit Station" else "New Station" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2
                )

                Spacer(Modifier.weight(1f))

                val hasUnsavedChanges = if (isEditing && station != null) {
                    name != station.name ||
                    url != station.url ||
                    favicon != station.favicon ||
                    tags != station.tags.joinToString(", ") ||
                    countryCode != station.countryCode ||
                    languageCodes != station.languageCodes.joinToString(", ") ||
                    homepage != station.homepage ||
                    probedCodec != station.codec ||
                    probedBitrate != station.bitrate
                } else {
                    true
                }

                var isUploading by remember { mutableStateOf(false) }
                val showUploadMode = true
                var uploadMode by remember { mutableStateOf(false) } // Default to local save mode

                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Main action button
                    Button(
                        onClick = {
                            if (uploadMode && showUploadMode) {
                                isUploading = true
                                val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                viewModel.uploadStationToRadioBrowser(
                                    stationUuid = if (isEditing) station!!.stationUuid else "",
                                    name = name,
                                    url = url,
                                    homepage = homepage,
                                    favicon = favicon,
                                    countryCode = countryCode,
                                    languageCodes = langList,
                                    tags = tagList,
                                    codec = probedCodec,
                                    bitrate = probedBitrate,
                                    onSuccess = {
                                        isUploading = false
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isUploading = false
                                    }
                                )
                            } else {
                                if (isEditing && station != null) {
                                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    viewModel.updateStation(
                                        stationUuid = station.stationUuid,
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tagList,
                                        countryCode = countryCode,
                                        languageCodes = langList,
                                        homepage = homepage,
                                        codec = probedCodec,
                                        bitrate = probedBitrate
                                    )
                                } else {
                                    viewModel.addStation(
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tags,
                                        countryCode = countryCode,
                                        languageCodes = languageCodes,
                                        homepage = homepage,
                                        codec = probedCodec,
                                        bitrate = probedBitrate
                                    )
                                }
                                onNavigateBack()
                            }
                        },
                        enabled = canSave && (!isEditing || hasUnsavedChanges || (station?.isCustom == true && uploadMode)) && !isUploading,
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        ),
                        shape = ButtonDefaults.shape(
                            shape = if (showUploadMode) RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp) else RoundedCornerShape(12.dp)
                        ),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        AnimatedContent(
                            targetState = uploadMode && showUploadMode,
                            label = "saveButtonAnimation"
                        ) { isUpload ->
                            Text(
                                text = if (isUpload) (if (isEditing) "Upload & Save" else "Upload & Add") else (if (isEditing) "Save" else "Add"),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Split toggle button
                    if (showUploadMode) {
                        Button(
                            onClick = { uploadMode = !uploadMode },
                            enabled = true,
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            ),
                            shape = ButtonDefaults.shape(
                                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                            ),
                            modifier = Modifier.width(64.dp).fillMaxHeight()
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription = "Toggle save mode",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Right panel — form ───────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 48.dp, bottom = 48.dp, end = 96.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = if (isEditing) "Edit Station" else "Add Custom Station",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.height(8.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.border.copy(alpha = 0.5f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { androidx.compose.material3.Text("Name *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFieldFocusRequester),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        viewModel.checkDuplicateUrl(it)
                    },
                    label = { androidx.compose.material3.Text("Stream URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                OutlinedTextField(
                    value = favicon,
                    onValueChange = { favicon = it },
                    label = { androidx.compose.material3.Text("Favicon URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it },
                        label = { androidx.compose.material3.Text("Country Code (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                        )
                    )
                    OutlinedTextField(
                        value = languageCodes,
                        onValueChange = { languageCodes = it },
                        label = { androidx.compose.material3.Text("Languages (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                        )
                    )
                }

                OutlinedTextField(
                    value = homepage,
                    onValueChange = { homepage = it },
                    label = { androidx.compose.material3.Text("Homepage URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.lowercase() },
                    label = { androidx.compose.material3.Text("Tags (comma separated, Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                val bitrateText = if (probedBitrate > 0) "$probedBitrate kbps" else "-"
                val isCodecUnknown = probedCodec.equals("unknown", ignoreCase = true) || probedCodec.isBlank()
                val codecText = if (!isCodecUnknown) probedCodec.uppercase() else "-"
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.material3.Text(
                                    text = "$codecText • $bitrateText",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isProbing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).padding(4.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        if (url.startsWith("http")) {
                                            isProbing = true
                                            coroutineScope.launch {
                                                val result = viewModel.probeStream(url)
                                                if (result != null) {
                                                    handleProbeResult(result)
                                                }
                                                isProbing = false
                                            }
                                        }
                                    }
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Filled.Refresh,
                                        contentDescription = "Refresh Metadata"
                                    )
                                }
                            }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "* Required fields",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                if (isEditing && station?.isCustom == true) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                val duplicateStations by viewModel.duplicateStations.collectAsStateWithLifecycle()
                val isCheckingUrl by viewModel.isCheckingUrl.collectAsStateWithLifecycle()

                if ((isCheckingUrl || duplicateStations.isNotEmpty()) && (!isEditing || station?.isCustom == true)) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.edit_station_similar_stations),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isCheckingUrl) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(duplicateStations) { s ->
                                androidx.compose.material3.Card(
                                    modifier = Modifier.width(160.dp),
                                    onClick = { onPlayStation(s) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        androidx.compose.material3.Text(
                                            text = s.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                        if (s.country.isNotBlank()) {
                                            androidx.compose.material3.Text(
                                                text = s.country,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
