package com.heartbeat.network

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Signaling transport over Firebase Realtime Database.
 *
 * Session path:
 * /sessions/{sessionCode}/signals/{senderUserId}/{messageId}
 */
class FirebaseSignalingRepository(
    private val database: FirebaseDatabase,
) : SignalingRepository {
    private var sessionCode: String? = null
    private var localUserId: String? = null

    override suspend fun joinSession(sessionCode: String, localUserId: String) {
        this.sessionCode = sessionCode
        this.localUserId = localUserId
        database.getReference("sessions").child(sessionCode).child("presence").child(localUserId).setValue(true).await()
    }

    override suspend fun leaveSession() {
        val code = sessionCode ?: return
        val user = localUserId ?: return
        database.getReference("sessions").child(code).child("presence").child(user).removeValue().await()
    }

    override suspend fun publish(message: SignalingMessage) {
        val code = sessionCode ?: return
        val user = localUserId ?: return
        val ref = database.getReference("sessions").child(code).child("signals").child(user).push()

        val map = when (message) {
            is SignalingMessage.Offer -> mapOf("type" to "offer", "sdp" to message.sdp)
            is SignalingMessage.Answer -> mapOf("type" to "answer", "sdp" to message.sdp)
            is SignalingMessage.IceCandidate -> mapOf(
                "type" to "ice",
                "sdpMid" to message.sdpMid,
                "sdpMLineIndex" to message.sdpMLineIndex,
                "candidate" to message.candidate,
            )
        }
        ref.setValue(map).await()
    }

    override val incoming: Flow<SignalingMessage> = callbackFlow {
        val code = sessionCode
        val user = localUserId
        if (code == null || user == null) {
            close()
            return@callbackFlow
        }

        val ref = database.getReference("sessions").child(code).child("signals")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { senderNode ->
                    if (senderNode.key == user) return@forEach

                    senderNode.children.forEach { signalNode ->
                        val type = signalNode.child("type").getValue(String::class.java) ?: return@forEach
                        val message = when (type) {
                            "offer" -> SignalingMessage.Offer(signalNode.child("sdp").getValue(String::class.java) ?: return@forEach)
                            "answer" -> SignalingMessage.Answer(signalNode.child("sdp").getValue(String::class.java) ?: return@forEach)
                            "ice" -> SignalingMessage.IceCandidate(
                                sdpMid = signalNode.child("sdpMid").getValue(String::class.java),
                                sdpMLineIndex = signalNode.child("sdpMLineIndex").getValue(Int::class.java) ?: return@forEach,
                                candidate = signalNode.child("candidate").getValue(String::class.java) ?: return@forEach,
                            )
                            else -> null
                        }
                        if (message != null) {
                            trySend(message)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
