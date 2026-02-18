package com.heartbeaten.network

import kotlinx.coroutines.flow.Flow

/**
 * WebRTC-first transport with optional relay fallback.
 *
 * Implementations should:
 * - attempt direct P2P first
 * - expose fallback transition via [TransportState.FALLBACK_RELAY]
 * - guarantee ordering for frames from the same sender
 */
interface PeerTransport {
    val incomingFrames: Flow<HeartRateFrame>
    val connectionState: Flow<TransportState>

    suspend fun connect(sessionCode: String)
    suspend fun disconnect()
    suspend fun send(frame: HeartRateFrame)
}

enum class TransportState {
    IDLE,
    CONNECTING,
    CONNECTED,
    FALLBACK_RELAY,
    FAILED,
}
