package com.heartbeat.network

sealed class SignalingMessage {
    data class Offer(val sdp: String) : SignalingMessage()
    data class Answer(val sdp: String) : SignalingMessage()
    data class IceCandidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String) : SignalingMessage()
}
