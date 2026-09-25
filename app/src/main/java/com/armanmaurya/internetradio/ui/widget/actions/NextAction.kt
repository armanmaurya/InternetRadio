package com.armanmaurya.internetradio.ui.widget.actions

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.armanmaurya.internetradio.service.PlaybackService

class NextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT"
        if (PlaybackService.isRunning) {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        } else {
            context.startForegroundService(
                Intent(context, PlaybackService::class.java).apply { this.action = action }
            )
        }
    }
}
