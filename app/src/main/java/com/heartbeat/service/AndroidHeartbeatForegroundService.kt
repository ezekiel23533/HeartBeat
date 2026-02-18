package com.heartbeaten.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Android foreground service shell that keeps the app process alive while an active heartbeat
 * session is running.
 *
 * This service owns the required persistent notification to reduce risk of background kills.
 */
class AndroidHeartbeatForegroundService : Service() {
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionCode = intent.getStringExtra(EXTRA_SESSION_CODE).orEmpty().trim().uppercase()
                startAsForeground(sessionCode)
            }

            ACTION_UPDATE_STATE -> {
                val state = intent.getStringExtra(EXTRA_STATE).orEmpty()
                val bpm = intent.getIntExtra(EXTRA_BPM, -1)
                updateNotification(
                    sessionCode = intent.getStringExtra(EXTRA_SESSION_CODE).orEmpty(),
                    state = state,
                    bpm = bpm,
                )
            }

            ACTION_STOP -> stopAndRemoveNotification()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(sessionCode: String) {
        val notification = buildNotification(sessionCode = sessionCode, state = "Connecting", bpm = -1)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(sessionCode: String, state: String, bpm: Int) {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(sessionCode = sessionCode, state = state, bpm = bpm),
        )
    }

    private fun stopAndRemoveNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(sessionCode: String, state: String, bpm: Int): Notification {
        val sessionSuffix = if (sessionCode.isBlank()) "" else " • Session $sessionCode"
        val bpmSuffix = if (bpm >= 0) " • ${bpm} BPM" else ""

        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AndroidHeartbeatForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("HeartBeat active")
            .setContentText("$state$sessionSuffix$bpmSuffix")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "HeartBeat session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps heartbeat sharing active in background"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "heartbeat.session"
        private const val NOTIFICATION_ID = 1024

        private const val ACTION_START = "com.heartbeaten.action.START_FOREGROUND"
        private const val ACTION_UPDATE_STATE = "com.heartbeaten.action.UPDATE_STATE"
        private const val ACTION_STOP = "com.heartbeaten.action.STOP_FOREGROUND"

        private const val EXTRA_SESSION_CODE = "extra_session_code"
        private const val EXTRA_STATE = "extra_state"
        private const val EXTRA_BPM = "extra_bpm"

        fun startIntent(context: Context, sessionCode: String): Intent =
            Intent(context, AndroidHeartbeatForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_CODE, sessionCode)

        fun updateIntent(context: Context, sessionCode: String, state: String, bpm: Int): Intent =
            Intent(context, AndroidHeartbeatForegroundService::class.java)
                .setAction(ACTION_UPDATE_STATE)
                .putExtra(EXTRA_SESSION_CODE, sessionCode)
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_BPM, bpm)

        fun stopIntent(context: Context): Intent =
            Intent(context, AndroidHeartbeatForegroundService::class.java)
                .setAction(ACTION_STOP)
    }
}
