package com.heartbeaten.ble

import com.heartbeaten.network.HeartRateFrame

/**
 * Validates and normalizes raw HR values before transport.
 */
class HeartRateNormalizer(
    private val minBpm: Int = 35,
    private val maxBpm: Int = 220,
    private val maxFutureSkewMillis: Long = 3_000,
) {
    private var sequence: Long = 0

    fun normalize(rawBpm: Int, sourceUserId: String, timestampMillis: Long, nowMillis: Long): HeartRateFrame? {
        if (sourceUserId.isBlank()) return null
        if (rawBpm !in minBpm..maxBpm) return null
        if (timestampMillis > nowMillis + maxFutureSkewMillis) return null

        return HeartRateFrame(
            bpm = rawBpm,
            timestampMillis = timestampMillis,
            sourceUserId = sourceUserId,
            sequence = sequence++,
        )
    }
}
