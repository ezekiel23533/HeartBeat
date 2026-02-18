package com.heartbeaten.ble

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over Mi Band 6 BLE HR stream.
 */
interface BleHeartRateSource {
    suspend fun start()
    suspend fun stop()
    val heartRateBpm: Flow<Int>
}
