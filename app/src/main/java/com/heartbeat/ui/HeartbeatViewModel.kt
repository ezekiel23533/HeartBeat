package com.heartbeaten.ui

import com.heartbeaten.audio.ListenMode
import com.heartbeaten.network.TransportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class HeartbeatViewModel {
    private val _uiState = MutableStateFlow(HeartbeatUiState())
    val uiState: StateFlow<HeartbeatUiState> = _uiState

    fun onSessionCodeChanged(code: String) {
        val normalized = code.trim().uppercase()
        _uiState.update {
            it.copy(
                sessionCode = normalized,
                canStartSession = normalized.length >= MIN_SESSION_CODE_LENGTH,
                errorMessage = null,
            )
        }
    }

    fun onSharingToggled(enabled: Boolean) {
        _uiState.update { it.copy(isSharingEnabled = enabled) }
    }

    fun onListenModeChanged(mode: ListenMode) {
        _uiState.update { it.copy(listenMode = mode) }
    }

    fun onPlayPauseClicked() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun onTransportStateChanged(state: TransportState) {
        _uiState.update { current ->
            current.copy(
                transportState = state,
                errorMessage = if (state == TransportState.FAILED) {
                    "Unable to connect. Check network and session code."
                } else {
                    null
                },
            )
        }
    }

    fun onBpmUpdated(bpm: Int) {
        _uiState.update { it.copy(currentBpm = bpm.coerceAtLeast(0)) }
    }

    fun onPermissionStatusChanged(hasRequiredPermissions: Boolean) {
        _uiState.update {
            it.copy(
                hasRequiredPermissions = hasRequiredPermissions,
                shouldRequestPermissions = false,
                errorMessage = if (hasRequiredPermissions) null else it.errorMessage,
            )
        }
    }

    /**
     * Returns true when caller can proceed with streaming start/stop action.
     * Returns false when caller must open runtime permission request flow first.
     */
    fun onStartStopRequested(): Boolean {
        val state = _uiState.value
        return if (state.hasRequiredPermissions) {
            true
        } else {
            _uiState.update {
                it.copy(
                    shouldRequestPermissions = true,
                    errorMessage = "Bluetooth and notification permissions are required.",
                )
            }
            false
        }
    }

    fun onPermissionRequestHandled() {
        _uiState.update { it.copy(shouldRequestPermissions = false) }
    }

    companion object {
        private const val MIN_SESSION_CODE_LENGTH = 6
    }
}
