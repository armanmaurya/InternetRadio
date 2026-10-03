package com.armanmaurya.internetradio.core.media.player

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.armanmaurya.internetradio.service.playback.engine.RetryStateTracker
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class RadioPlayer (player: Player, val retryStateTracker: RetryStateTracker) : ForwardingSimpleBasePlayer(player) {

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val state = state

        if (playWhenReady && !state.playWhenReady) {

            val playbackState = state.playbackState
            retryStateTracker.reset()
            if (playbackState == STATE_READY || playbackState == STATE_BUFFERING) {
                handleStop()
                handlePrepare()
            }
        }

        return super.handleSetPlayWhenReady(playWhenReady)
    }
}