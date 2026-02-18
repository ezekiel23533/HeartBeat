package com.heartbeaten.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Concrete WebRTC transport configured with Google STUN server.
 */
class WebRtcPeerTransport(
    private val localUserId: String,
    private val signalingRepository: SignalingRepository,
    private val peerConnectionFactory: PeerConnectionFactory,
) : PeerTransport {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _incomingFrames = MutableSharedFlow<HeartRateFrame>(extraBufferCapacity = 64)
    override val incomingFrames: Flow<HeartRateFrame> = _incomingFrames.asSharedFlow()

    private val _connectionState = MutableStateFlow(TransportState.IDLE)
    override val connectionState = _connectionState.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var signalingJob: Job? = null

    override suspend fun connect(sessionCode: String) {
        disconnect()

        _connectionState.value = TransportState.CONNECTING
        signalingRepository.joinSession(sessionCode, localUserId)

        peerConnection = createPeerConnection()
        dataChannel = peerConnection?.createDataChannel("heartbeat", DataChannel.Init())?.also(::registerDataChannelObserver)

        signalingJob = scope.launch {
            signalingRepository.incoming.collect { message ->
                when (message) {
                    is SignalingMessage.Offer -> handleOffer(message)
                    is SignalingMessage.Answer -> handleAnswer(message)
                    is SignalingMessage.IceCandidate -> {
                        peerConnection?.addIceCandidate(
                            IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate),
                        )
                    }
                }
            }
        }

        if (signalingRepository.shouldCreateOffer()) {
            createAndSendOffer()
        }
    }

    override suspend fun disconnect() {
        signalingJob?.cancel()
        signalingJob = null

        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null

        signalingRepository.leaveSession()
        _connectionState.value = TransportState.IDLE
    }

    override suspend fun send(frame: HeartRateFrame) {
        val channel = dataChannel ?: return
        if (channel.state() != DataChannel.State.OPEN) return

        val payload = "${frame.bpm}|${frame.timestampMillis}|${frame.sourceUserId}|${frame.sequence}".encodeToByteArray()
        channel.send(DataChannel.Buffer(payload.toByteBuffer(), false))
    }

    private fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()),
        )

        return peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    scope.launch {
                        signalingRepository.publish(
                            SignalingMessage.IceCandidate(
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                candidate = candidate.sdp,
                            ),
                        )
                    }
                }

                override fun onDataChannel(channel: DataChannel) {
                    dataChannel = channel
                    registerDataChannelObserver(channel)
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    _connectionState.value = when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED,
                        -> TransportState.CONNECTED

                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.CLOSED,
                        -> TransportState.IDLE

                        PeerConnection.IceConnectionState.FAILED -> TransportState.FAILED
                        else -> _connectionState.value
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out org.webrtc.MediaStream>) = Unit
            },
        )
    }

    private fun registerDataChannelObserver(channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() = Unit
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                parseFrame(bytes.decodeToString())?.let { _incomingFrames.tryEmit(it) }
            }
        })
    }

    private fun createAndSendOffer() {
        val pc = peerConnection ?: return
        pc.createOffer(object : BasicSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(BasicSdpObserver(), desc)
                scope.launch { signalingRepository.publish(SignalingMessage.Offer(desc.description)) }
            }

            override fun onCreateFailure(error: String?) {
                _connectionState.value = TransportState.FAILED
            }
        }, MediaConstraints())
    }

    private fun handleOffer(message: SignalingMessage.Offer) {
        val pc = peerConnection ?: return
        val remote = SessionDescription(SessionDescription.Type.OFFER, message.sdp)
        pc.setRemoteDescription(BasicSdpObserver(), remote)

        pc.createAnswer(object : BasicSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(BasicSdpObserver(), desc)
                scope.launch { signalingRepository.publish(SignalingMessage.Answer(desc.description)) }
            }
        }, MediaConstraints())
    }

    private fun handleAnswer(message: SignalingMessage.Answer) {
        peerConnection?.setRemoteDescription(
            BasicSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, message.sdp),
        )
    }

    private fun parseFrame(payload: String): HeartRateFrame? {
        val parts = payload.split('|')
        if (parts.size != 4) return null

        return HeartRateFrame(
            bpm = parts[0].toIntOrNull() ?: return null,
            timestampMillis = parts[1].toLongOrNull() ?: return null,
            sourceUserId = parts[2],
            sequence = parts[3].toLongOrNull() ?: return null,
        )
    }
}

private open class BasicSdpObserver : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}

private fun ByteArray.toByteBuffer(): java.nio.ByteBuffer = java.nio.ByteBuffer.wrap(this)
