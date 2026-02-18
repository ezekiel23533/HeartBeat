package com.heartbeat.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HeartRateNormalizerTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `valid bpm yields frame and increments sequence`() {
        val normalizer = HeartRateNormalizer()

        val first = normalizer.normalize(72, "alice", now, now)
        val second = normalizer.normalize(73, "alice", now + 1000, now + 1000)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(0L, first.sequence)
        assertEquals(1L, second.sequence)
    }

    @Test
    fun `invalid values are rejected`() {
        val normalizer = HeartRateNormalizer()

        assertNull(normalizer.normalize(10, "alice", now, now))
        assertNull(normalizer.normalize(300, "alice", now, now))
        assertNull(normalizer.normalize(80, "", now, now))
        assertNull(normalizer.normalize(80, "alice", now + 10_000, now))
    }
}
