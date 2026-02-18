package com.heartbeaten.network

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        database.getReference("sessions")
            .child(sessionCode)
            .child("presence")
            .child(localUserId)
            .setValue(true)
            .await()
    }

    override suspend fun leaveSession() {
        val code = sessionCode ?: return
        val user = localUserId ?: return
        val sessionRef = database.getReference("sessions").child(code)

        sessionRef.child("presence").child(user).removeValue().await()
        sessionRef.child("signals").child(user).removeValue().await()

        val initiatorRef = sessionRef.child("initiator")
        val initiator = initiatorRef.get().await().getValue(String::class.java)
        if (initiator == user) {
            initiatorRef.removeValue().await()
        }

        sessionCode = null
        localUserId = null
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

    override suspend fun shouldCreateOffer(): Boolean {
        val code = sessionCode ?: return false
        val user = localUserId ?: return false
        val initiatorRef = database.getReference("sessions").child(code).child("initiator")

        val elected = suspendCancellableCoroutine<String?> { cont ->
            initiatorRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val current = currentData.getValue(String::class.java)
                    if (current.isNullOrBlank()) {
                        currentData.value = user
                    }
                    return Transaction.success(currentData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null) {
                        cont.resumeWithException(error.toException())
                        return
                    }
                    cont.resume(snapshot?.getValue(String::class.java))
                }
            })
        }

        return elected == user
    }

    override val incoming: Flow<SignalingMessage> = callbackFlow {
        val code = sessionCode
        val user = localUserId
        if (code == null || user == null) {
            close(IllegalStateException("joinSession must be called before collecting incoming signaling"))
            return@callbackFlow
        }

        val seenMessageIds = mutableSetOf<String>()
        val ref = database.getReference("sessions").child(code).child("signals")
        val listener = object : ChildEventListener {
            override fun onChildAdded(senderSnapshot: DataSnapshot, previousChildName: String?) {
                if (senderSnapshot.key == user) return
                emitUnseenSignals(senderSnapshot, seenMessageIds)
            }

            override fun onChildChanged(senderSnapshot: DataSnapshot, previousChildName: String?) {
                if (senderSnapshot.key == user) return
                emitUnseenSignals(senderSnapshot, seenMessageIds)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }

            fun emitUnseenSignals(senderSnapshot: DataSnapshot, seenIds: MutableSet<String>) {
                val sender = senderSnapshot.key ?: return
                senderSnapshot.children.forEach { signalNode ->
                    val messageId = signalNode.key ?: return@forEach
                    val uniqueKey = "$sender/$messageId"
                    if (!seenIds.add(uniqueKey)) return@forEach

                    parseSignal(signalNode)?.let { trySend(it) }
                }
            }
        }

        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun parseSignal(signalNode: DataSnapshot): SignalingMessage? {
        val type = signalNode.child("type").getValue(String::class.java) ?: return null
        return when (type) {
            "offer" -> SignalingMessage.Offer(
                sdp = signalNode.child("sdp").getValue(String::class.java) ?: return null,
            )

            "answer" -> SignalingMessage.Answer(
                sdp = signalNode.child("sdp").getValue(String::class.java) ?: return null,
            )

            "ice" -> {
                val indexAny = signalNode.child("sdpMLineIndex").value
                val index = when (indexAny) {
                    is Long -> indexAny.toInt()
                    is Int -> indexAny
                    else -> return null
                }

                SignalingMessage.IceCandidate(
                    sdpMid = signalNode.child("sdpMid").getValue(String::class.java),
                    sdpMLineIndex = index,
                    candidate = signalNode.child("candidate").getValue(String::class.java) ?: return null,
                )
            }

            else -> null
        }
    }
}
