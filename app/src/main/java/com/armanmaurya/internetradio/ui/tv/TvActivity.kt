package com.armanmaurya.internetradio.ui.tv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import com.armanmaurya.internetradio.ui.tv.screens.home.HomeScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class TvActivity : AppCompatActivity() {

    // Requests to show the player (media notification, the TV's now-playing card). Conflated and
    // consumed once, so a screen rebuild doesn't reopen it.
    private val openPlayerRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On a rebuild the original intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            HomeScreen(openPlayerRequests = openPlayerRequests.receiveAsFlow())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (wantsPlayer(intent)) openPlayerRequests.trySend(Unit)
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER"
        const val EXTRA_OPEN_PLAYER = "open_player_sheet"

        /** Whether [intent] asks for the player. Never throws, even on malformed extras. */
        fun wantsPlayer(intent: Intent?): Boolean = try {
            intent != null &&
                (intent.action == ACTION_OPEN_PLAYER || intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false))
        } catch (_: Exception) {
            false
        }
    }
}
