package com.heartbeaten.service

import com.heartbeaten.audio.HeartbeatAudioEngine
import com.heartbeaten.ble.BleHeartRateSource
import com.heartbeaten.network.HeartRateFrame
import com.heartbeaten.network.PeerTransport
import com.heartbeaten.network.TransportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HeartbeatForegroundServiceTest {
    @Test
    fun `start and stop drive foreground notifier lifecycle`() = runTest {
        val ble = FakeBleHeartRateSource()
        val transport = FakePeerTransport()
        val audio = HeartbeatAudioEngine(initialBpm = 72)
        val notifier = RecordingNotifier()
        val service = HeartbeatForegroundService(
            localUserId = "alice",
            bleHeartRateSource = ble,
            peerTransport = transport,
            audioEngine = audio,
            notifier = notifier,
        )

        service.start("session1")
        transport.connectionStateMutable.value = TransportState.CONNECTED
        ble.emit(80)

        service.stop()

        assertEquals(listOf("session1"), notifier.startedSessions)
        assertEquals(true, notifier.updates.any { it.state == "Connected" })
        assertEquals(80, notifier.updates.last { it.bpm != null }.bpm)
        assertEquals(1, notifier.stopCalls)
        assertEquals(1, ble.startCalls)
        assertEquals(1, ble.stopCalls)
    }

    @Test
    fun `failed start rolls back runtime state and stops notifier`() = runTest {
        val ble = FakeBleHeartRateSource()
        val transport = FakePeerTransport(failOnConnect = true)
        val notifier = RecordingNotifier()
        val service = HeartbeatForegroundService(
            localUserId = "alice",
            bleHeartRateSource = ble,
            peerTransport = transport,
            audioEngine = HeartbeatAudioEngine(),
            notifier = notifier,
        )

        assertFailsWith<IllegalStateException> {
            service.start("session1")
        }

        assertFalse(service.isRunning.value)
        assertEquals(1, notifier.stopCalls)
        assertEquals(true, notifier.updates.any { it.state == "Connection failed" })
        assertEquals(1, ble.stopCalls)
        assertEquals(1, transport.disconnectCalls)
    }
}

private class FakeBleHeartRateSource : BleHeartRateSource {
    private val _heartRate = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    var startCalls: Int = 0
    var stopCalls: Int = 0

    override suspend fun start() {
        startCalls += 1
    }

    override suspend fun stop() {
        stopCalls += 1
    }

    override val heartRateBpm: Flow<Int> = _heartRate

    suspend fun emit(bpm: Int) {
        _heartRate.emit(bpm)
    }
}

private class FakePeerTransport(
    private val failOnConnect: Boolean = false,
) : PeerTransport {
    private val _incoming = MutableSharedFlow<HeartRateFrame>(extraBufferCapacity = 16)
    val connectionStateMutable = MutableStateFlow(TransportState.CONNECTING)
    var connectCalls = 0
    var disconnectCalls = 0

    override val incomingFrames: Flow<HeartRateFrame> = _incoming
    override val connectionState: StateFlow<TransportState> = connectionStateMutable.asStateFlow()

    override suspend fun connect(sessionCode: String) {
        connectCalls += 1
        if (failOnConnect) {
            throw IllegalStateException("connect failed")
        }
    }

    override suspend fun disconnect() {
        disconnectCalls += 1
    }

    override suspend fun send(frame: HeartRateFrame) = Unit
}

private class RecordingNotifier : SessionForegroundNotifier {
    val startedSessions = mutableListOf<String>()
    val updates = mutableListOf<Update>()
    var stopCalls: Int = 0

    override fun start(sessionCode: String) {
        startedSessions += sessionCode
    }

    override fun update(sessionCode: String, state: String, bpm: Int?) {
        updates += Update(sessionCode, state, bpm)
    }

    override fun stop() {
        stopCalls += 1
    }

    data class Update(
        val sessionCode: String,
        val state: String,
        val bpm: Int?,
    )
}
