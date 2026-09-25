package com.armanmaurya.internetradio.ui.widget.actions

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.armanmaurya.internetradio.service.PlaybackService

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE"
        if (PlaybackService.isRunning) {
            // Service is already in foreground — use a broadcast (works on all OEM devices)
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        } else {
            // Service is not running — start it (cold boot / restore last station)
            context.startForegroundService(
                Intent(context, PlaybackService::class.java).apply { this.action = action }
            )
        }
    }
}
