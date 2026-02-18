# Architecture

## 1) Data flow

1. Mi Band 6 broadcasts HR over BLE.
2. `BleHeartRateSource` reads raw HR packets.
3. `HeartRateNormalizer` validates value, source, timestamp skew and adds sequence.
4. `HeartbeatForegroundService` sends normalized `HeartRateFrame` to `PeerTransport`.
5. `WebRtcPeerTransport` uses Firebase signaling to exchange SDP/ICE.
6. Peer connection is negotiated with STUN Google (`stun:stun.l.google.com:19302`).
7. DataChannel carries heart-rate frames P2P.
8. Receiver updates `HeartbeatAudioEngine` based on selected `ListenMode`.

## 2) Discovery/signaling

### Signaling channel

- Backend: Firebase Realtime Database.
- Session path: `/sessions/{sessionCode}`.
- Presence: `/sessions/{sessionCode}/presence/{userId}`.
- Signals: `/sessions/{sessionCode}/signals/{senderUserId}/{messageId}`.

### Message types

- `offer` (SDP)
- `answer` (SDP)
- `ice` (ICE candidate)

## 3) Connectivity

- ICE server configured: `stun:stun.l.google.com:19302`.
- Transport surface is abstracted by `PeerTransport`.
- If desired, relay fallback can be added in another `PeerTransport` implementation.

## 4) Firebase + Gradle integration

- Root Gradle plugin includes `com.google.gms.google-services` (apply false).
- App module applies `com.google.gms.google-services`.
- Firebase dependencies imported via BoM `34.9.0`.
- `google-services.json` in `app/` enables SDK auto-configuration.

## 5) Reliability expectations

- Start/stop operations are idempotent and serialized.
- Stream collectors are cancellable and supervised.
- Exceptions in collectors are surfaced through service `onError` callback.

## 6) Next implementation steps

1. Add secure Firebase Database rules for session-scoped read/write.
2. Add TURN servers for symmetric NAT scenarios.
3. Persist and clean old signaling nodes to avoid repeated reads.
4. Complete runtime BLE permission flow (scan/connect + Android 12+ permissions).
5. Add instrumentation tests for background reconnection and audio continuity.
