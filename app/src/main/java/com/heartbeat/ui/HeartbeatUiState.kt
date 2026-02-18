package com.heartbeaten.ui

import com.heartbeaten.audio.ListenMode
import com.heartbeaten.network.TransportState

data class HeartbeatUiState(
    val sessionCode: String = "",
    val isSharingEnabled: Boolean = false,
    val isPlaying: Boolean = false,
    val listenMode: ListenMode = ListenMode.PARTNER,
    val transportState: TransportState = TransportState.IDLE,
    val currentBpm: Int = 0,
    val canStartSession: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val shouldRequestPermissions: Boolean = false,
    val errorMessage: String? = null,
)
