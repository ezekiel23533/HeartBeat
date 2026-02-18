package com.heartbeaten.ui

import com.heartbeaten.network.TransportState
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
