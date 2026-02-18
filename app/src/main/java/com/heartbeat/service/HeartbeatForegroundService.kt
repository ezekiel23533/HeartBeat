package com.heartbeaten.service

import com.heartbeaten.audio.HeartbeatAudioEngine
import com.heartbeaten.audio.ListenMode
import com.heartbeaten.ble.BleHeartRateSource
import com.heartbeaten.ble.HeartRateNormalizer
import com.heartbeaten.network.PeerTransport
import com.heartbeaten.network.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service-layer orchestrator.
 *
 * Android-specific foreground behavior is delegated to [SessionForegroundNotifier],
 * allowing this orchestration layer to remain testable and platform-agnostic.
 */
class HeartbeatForegroundService(
    private val localUserId: String,
    private val bleHeartRateSource: BleHeartRateSource,
    private val peerTransport: PeerTransport,
    private val audioEngine: HeartbeatAudioEngine,
    private val normalizer: HeartRateNormalizer = HeartRateNormalizer(),
    private val notifier: SessionForegroundNotifier = SessionForegroundNotifier.NoOp,
    private val onError: (Throwable) -> Unit = {},
) {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val startStopMutex = Mutex()

    private var streamJob: Job? = null
    private var sessionCode: String? = null
    private var latestTransportState: TransportState = TransportState.IDLE
    private var lastLocalBpm: Int? = null
    private var lastPartnerBpm: Int? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    var listenMode: ListenMode = ListenMode.PARTNER
    var isSharingEnabled: Boolean = false

    suspend fun start(sessionCode: String) = startStopMutex.withLock {
        if (_isRunning.value) return
        require(localUserId.isNotBlank()) { "localUserId cannot be blank" }
        require(sessionCode.isNotBlank()) { "sessionCode cannot be blank" }

        latestTransportState = TransportState.CONNECTING
        this.sessionCode = sessionCode

        try {
            notifier.start(sessionCode)
            notifier.update(sessionCode, state = transportStateLabel(TransportState.CONNECTING), bpm = null)

            bleHeartRateSource.start()
            peerTransport.connect(sessionCode)

            streamJob = scope.launch {
                launch {
                    bleHeartRateSource.heartRateBpm.collectLatest { bpm ->
                        try {
                            val now = System.currentTimeMillis()
                            val frame = normalizer.normalize(
                                rawBpm = bpm,
                                sourceUserId = localUserId,
                                timestampMillis = now,
                                nowMillis = now,
                            ) ?: return@collectLatest

                            lastLocalBpm = frame.bpm
                            if (isSharingEnabled) {
                                peerTransport.send(frame)
                            }
                            pushMixedBpmIfNeeded()
                        } catch (t: Throwable) {
                            onError(t)
                        }
                    }
                }

                launch {
                    peerTransport.connectionState.collectLatest { state ->
                        latestTransportState = state
                        pushNotificationUpdate(currentBpm = null)
                    }
                }

                launch {
                    peerTransport.incomingFrames.collectLatest { frame ->
                        try {
                            if (frame.sourceUserId == localUserId) return@collectLatest
                            lastPartnerBpm = frame.bpm
                            pushMixedBpmIfNeeded()
                        } catch (t: Throwable) {
                            onError(t)
                        }
                    }
                }
            }

            _isRunning.value = true
        } catch (t: Throwable) {
            rollbackFailedStart(sessionCode)
            onError(t)
            throw t
        }
    }

    suspend fun stop() = startStopMutex.withLock {
        streamJob?.cancelAndJoin()
        streamJob = null
        val activeSessionCode = sessionCode
        sessionCode = null
        lastLocalBpm = null
        lastPartnerBpm = null
        latestTransportState = TransportState.IDLE

        bleHeartRateSource.stop()
        peerTransport.disconnect()
        audioEngine.pause()

        activeSessionCode?.let { notifier.update(it, transportStateLabel(TransportState.IDLE), bpm = null) }
        notifier.stop()

        _isRunning.value = false
    }

    fun playAudio() = audioEngine.play()
    fun pauseAudio() = audioEngine.pause()

    private suspend fun rollbackFailedStart(sessionCode: String) {
        streamJob?.cancelAndJoin()
        streamJob = null

        lastLocalBpm = null
        lastPartnerBpm = null
        latestTransportState = TransportState.IDLE
        this.sessionCode = null
        _isRunning.value = false

        runCatching { bleHeartRateSource.stop() }
        runCatching { peerTransport.disconnect() }

        notifier.update(sessionCode, transportStateLabel(TransportState.FAILED), bpm = null)
        notifier.stop()
    }

    private fun pushMixedBpmIfNeeded() {
        val target = when (listenMode) {
            ListenMode.SELF -> lastLocalBpm
            ListenMode.PARTNER -> lastPartnerBpm
            ListenMode.BOTH -> {
                val local = lastLocalBpm
                val partner = lastPartnerBpm
                when {
                    local == null -> partner
                    partner == null -> local
                    else -> (local + partner) / 2
                }
            }
        }
        if (target != null) {
            audioEngine.updateBpm(target)
            pushNotificationUpdate(currentBpm = target)
        }
    }

    private fun pushNotificationUpdate(currentBpm: Int?) {
        val activeSessionCode = sessionCode ?: return
        notifier.update(
            sessionCode = activeSessionCode,
            state = transportStateLabel(latestTransportState),
            bpm = currentBpm,
        )
    }

    private fun transportStateLabel(state: TransportState): String = when (state) {
        TransportState.IDLE -> "Idle"
        TransportState.CONNECTING -> "Connecting"
        TransportState.CONNECTED -> "Connected"
        TransportState.FALLBACK_RELAY -> "Relay mode"
        TransportState.FAILED -> "Connection failed"
    }
}
