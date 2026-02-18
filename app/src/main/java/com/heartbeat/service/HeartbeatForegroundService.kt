package com.heartbeaten.service

import com.heartbeaten.audio.HeartbeatAudioEngine
import com.heartbeaten.audio.ListenMode
import com.heartbeaten.ble.BleHeartRateSource
import com.heartbeaten.ble.HeartRateNormalizer
import com.heartbeaten.network.PeerTransport
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
 * In Android production this should be wrapped by an actual foreground [android.app.Service]
 * that owns notifications, wake locks, and platform lifecycle callbacks.
 */
class HeartbeatForegroundService(
    private val localUserId: String,
    private val bleHeartRateSource: BleHeartRateSource,
    private val peerTransport: PeerTransport,
    private val audioEngine: HeartbeatAudioEngine,
    private val normalizer: HeartRateNormalizer = HeartRateNormalizer(),
    private val onError: (Throwable) -> Unit = {},
) {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val startStopMutex = Mutex()

    private var streamJob: Job? = null
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
    }

    suspend fun stop() = startStopMutex.withLock {
        streamJob?.cancelAndJoin()
        streamJob = null
        lastLocalBpm = null
        lastPartnerBpm = null

        bleHeartRateSource.stop()
        peerTransport.disconnect()
        audioEngine.pause()

        _isRunning.value = false
    }

    fun playAudio() = audioEngine.play()
    fun pauseAudio() = audioEngine.pause()

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
        }
    }
}
