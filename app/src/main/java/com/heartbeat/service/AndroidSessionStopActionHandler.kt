package com.heartbeaten.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles foreground-notification Stop action events and forwards them to session orchestration.
 *
 * App layer should register this while session controls are alive to guarantee end-to-end stop.
 */
class AndroidSessionStopActionHandler(
    context: Context,
    private val scope: CoroutineScope,
    private val onStopRequested: suspend () -> Unit,
) {
    private val appContext = context.applicationContext
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AndroidHeartbeatForegroundService.ACTION_STOP_REQUESTED) return
            scope.launch {
                onStopRequested()
            }
        }
    }

    fun register() {
        if (isRegistered) return

        val filter = IntentFilter(AndroidHeartbeatForegroundService.ACTION_STOP_REQUESTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        appContext.unregisterReceiver(receiver)
        isRegistered = false
    }
}
