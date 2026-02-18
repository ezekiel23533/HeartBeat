package com.heartbeaten.service

import android.content.Context
import com.heartbeaten.audio.HeartbeatAudioEngine
import com.heartbeaten.ble.BleHeartRateSource
import com.heartbeaten.ble.HeartRateNormalizer
import com.heartbeaten.network.PeerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Production wiring helper that binds:
 * - session orchestration [HeartbeatForegroundService]
 * - Android foreground notification bridge [AndroidSessionForegroundNotifier]
 * - notification stop action receiver [AndroidSessionStopActionHandler]
 *
 * This gives app layer a single integration surface for end-to-end session lifecycle.
 */
class AndroidHeartbeatSessionRuntime private constructor(
    private val scope: CoroutineScope,
    val service: HeartbeatForegroundService,
    private val stopActionHandler: AndroidSessionStopActionHandler,
    private val onError: (Throwable) -> Unit,
) {
    fun attach() {
        stopActionHandler.register()
    }

    fun detach() {
        stopActionHandler.unregister()
    }

    fun start(sessionCode: String) {
        scope.launch {
            runCatching { service.start(sessionCode) }
                .onFailure(onError)
        }
    }

    fun stop() {
        scope.launch {
            runCatching { service.stop() }
                .onFailure(onError)
        }
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            localUserId: String,
            bleHeartRateSource: BleHeartRateSource,
            peerTransport: PeerTransport,
            audioEngine: HeartbeatAudioEngine,
            normalizer: HeartRateNormalizer = HeartRateNormalizer(),
            onError: (Throwable) -> Unit = {},
        ): AndroidHeartbeatSessionRuntime {
            val notifier = AndroidSessionForegroundNotifier(context)
            lateinit var service: HeartbeatForegroundService
            service = HeartbeatForegroundService(
                localUserId = localUserId,
                bleHeartRateSource = bleHeartRateSource,
                peerTransport = peerTransport,
                audioEngine = audioEngine,
                normalizer = normalizer,
                notifier = notifier,
                onError = onError,
            )

            val stopHandler = AndroidSessionStopActionHandler(
                context = context,
                scope = scope,
                onStopRequested = { service.stop() },
            )

            return AndroidHeartbeatSessionRuntime(
                scope = scope,
                service = service,
                stopActionHandler = stopHandler,
                onError = onError,
            )
        }
    }
}
