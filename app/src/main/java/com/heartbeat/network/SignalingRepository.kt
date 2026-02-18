package com.heartbeat.network

import kotlinx.coroutines.flow.Flow

interface SignalingRepository {
    suspend fun joinSession(sessionCode: String, localUserId: String)
    suspend fun leaveSession()
    suspend fun publish(message: SignalingMessage)
    val incoming: Flow<SignalingMessage>
}
