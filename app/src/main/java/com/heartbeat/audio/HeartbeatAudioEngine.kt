package com.heartbeaten.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Production-oriented heartbeat timing engine.
 *
 * The engine constrains invalid values and smooths abrupt BPM changes to avoid jarring playback.
 */
class HeartbeatAudioEngine(
    initialBpm: Int = 70,
    private val minBpm: Int = 35,
    private val maxBpm: Int = 220,
    private val maxStepPerUpdate: Int = 12,
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentBpm = MutableStateFlow(initialBpm.coerceIn(minBpm, maxBpm))
    val currentBpm: StateFlow<Int> = _currentBpm

    fun updateBpm(requestedBpm: Int) {
        val target = requestedBpm.coerceIn(minBpm, maxBpm)
        val current = _currentBpm.value
        val next = when {
            abs(target - current) <= maxStepPerUpdate -> target
            target > current -> current + maxStepPerUpdate
            else -> current - maxStepPerUpdate
        }
        _currentBpm.value = next.coerceIn(minBpm, maxBpm)
    }

    fun play() {
        _isPlaying.value = true
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun beatIntervalMillis(): Long {
        val bpm = _currentBpm.value.coerceAtLeast(1)
        return (60_000.0 / bpm).roundToLong()
    }
}
