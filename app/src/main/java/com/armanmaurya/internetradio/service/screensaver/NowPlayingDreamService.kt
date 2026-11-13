package com.armanmaurya.internetradio.service.screensaver

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.armanmaurya.internetradio.domain.controller.PlayerController
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.ui.shared.theme.TvInternetRadioTheme
import com.armanmaurya.internetradio.ui.tv.screensaver.NowPlayingScreensaver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System screensaver ("Internet Radio" under the TV's Settings → Screensaver). Shows the same
 * now-playing view as the in-app screensaver, or just the clock when nothing is playing.
 */
@AndroidEntryPoint
class NowPlayingDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var settingsRepository: SettingsRepository

    // ComposeView needs a lifecycle and saved-state owner, which a DreamService doesn't provide.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any key or touch wakes up the TV
        isFullscreen = true

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NowPlayingDreamService)
            setViewTreeSavedStateRegistryOwner(this@NowPlayingDreamService)
            setContent {
                val session by playerController.playbackSession.collectAsStateWithLifecycle()
                val prefs by settingsRepository.appPreferencesFlow
                    .collectAsStateWithLifecycle(initialValue = AppPreferences())
                TvInternetRadioTheme(appPreferences = prefs) {
                    NowPlayingScreensaver(
                        station = session.currentStation,
                        trackTitle = session.cleanTrackName ?: session.currentTrack,
                        artist = session.cleanArtistName,
                        coverArtUri = session.trackCoverArtUri
                    )
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
