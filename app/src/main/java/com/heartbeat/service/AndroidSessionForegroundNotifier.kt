package com.heartbeaten.service

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Android implementation of [SessionForegroundNotifier] backed by
 * [AndroidHeartbeatForegroundService].
 */
class AndroidSessionForegroundNotifier(
    context: Context,
) : SessionForegroundNotifier {
    private val appContext = context.applicationContext

    override fun start(sessionCode: String) {
        ContextCompat.startForegroundService(
            appContext,
            AndroidHeartbeatForegroundService.startIntent(appContext, sessionCode),
        )
    }

    override fun update(sessionCode: String, state: String, bpm: Int?) {
        appContext.startService(
            AndroidHeartbeatForegroundService.updateIntent(
                context = appContext,
                sessionCode = sessionCode,
                state = state,
                bpm = bpm ?: UNKNOWN_BPM,
            ),
        )
    }

    override fun stop() {
        appContext.startService(AndroidHeartbeatForegroundService.stopIntent(appContext))
    }

    private companion object {
        private const val UNKNOWN_BPM = -1
    }
}
