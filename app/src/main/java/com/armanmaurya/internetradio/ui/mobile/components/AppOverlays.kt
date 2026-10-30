package com.armanmaurya.internetradio.ui.mobile.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.config.StoreConfig
import com.armanmaurya.internetradio.ui.shared.components.UpdateBottomSheet
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryRemovalViewModel
import com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel
import kotlinx.coroutines.flow.first

@Composable
fun AppOverlays(
    mainViewModel: MainViewModel,
    libraryRemovalViewModel: LibraryRemovalViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateAvailable by mainViewModel.updateAvailable.collectAsStateWithLifecycle()
    val showReviewPrompt by mainViewModel.showReviewPrompt.collectAsStateWithLifecycle()
    val pendingRemoval by libraryRemovalViewModel.pendingRemoval.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val prefs = mainViewModel.appPreferences.first()
        if (!prefs.disableUpdateCheck) {
            if (StoreConfig.isPlayStoreBuild) {
                (context as? Activity)?.let { StoreConfig.checkPlayStoreUpdate(it) }
            } else {
                mainViewModel.checkForUpdates()
            }
        }
    }

    updateAvailable?.let { release ->
        UpdateBottomSheet(
            release = release,
            onDismiss = { mainViewModel.dismissUpdate() },
            onConfirm = {
                try {
                    uriHandler.openUri(release.html_url)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.html_url)))
                }
            }
        )
    }

    pendingRemoval?.let { removal ->
        RemoveStationBottomSheet(
            removal = removal,
            onDismissRequest = { libraryRemovalViewModel.dismissRemoval() },
            onConfirm = { libraryRemovalViewModel.confirmRemoval(removal.stationUuid) }
        )
    }

    if (showReviewPrompt) {
        RateAppDialog(
            onRateClick = {
                mainViewModel.dismissReviewPrompt(hasRated = true)
                val url = if (StoreConfig.isPlayStoreBuild) {
                    "https://play.google.com/store/apps/details?id=${context.packageName}"
                } else {
                    "https://github.com/armanmaurya/InternetRadio"
                }
                try {
                    uriHandler.openUri(url)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            onDismissClick = { permanently ->
                mainViewModel.dismissReviewPrompt(hasRated = permanently)
            }
        )
    }
}

@Composable
fun rememberManualUpdateChecker(mainViewModel: MainViewModel): () -> Unit {
    val context = LocalContext.current
    val checkingMessage = stringResource(R.string.settings_checking_for_updates)
    val noUpdateMessage = stringResource(R.string.settings_no_update_available)

    return {
        Toast.makeText(context, checkingMessage, Toast.LENGTH_SHORT).show()
        if (StoreConfig.isPlayStoreBuild) {
            (context as? Activity)?.let { StoreConfig.checkPlayStoreUpdate(it, manualCheck = true) }
        } else {
            mainViewModel.checkForUpdates(force = true) { hasUpdate ->
                if (!hasUpdate) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, noUpdateMessage, Toast.LENGTH_SHORT).show()
                    } ?: Toast.makeText(context, noUpdateMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
