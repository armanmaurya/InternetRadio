package com.armanmaurya.internetradio.ui.mobile

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.service.PlaybackService
import com.armanmaurya.internetradio.ui.tv.TvActivity
import com.armanmaurya.internetradio.ui.widget.NowPlayingWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MobileActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetController: WidgetController

    private val _intentFlow = MutableSharedFlow<Intent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { _intentFlow.tryEmit(it) }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.let { _intentFlow.tryEmit(it) }
        installSplashScreen()
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        initAppLocaleAndWidgets()
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            MobileApp(
                intentFlow = _intentFlow,
                widthSizeClass = windowSizeClass.widthSizeClass
            )
        }
    }

    private fun initAppLocaleAndWidgets() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val activeTag = if (currentLocales.isEmpty) "System" else currentLocales[0]?.toLanguageTag() ?: "System"
        lifecycleScope.launch {
            settingsRepository.setAppLanguage(activeTag)

            if (!PlaybackService.isRunning) {
                widgetController.updatePlayback(
                    title = getString(R.string.widget_nothing_playing),
                    artist = "",
                    artworkUrl = null,
                    isPlaying = false,
                    hasNext = false,
                    hasPrev = false
                )
            }
            NowPlayingWidget().updateAll(this@MobileActivity)
        }
    }
}