package com.heartbeaten.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heartbeaten.audio.ListenMode

@Composable
fun HeartbeatScreen(
    state: HeartbeatUiState,
    onSessionCodeChanged: (String) -> Unit,
    onSharingToggled: (Boolean) -> Unit,
    onListenModeChanged: (ListenMode) -> Unit,
    onPlayPauseClicked: () -> Unit,
    onStartStopStreamingClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HeartBeat", style = MaterialTheme.typography.headlineMedium)
        Text("Current BPM: ${state.currentBpm}")
        Text("Transport: ${state.transportState}")

        state.errorMessage?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = state.sessionCode,
            onValueChange = onSessionCodeChanged,
            label = { Text("Session code") },
            supportingText = {
                Text("Min 6 chars, share only with trusted peer")
            },
        )

        Text("Share my heart rate")
        Switch(
            checked = state.isSharingEnabled,
            onCheckedChange = onSharingToggled,
        )

        Text("Listen mode")
        ListenMode.entries.forEach { mode ->
            RowOption(
                text = mode.name,
                selected = state.listenMode == mode,
                onSelected = { onListenModeChanged(mode) },
            )
        }

        Button(
            onClick = onStartStopStreamingClicked,
            enabled = state.canStartSession,
        ) {
            Text("Start / Stop Streaming")
        }

        Button(onClick = onPlayPauseClicked) {
            Text(if (state.isPlaying) "Pause" else "Play")
        }
    }
}

@Composable
private fun RowOption(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(text)
    }
}
