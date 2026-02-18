package com.heartbeat.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class HeartbeatAudioEngineTest {
    @Test
    fun `beat interval maps bpm to milliseconds`() {
        val engine = HeartbeatAudioEngine(initialBpm = 60)
        assertEquals(1000L, engine.beatIntervalMillis())

        engine.updateBpm(120)
        assertEquals(833L, engine.beatIntervalMillis())
    }

    @Test
    fun `bpm changes are smoothed by max step`() {
        val engine = HeartbeatAudioEngine(initialBpm = 70)

        engine.updateBpm(160)
        assertEquals(82, engine.currentBpm.value)

        engine.updateBpm(10)
        assertEquals(70, engine.currentBpm.value)
    }
}
