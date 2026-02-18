package com.heartbeaten

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import com.heartbeaten.ui.HeartbeatScreen
import com.heartbeaten.ui.HeartbeatViewModel

class MainActivity : ComponentActivity() {
    private val viewModel = HeartbeatViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.onPermissionStatusChanged(PermissionRequirements.hasRequiredPermissions(this))

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val requiredPermissions = remember { PermissionRequirements.requiredPermissionsForCurrentSdk() }
            val latestVm by rememberUpdatedState(viewModel)

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                latestVm.onPermissionStatusChanged(PermissionRequirements.hasRequiredPermissions(this))
                latestVm.onPermissionRequestHandled()
            }

            LaunchedEffect(uiState.shouldRequestPermissions) {
                if (!uiState.shouldRequestPermissions) return@LaunchedEffect
                permissionLauncher.launch(requiredPermissions)
            }

            HeartbeatScreen(
                state = uiState,
                onSessionCodeChanged = viewModel::onSessionCodeChanged,
                onSharingToggled = viewModel::onSharingToggled,
                onListenModeChanged = viewModel::onListenModeChanged,
                onPlayPauseClicked = viewModel::onPlayPauseClicked,
                onStartStopStreamingClicked = {
                    val canProceed = viewModel.onStartStopRequested()
                    if (canProceed) {
                        // TODO: Wire to AndroidHeartbeatSessionRuntime start/stop orchestration.
                    }
                },
                onRequestPermissionsClicked = {
                    viewModel.onPermissionRequestHandled()
                    permissionLauncher.launch(requiredPermissions)
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onPermissionStatusChanged(PermissionRequirements.hasRequiredPermissions(this))
    }
}
