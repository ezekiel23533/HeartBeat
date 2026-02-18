package com.heartbeat.ui

import com.heartbeat.audio.ListenMode
import com.heartbeat.network.TransportState

data class HeartbeatUiState(
    val sessionCode: String = "",
    val isSharingEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val listenMode: ListenMode = ListenMode.PARTNER,
    val transportState: TransportState = TransportState.IDLE,
    val currentBpm: Int = 0,
    val canStartSession: Boolean = false,
    val errorMessage: String? = null,
)
