package com.heartbeaten.service

/**
 * Bridge for surfacing long-running session state to Android foreground notifications.
 */
interface SessionForegroundNotifier {
    fun start(sessionCode: String)
    fun update(sessionCode: String, state: String, bpm: Int?)
    fun stop()

    object NoOp : SessionForegroundNotifier {
        override fun start(sessionCode: String) = Unit
        override fun update(sessionCode: String, state: String, bpm: Int?) = Unit
        override fun stop() = Unit
    }
}
