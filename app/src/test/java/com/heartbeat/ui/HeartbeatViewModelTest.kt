package com.heartbeaten.ui

import com.heartbeaten.network.TransportState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeartbeatViewModelTest {
    @Test
    fun `session code is normalized and validated`() {
        val vm = HeartbeatViewModel()

        vm.onSessionCodeChanged(" ab12cd ")

        val state = vm.uiState.value
        assertEquals("AB12CD", state.sessionCode)
        assertTrue(state.canStartSession)
    }

    @Test
    fun `transport failure sets and clears user facing error`() {
        val vm = HeartbeatViewModel()

        vm.onTransportStateChanged(TransportState.FAILED)
        assertEquals("Unable to connect. Check network and session code.", vm.uiState.value.errorMessage)

        vm.onTransportStateChanged(TransportState.CONNECTED)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `start request without permissions triggers permission UX state`() {
        val vm = HeartbeatViewModel()

        val canProceed = vm.onStartStopRequested()

        assertFalse(canProceed)
        assertTrue(vm.uiState.value.shouldRequestPermissions)
        assertEquals("Bluetooth and notification permissions are required.", vm.uiState.value.errorMessage)
    }

    @Test
    fun `permissions granted allows start request and clears prompt flag`() {
        val vm = HeartbeatViewModel()

        vm.onPermissionStatusChanged(hasRequiredPermissions = true)
        val canProceed = vm.onStartStopRequested()

        assertTrue(canProceed)
        assertFalse(vm.uiState.value.shouldRequestPermissions)
        assertTrue(vm.uiState.value.hasRequiredPermissions)
    }
}
